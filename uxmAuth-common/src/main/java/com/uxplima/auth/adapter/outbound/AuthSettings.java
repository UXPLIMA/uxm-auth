package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.password.CharClass;

/** The blocks of {@code config.conf} that decide how a player gets in, one immutable record each. */
public final class AuthSettings {

    /** BCrypt reads this many bytes of a password and ignores the rest. */
    public static final int BCRYPT_BYTES = 72;

    private AuthSettings() {}

    /**
     * What a name may be, and whether a second connection under a name already online may come in from the
     * address the first one holds.
     */
    public record Names(
            String pattern, int minLength, int maxLength, List<String> blocked, boolean reconnectFromSameAddress) {
        public Names {
            Objects.requireNonNull(pattern, "pattern");
            blocked = List.copyOf(Objects.requireNonNull(blocked, "blocked"));
        }
    }

    /** What a Bedrock player Floodgate vouched for gets: in at once, or the same password as everyone. */
    public record Bedrock(boolean autoLogin) {}

    /** What a password may be, which confirmations are asked, and how many wrong ones a player gets. */
    public record Passwords(
            int minLength,
            int maxLength,
            String allowed,
            Set<CharClass> required,
            boolean confirmOnRegister,
            boolean oldOnChange,
            int maxAttempts) {
        public Passwords {
            Objects.requireNonNull(allowed, "allowed");
            required = Set.copyOf(Objects.requireNonNull(required, "required"));
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("passwords.max-attempts must be at least 1, got: " + maxAttempts);
            }
        }
    }

    /** The algorithm new hashes are written in, and the numbers of all four. */
    public record HashSettings(
            HashAlgorithm algorithm,
            int argon2MemoryKib,
            int argon2Iterations,
            int argon2Parallelism,
            int bcryptCost,
            int pbkdf2Iterations) {
        public HashSettings {
            Objects.requireNonNull(algorithm, "algorithm");
        }
    }

    /** How many hashes run at once, and how many may wait. */
    public record Lane(int threads, int maxThreads, int queue) {
        public Lane {
            if (threads < 1 || maxThreads < threads || queue < 1) {
                throw new IllegalArgumentException("lane needs threads of at least 1, max-threads no lower than"
                        + " threads, and a queue of at least 1; got " + threads + ", " + maxThreads + ", " + queue);
            }
        }
    }

    /** What a player waiting in the world may not do, one switch each. */
    public record Hold(
            boolean movement,
            boolean chat,
            boolean commands,
            List<String> allowedCommands,
            boolean blocks,
            boolean interact,
            boolean inventory,
            boolean damage,
            boolean vehicles,
            boolean portals,
            boolean teleports,
            boolean hidePlayers,
            boolean blindness,
            boolean hideInventory,
            boolean hideStats) {
        public Hold {
            allowedCommands = List.copyOf(Objects.requireNonNull(allowedCommands, "allowedCommands"));
        }
    }

    /** How long a player may wait, and how often they are reminded. */
    public record Timeouts(Duration register, Duration login, Duration reminder) {
        public Timeouts {
            Objects.requireNonNull(register, "register");
            Objects.requireNonNull(login, "login");
            Objects.requireNonNull(reminder, "reminder");
        }
    }

    /** The moments an operator gives a list of effects to. */
    public enum Moment {
        JOIN_UNREGISTERED("join-unregistered"),
        JOIN_REGISTERED("join-registered"),
        REMINDER_REGISTER("reminder-register"),
        REMINDER_LOGIN("reminder-login"),
        WRONG_PASSWORD("wrong-password"),
        REGISTERED("registered"),
        LOGGED_IN("logged-in"),
        TIMEOUT("timeout"),
        TOO_MANY_ATTEMPTS("too-many-attempts"),
        LOGGED_OUT("logged-out"),
        PASSWORD_CHANGED("password-changed");

        private final String key;

        Moment(String key) {
            this.key = key;
        }

        /** The name of the list in the {@code effects} block. */
        public String key() {
            return key;
        }
    }

    /** One list of action lines per moment, as the operator wrote it. */
    public record Effects(Map<Moment, List<String>> lists) {
        public Effects {
            Objects.requireNonNull(lists, "lists");
            Map<Moment, List<String>> copy = new EnumMap<>(Moment.class);
            lists.forEach((moment, lines) -> copy.put(moment, List.copyOf(lines)));
            lists = Map.copyOf(copy);
        }

        public List<String> of(Moment moment) {
            return lists.getOrDefault(Objects.requireNonNull(moment, "moment"), List.of());
        }
    }
}
