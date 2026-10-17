package com.uxplima.auth.domain.premium;

import java.util.Objects;

/** What the door does with a connection. */
public sealed interface Decision {

    /** Ask the client for the encryption handshake, and let Mojang say who it is. */
    record Handshake() implements Decision {}

    /**
     * Let it in to log in or register with a password.
     *
     * @param unverified whether no source could say who owns the name, so the account asks again next time
     * @param explain whether this connection failed the handshake lately and is told why it joins this way
     * @param marked whether it plays under the domain rule's mark, a name of its own beside the paid one
     */
    record Cracked(boolean unverified, boolean explain, boolean marked) implements Decision {

        /** A password, under the name the player typed. */
        public Cracked(boolean unverified, boolean explain) {
            this(unverified, explain, false);
        }
    }

    /** Turn it away, with the reason in plain words. */
    record Refuse(Refusal why) implements Decision {
        public Refuse {
            Objects.requireNonNull(why, "why");
        }
    }
}
