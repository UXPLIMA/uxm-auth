package com.uxplima.auth.domain.protection;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * How an address is banned for wrong passwords.
 *
 * @param lengths how long each ban lasts, in order; the last one repeats for every ban after it
 * @param failures how many wrong passwords earn the next ban
 * @param forgiveAfter how long an address stays quiet before it starts from the first step again
 */
public record BanSteps(List<Duration> lengths, int failures, Duration forgiveAfter) {
    public BanSteps {
        lengths = List.copyOf(Objects.requireNonNull(lengths, "lengths"));
        Objects.requireNonNull(forgiveAfter, "forgiveAfter");
        if (lengths.isEmpty() || lengths.stream().anyMatch(length -> length.isNegative() || length.isZero())) {
            throw new IllegalArgumentException("A ban needs at least one step, each longer than nothing");
        }
        if (failures < 1) {
            throw new IllegalArgumentException("A ban needs at least one failure, got: " + failures);
        }
        if (forgiveAfter.isNegative() || forgiveAfter.isZero()) {
            throw new IllegalArgumentException("The quiet spell that forgives must be longer than nothing");
        }
    }

    /** The length of the ban at this step, the last one for every step past the end. */
    Duration length(int step) {
        return lengths.get(Math.min(step, lengths.size() - 1));
    }

    /** The step after this one, which stays at the last. */
    int next(int step) {
        return Math.min(step + 1, lengths.size() - 1);
    }
}
