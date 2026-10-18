package com.uxplima.auth.application.protection;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.premium.HostPatterns;

/**
 * Who skips the password: a player back from the same address within the time, when the operator turned sessions
 * on for everybody, or for those who chose them and this player did. Through a shared-address host nobody is
 * remembered, because one player's address is everybody's there.
 */
public final class Remember {

    /** For whom sessions are on. */
    public enum Mode {
        /** Nobody: every join asks the password. */
        OFF,
        /** Everybody who logs in. */
        EVERYONE,
        /** Those who chose it, in the dialog, the form or with /session. */
        CHOSEN
    }

    /** For whom sessions are on, and how long one lasts. */
    public record Settings(Mode mode, Duration lasts) {
        public Settings {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(lasts, "lasts");
        }
    }

    private final RememberedSessions store;
    private final Supplier<Settings> settings;
    private final Supplier<HostPatterns> shared;
    private final Supplier<Instant> clock;

    public Remember(
            RememberedSessions store,
            Supplier<Settings> settings,
            Supplier<HostPatterns> shared,
            Supplier<Instant> clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.shared = Objects.requireNonNull(shared, "shared");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The account logged in from this address, through this host: remember it when the rules say so. */
    public void loggedIn(UUID account, String address, String host) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(address, "address");
        Settings now = settings.get();
        boolean wanted =
                switch (now.mode()) {
                    case OFF -> false;
                    case EVERYONE -> true;
                    case CHOSEN -> store.choice(account).orElse(false);
                };
        if (wanted && !shared(Objects.requireNonNull(host, "host"))) {
            store.remember(account, address, clock.get().plus(now.lasts()));
        }
    }

    /** Whether the account, back from this address through this host, skips the password. */
    public boolean resumes(UUID account, String address, String host) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(address, "address");
        if (settings.get().mode() == Mode.OFF || shared(Objects.requireNonNull(host, "host"))) {
            return false;
        }
        return store.holds(account, address, clock.get());
    }

    /**
     * Whether the host is a shared-address filter. While the operator lists any, a host nobody knows counts as
     * one: a connection the door could not read the host of must not be trusted by its address.
     */
    private boolean shared(String host) {
        HostPatterns patterns = shared.get();
        return patterns.matches(host) || (host.isBlank() && !patterns.isEmpty());
    }

    /** The account chose sessions, or no sessions; choosing none ends the ones it has. */
    public void choose(UUID account, boolean remember) {
        store.choose(Objects.requireNonNull(account, "account"), remember);
        if (!remember) {
            store.forget(account);
        }
    }

    /** What the account chose, and no sessions until it chose. */
    public boolean chose(UUID account) {
        return store.choice(Objects.requireNonNull(account, "account")).orElse(false);
    }

    /** End every session of the account: its password changed, or it logged out. */
    public void forget(UUID account) {
        store.forget(Objects.requireNonNull(account, "account"));
    }
}
