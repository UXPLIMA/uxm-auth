package com.uxplima.auth.application.protection;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.protection.SharedAddresses;

/**
 * Who skips the password: a player back from the same address within the time, when the operator turned sessions
 * on for everybody, or for those who chose them and this player did. Through a shared-address filter nobody is
 * remembered or resumed, because one player's address is everybody's there.
 *
 * <p>A session is bound to the host the player typed as well, read the same way on a server and on the proxy: the
 * name, without a port, case or a trailing dot. Players who share one address, as a mobile network's do, rarely
 * type the same host by chance, though the host is theirs to write and proves nothing alone: it narrows who may
 * resume, it does not prove who does. No other signal of the client reaches the door on both platforms before the
 * session is decided, so none is bound.
 */
public final class Remember {

    /** The longest host the session table keeps. */
    private static final int LONGEST_HOST = 255;

    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");

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
    private final Supplier<SharedAddresses> shared;
    private final Supplier<Instant> clock;

    public Remember(
            RememberedSessions store,
            Supplier<Settings> settings,
            Supplier<SharedAddresses> shared,
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
        if (wanted && !shared.get().through(address, Objects.requireNonNull(host, "host"))) {
            store.remember(account, address, typed(host), clock.get().plus(now.lasts()));
            // A choice of no sessions may have landed between the read above and the write: then it goes again.
            if (now.mode() == Mode.CHOSEN && !chose(account)) {
                store.forget(account);
            }
        }
    }

    /** Whether the account, back from this address through this host, skips the password. */
    public boolean resumes(UUID account, String address, String host) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(address, "address");
        if (settings.get().mode() == Mode.OFF || shared.get().through(address, Objects.requireNonNull(host, "host"))) {
            return false;
        }
        return store.holds(account, address, typed(host), clock.get());
    }

    /**
     * The host as a session keeps it: what comes before a Forge marker, without the port, in lower case and with
     * no trailing dot, cut to the column it is kept in.
     */
    static String typed(String host) {
        String name = host;
        int marker = name.indexOf('\0');
        if (marker >= 0) {
            name = name.substring(0, marker);
        }
        name = name.strip().toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0
                && colon == name.lastIndexOf(':')
                && PORT.matcher(name.substring(colon + 1)).matches()) {
            name = name.substring(0, colon);
        }
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        return name.length() > LONGEST_HOST ? name.substring(0, LONGEST_HOST) : name;
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
