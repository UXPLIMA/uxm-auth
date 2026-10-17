package com.uxplima.auth.handshake;

import java.util.Objects;

import com.uxplima.auth.domain.premium.Decision;

/** What the hello of a connection came to, as its pre-login reads it. */
public sealed interface HelloOutcome {

    /** The client proved its key and Mojang said who it is: the profile the pre-login holds is Mojang's. */
    record Proved() implements HelloOutcome {}

    /** The client was asked for its key and the pre-login came without the proof. */
    record NotProved() implements HelloOutcome {}

    /** The door let the hello through with this decision, a password or a refusal. */
    record Decided(Decision decision) implements HelloOutcome {
        public Decided {
            Objects.requireNonNull(decision, "decision");
        }
    }
}
