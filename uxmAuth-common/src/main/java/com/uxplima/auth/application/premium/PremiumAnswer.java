package com.uxplima.auth.application.premium;

import java.util.Objects;
import java.util.UUID;

/** What a source says about a name. */
public sealed interface PremiumAnswer {

    /** A paid account holds the name, under this Mojang id and in this spelling. */
    record Premium(UUID mojangId, String exactName) implements PremiumAnswer {
        public Premium {
            Objects.requireNonNull(mojangId, "mojangId");
            Objects.requireNonNull(exactName, "exactName");
        }
    }

    /** No paid account holds the name. A demo account counts as none. */
    record Cracked() implements PremiumAnswer {}

    /** The source could not say: refused, limited, down, or slower than the timeout. */
    record Unknown() implements PremiumAnswer {}
}
