package com.uxplima.auth.domain.network;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /** Staff removed this account: its player is sent away wherever they are. */
    record Unregistered(UUID account) implements Bulletin {
        public Unregistered {
            Objects.requireNonNull(account, "account");
        }
    }

    /** Staff changed how this account logs in: its player is sent away to join again under the new way. */
    record Rejoin(UUID account) implements Bulletin {
        public Rejoin {
            Objects.requireNonNull(account, "account");
        }
    }

    /** What a visit token may be: the nonce of a proxy's claim, or a random id a server gave a connection. */
    Pattern VISIT = Pattern.compile("[A-Za-z0-9-]{1,64}");
}
