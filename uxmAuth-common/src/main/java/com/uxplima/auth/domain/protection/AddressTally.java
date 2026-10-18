package com.uxplima.auth.domain.protection;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * What one address did wrong: the wrong passwords since its last ban, the step its next ban is, until when it is
 * banned, and when it last failed.
 */
public record AddressTally(
        int failures,
        int step,
        @Nullable Instant bannedUntil,
        @Nullable Instant lastFailure) {

    public AddressTally {
        if (failures < 0 || step < 0) {
            throw new IllegalArgumentException("A tally counts up from nothing");
        }
    }

    /** An address that never failed. */
    public static AddressTally clean() {
        return new AddressTally(0, 0, null, null);
    }

    /** Whether the address is banned at this moment. */
    public boolean bannedAt(Instant now) {
        return bannedUntil != null && Objects.requireNonNull(now, "now").isBefore(bannedUntil);
    }

    /** How long the ban has left at this moment, nothing when there is none. */
    public Duration leftAt(Instant now) {
        return bannedAt(now) ? Duration.between(now, bannedUntil) : Duration.ZERO;
    }

    /**
     * One more wrong password at this moment. An address quiet for as long as the steps forgive starts again
     * from the first step; the failure that reaches the limit bans it for its step and starts the count again.
     */
    public AddressTally failedAt(Instant now, BanSteps steps) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(steps, "steps");
        AddressTally from = forgivenAt(now, steps) ? clean() : this;
        int counted = from.failures + 1;
        if (counted < steps.failures()) {
            return new AddressTally(counted, from.step, from.bannedUntil, now);
        }
        return new AddressTally(0, steps.next(from.step), now.plus(steps.length(from.step)), now);
    }

    /** Quiet counts from the later of the last failure and the end of the ban: a banned address cannot fail. */
    private boolean forgivenAt(Instant now, BanSteps steps) {
        Instant quietSince = lastFailure;
        if (bannedUntil != null && (quietSince == null || bannedUntil.isAfter(quietSince))) {
            quietSince = bannedUntil;
        }
        return quietSince != null && !now.isBefore(quietSince.plus(steps.forgiveAfter()));
    }
}
