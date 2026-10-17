package com.uxplima.auth.application.premium;

import java.util.Objects;

import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.auth.domain.state.AuthState;

/** What a connection Mojang proved comes to. */
public sealed interface PremiumAdmission {

    /** In, logged in to this account, and playing under its id. */
    record In(AuthState.Authenticated state) implements PremiumAdmission {
        public In {
            Objects.requireNonNull(state, "state");
        }
    }

    /** Turned away, for this reason. */
    record Refused(Refusal why) implements PremiumAdmission {
        public Refused {
            Objects.requireNonNull(why, "why");
        }
    }
}
