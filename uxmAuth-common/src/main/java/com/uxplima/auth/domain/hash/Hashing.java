package com.uxplima.auth.domain.hash;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import com.uxplima.auth.domain.password.Password;

/**
 * The current algorithm, and every algorithm a stored hash may be in.
 *
 * <p>A hash is written in the current one only. A hash is read in whichever wrote it, and a right password on
 * anything but the current algorithm and parameters comes back with a fresh hash, so an operator who changes
 * the algorithm moves every active account across without a single reset.
 */
public final class Hashing {

    private final PasswordHasher current;
    private final Map<HashAlgorithm, PasswordHasher> readers = new EnumMap<>(HashAlgorithm.class);

    public Hashing(PasswordHasher current, List<PasswordHasher> all) {
        this.current = Objects.requireNonNull(current, "current");
        Objects.requireNonNull(all, "all");
        for (PasswordHasher hasher : all) {
            readers.put(hasher.algorithm(), hasher);
        }
        readers.put(current.algorithm(), current);
    }

    /**
     * This hashing, telling the time of every hash and every check it makes.
     *
     * @param ticks a monotonic clock in nanoseconds
     * @param spent hears the nanoseconds each took
     */
    public Hashing observed(LongSupplier ticks, LongConsumer spent) {
        Objects.requireNonNull(ticks, "ticks");
        Objects.requireNonNull(spent, "spent");
        return new Hashing(
                new Timed(current, ticks, spent),
                readers.values().stream()
                        .filter(reader -> reader != current)
                        .<PasswordHasher>map(reader -> new Timed(reader, ticks, spent))
                        .toList());
    }

    /** A hasher whose every hash and check is timed. */
    private record Timed(PasswordHasher hasher, LongSupplier ticks, LongConsumer spent) implements PasswordHasher {
        @Override
        public HashAlgorithm algorithm() {
            return hasher.algorithm();
        }

        @Override
        public PasswordHash hash(Password password) {
            long start = ticks.getAsLong();
            try {
                return hasher.hash(password);
            } finally {
                spent.accept(ticks.getAsLong() - start);
            }
        }

        @Override
        public boolean verify(Password password, PasswordHash hash) {
            long start = ticks.getAsLong();
            try {
                return hasher.verify(password, hash);
            } finally {
                spent.accept(ticks.getAsLong() - start);
            }
        }

        @Override
        public boolean isCurrent(PasswordHash hash) {
            return hasher.isCurrent(hash);
        }
    }

    /** The algorithm new hashes are written in. */
    public HashAlgorithm currentAlgorithm() {
        return current.algorithm();
    }

    public PasswordHash hash(Password password) {
        return current.hash(Objects.requireNonNull(password, "password"));
    }

    public Verification verify(Password password, PasswordHash stored) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(stored, "stored");
        Optional<PasswordHasher> reader = HashFormats.recognise(stored).map(readers::get);
        if (reader.isEmpty() || !reader.get().verify(password, stored)) {
            return new Verification.Wrong();
        }
        if (reader.get() == current && current.isCurrent(stored)) {
            return new Verification.Right();
        }
        return new Verification.RightButRehash(current.hash(password));
    }
}
