package com.uxplima.auth.domain.network;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.name.PlayerName;

/** Something every member of a network hears at once, the proxies and the servers alike. */
public sealed interface Bulletin {

    /**
     * Every session of this account ended, because its password changed, but the one {@code keep} names: the
     * visit of the player who made the change themselves. Without it, every session ends.
     */
    record SessionsEnded(UUID account, Optional<String> keep) implements Bulletin {
        public SessionsEnded {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(keep, "keep");
            keep.ifPresent(visit -> {
                if (!VISIT.matcher(visit).matches()) {
                    throw new IllegalArgumentException("A visit is letters, digits and dashes, up to 64: " + visit);
                }
            });
        }
    }

    /**
     * Staff let the waiting player of this account in, on whichever server or proxy they wait. The nonce makes each
     * request one of a kind, so a copy of it heard again lets nobody in.
     */
    record LetIn(UUID account, String nonce) implements Bulletin {
        public LetIn {
            Objects.requireNonNull(account, "account");
            if (!VISIT.matcher(Objects.requireNonNull(nonce, "nonce")).matches()) {
                throw new IllegalArgumentException("A nonce is letters, digits and dashes, up to 64: " + nonce);
            }
        }
    }

    /** Who removed or switched an account, so its player is sent away in the right words. */
    enum By {
        /** Staff, from a command or the console. */
        STAFF,
        /** The player, from their own account. */
        OWNER
    }

    /** This account was removed: its player is sent away wherever they are. */
    record Unregistered(UUID account, By by) implements Bulletin {
        public Unregistered {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(by, "by");
        }

        /** Staff removed it. */
        public Unregistered(UUID account) {
            this(account, By.STAFF);
        }
    }

    /** How this account logs in changed: its player is sent away to join again under the new way. */
    record Rejoin(UUID account, By by) implements Bulletin {
        public Rejoin {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(by, "by");
        }

        /** Staff changed it. */
        public Rejoin(UUID account) {
            this(account, By.STAFF);
        }
    }

    /** Something staff hear on every server: a failed login, a ban, an address many accounts share, staff acting. */
    sealed interface Alert extends Bulletin {}

    /** A wrong answer from this address, the first of its window. */
    record FailedLogin(String address) implements Alert {
        public FailedLogin {
            requireText(address, "address");
        }
    }

    /** This address is banned for this long, after its wrong answers. */
    record Banned(String address, Duration length) implements Alert {
        public Banned {
            requireText(address, "address");
            if (Objects.requireNonNull(length, "length").isNegative() || length.isZero()) {
                throw new IllegalArgumentException("A ban lasts a while: " + length);
            }
        }
    }

    /** This name logged in from an address that this many accounts, its own among them, have used. */
    record SharedAddress(PlayerName name, String address, int accounts) implements Alert {
        public SharedAddress {
            Objects.requireNonNull(name, "name");
            requireText(address, "address");
            if (accounts < 2) {
                throw new IllegalArgumentException("An address is shared by two accounts or more: " + accounts);
            }
        }
    }

    /**
     * Staff did this to this target, a name or an address.
     *
     * @param actor the staff player, or empty for the console
     * @param action the key of the staff command
     */
    record StaffActed(Optional<PlayerName> actor, String action, String target) implements Alert {
        public StaffActed {
            Objects.requireNonNull(actor, "actor");
            if (!ACTION.matcher(Objects.requireNonNull(action, "action")).matches()) {
                throw new IllegalArgumentException("A staff action is its key: " + action);
            }
            requireText(target, "target");
        }
    }

    private static void requireText(String text, String what) {
        if (Objects.requireNonNull(text, what).isBlank()) {
            throw new IllegalArgumentException("An alert names its " + what);
        }
    }

    /** What a visit token may be: the nonce of a proxy's claim, or a random id a server gave a connection. */
    Pattern VISIT = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /** What a staff command's key may be. */
    Pattern ACTION = Pattern.compile("[a-z0-9-]{1,32}");
}
