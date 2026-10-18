package com.uxplima.auth.application.staff;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.port.AuthNotices;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.protection.AddressBans;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.protection.AddressKey;
import com.uxplima.auth.domain.state.AuthMethod;

/**
 * What staff hear as it happens: wrong answers from an address, once a window, the ban that ends them, a new account
 * on an address many accounts use, and what staff did. Each goes out as a bulletin, so staff on every server hear it,
 * and is handed off the calling thread first, so none can delay a login or a command.
 */
public final class Alerts {

    /**
     * Which alerts go out.
     *
     * @param failedLoginWindow how long the first wrong answer from an address speaks for the ones after it
     * @param sharedAddressAccounts how many accounts on one address are told, the new one among them; 0 is off
     */
    public record Settings(
            boolean failedLogin,
            Duration failedLoginWindow,
            boolean ban,
            int sharedAddressAccounts,
            boolean staffActions) {
        public Settings {
            if (Objects.requireNonNull(failedLoginWindow, "failedLoginWindow").toSeconds() < 1) {
                throw new IllegalArgumentException(
                        "A failed-login window lasts a second or more: " + failedLoginWindow);
            }
            if (sharedAddressAccounts < 0 || sharedAddressAccounts == 1) {
                throw new IllegalArgumentException(
                        "A shared address is told at two accounts or more, or never at 0: " + sharedAddressAccounts);
            }
        }
    }

    /** How many accounts on one address are read at most, which is far more than any threshold means. */
    private static final int READ_AT_MOST = 1000;

    private final Supplier<Settings> settings;
    private final LoginHistory history;
    private final NetworkEvents events;
    private final Executor async;
    private final Supplier<Instant> clock;
    private final AuthNotices notices;
    private final Map<String, Instant> failedSince = new ConcurrentHashMap<>();

    /** @param async runs a task off every server and region thread */
    public Alerts(
            Supplier<Settings> settings,
            LoginHistory history,
            NetworkEvents events,
            Executor async,
            Supplier<Instant> clock) {
        this(settings, history, events, async, clock, AuthNotices.NONE);
    }

    /** @param notices hears what happened whatever the alert switches say, which is the Discord channel */
    public Alerts(
            Supplier<Settings> settings,
            LoginHistory history,
            NetworkEvents events,
            Executor async,
            Supplier<Instant> clock,
            AuthNotices notices) {
        this.notices = Objects.requireNonNull(notices, "notices");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.history = Objects.requireNonNull(history, "history");
        this.events = Objects.requireNonNull(events, "events");
        this.async = Objects.requireNonNull(async, "async");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** These failures, telling staff of every wrong answer that counts against its address. */
    public LogIn.Failures watching(AddressBans bans) {
        Objects.requireNonNull(bans, "bans");
        return (address, host) -> {
            Optional<Duration> banned = bans.failed(address, host);
            if (bans.counts(address, host)) {
                failed(address, banned);
            }
            return banned;
        };
    }

    private void failed(String address, Optional<Duration> banned) {
        banned.ifPresent(length -> notices.banned(address, length));
        Settings now = settings.get();
        if (banned.isPresent() && now.ban()) {
            publish(new Bulletin.Banned(address, banned.get()));
        } else if (now.failedLogin() && opensWindow(AddressKey.of(address), now.failedLoginWindow())) {
            publish(new Bulletin.FailedLogin(address));
        }
    }

    /** Whether this wrong answer is the first of a new window for its address. Windows that ended are dropped. */
    private boolean opensWindow(String key, Duration window) {
        Instant now = clock.get();
        AtomicBoolean opened = new AtomicBoolean();
        failedSince.compute(key, (k, since) -> {
            boolean fresh = since == null || !now.isBefore(since.plus(window));
            opened.set(fresh);
            return fresh ? now : since;
        });
        if (opened.get()) {
            failedSince.values().removeIf(since -> !now.isBefore(since.plus(window)));
        }
        return opened.get();
    }

    /**
     * This account logged in from this address, this way. The notices hear it, and hear of a new account on an
     * address other accounts used; staff are told when the address now has as many accounts as the operator's
     * threshold, or more. Reads the history, so it runs off the server's threads, before the login is written.
     */
    public void landed(UUID account, PlayerName name, String address, AuthMethod method) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        if (Objects.requireNonNull(method, "method") == AuthMethod.REGISTERED) {
            notices.registered(name, address);
        } else {
            notices.loggedIn(name, address, method);
        }
        if (!AddressKey.isAddress(address)) {
            return;
        }
        List<UUID> before = history.accountsOn(address, READ_AT_MOST);
        if (before.isEmpty() || before.contains(account)) {
            return;
        }
        int accounts = before.size() + 1;
        notices.knownAddress(name, address, accounts);
        int threshold = settings.get().sharedAddressAccounts();
        if (threshold > 0 && accounts >= threshold) {
            publish(new Bulletin.SharedAddress(name, address, accounts));
        }
    }

    /**
     * Staff did this to this target, a name or an address.
     *
     * @param actor the staff player, or empty for the console
     */
    public void staffActed(Optional<PlayerName> actor, StaffAction action, String target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        if (action == StaffAction.RESOLVE) {
            notices.conflictSettled(target);
        }
        if (settings.get().staffActions()) {
            publish(new Bulletin.StaffActed(actor, action.key(), target));
        }
    }

    private void publish(Bulletin.Alert alert) {
        async.execute(() -> events.publish(alert));
    }
}
