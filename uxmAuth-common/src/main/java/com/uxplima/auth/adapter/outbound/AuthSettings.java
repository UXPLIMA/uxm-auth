package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.uxplima.auth.adapter.outbound.premium.PremiumSource;
import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.network.AfterLogin;
import com.uxplima.auth.domain.network.Balance;
import com.uxplima.auth.domain.network.Destinations;
import com.uxplima.auth.domain.network.Route;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;

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

    /**
     * How a name is asked of the web: the sources in order, how long an answer is kept, how many requests
     * Mojang is sent in ten minutes, how long one may take, and where each source answers.
     */
    public record Premium(
            List<PremiumSource> sources,
            Duration keepAnswers,
            int mojangCap,
            Duration timeout,
            Map<PremiumSource, String> addresses) {
        public Premium {
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            Objects.requireNonNull(keepAnswers, "keepAnswers");
            Objects.requireNonNull(timeout, "timeout");
            addresses = Map.copyOf(Objects.requireNonNull(addresses, "addresses"));
        }

        /** Where this source answers, with {@code {name}} in place of the name. */
        public String address(PremiumSource source) {
            return addresses.getOrDefault(Objects.requireNonNull(source, "source"), source.shippedAddress());
        }
    }

    /**
     * Who owns a name: the rule, what a name nobody could look up is taken for, how long a failed handshake is
     * remembered, how long a claim waits for its next connection, the addresses of the domain rule, the UUID a
     * player plays under, and what happens to a cracked account whose name its paid owner proves.
     */
    public record Ownership(
            NameRule rule,
            WhenUnknown whenUnknown,
            Duration failedMemory,
            Duration claimMemory,
            List<String> premiumDomains,
            List<String> crackedDomains,
            UuidScheme uuidScheme,
            ConflictStrategy conflict,
            String renameMark,
            DomainMark crackedMark) {
        public Ownership {
            Objects.requireNonNull(uuidScheme, "uuidScheme");
            Objects.requireNonNull(conflict, "conflict");
            Objects.requireNonNull(renameMark, "renameMark");
            Objects.requireNonNull(crackedMark, "crackedMark");
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(whenUnknown, "whenUnknown");
            Objects.requireNonNull(failedMemory, "failedMemory");
            Objects.requireNonNull(claimMemory, "claimMemory");
            premiumDomains = List.copyOf(Objects.requireNonNull(premiumDomains, "premiumDomains"));
            crackedDomains = List.copyOf(Objects.requireNonNull(crackedDomains, "crackedDomains"));
        }

        /** The decision the door makes under this rule. */
        public NameOwnership decision() {
            return new NameOwnership(
                    rule, whenUnknown, new HostPatterns(premiumDomains), new HostPatterns(crackedDomains));
        }
    }

    /** Where a player waits on a network before logging in. */
    public enum WaitIn {
        /** On a server, at its dialog or in its frozen world. */
        SERVER,
        /** In a world LimboAPI builds inside the proxy, which no server sees. */
        LIMBO
    }

    /**
     * Where the proxy sends a player: an auth server while they wait, and once logged in a lobby, their last
     * server or the server of the address they typed. Every name is a server in the proxy's own list.
     */
    public record Routing(
            List<String> authServers,
            List<String> lobbies,
            Balance balance,
            AfterLogin afterLogin,
            List<Route> byDomain) {
        public Routing {
            authServers = List.copyOf(Objects.requireNonNull(authServers, "authServers"));
            lobbies = List.copyOf(Objects.requireNonNull(lobbies, "lobbies"));
            Objects.requireNonNull(balance, "balance");
            Objects.requireNonNull(afterLogin, "afterLogin");
            byDomain = List.copyOf(Objects.requireNonNull(byDomain, "byDomain"));
        }

        /** The rule that picks among these servers. */
        public Destinations destinations(RandomGenerator random) {
            return new Destinations(balance, afterLogin, lobbies, byDomain, random);
        }
    }

    /** What a Bedrock player Floodgate vouched for gets: in at once, or the same password as everyone. */
    public record Bedrock(boolean autoLogin) {}

    /**
     * Whether this server is a backend behind the proxy half, and what the two share.
     *
     * @param secret the key the proxy signs its claims with, copied from the proxy's data folder
     * @param claimFor how long a signed claim stays fresh after the proxy wrote it
     * @param allowedCommands the proxy's own commands a player may run before logging in
     */
    public record Network(
            boolean enabled,
            String secret,
            Duration claimFor,
            List<String> allowedCommands,
            Routing routing,
            WaitIn waitIn,
            String limboFile,
            Spot limboSpawn,
            Redis redis) {
        public Network {
            Objects.requireNonNull(secret, "secret");
            Objects.requireNonNull(claimFor, "claimFor");
            allowedCommands = List.copyOf(Objects.requireNonNull(allowedCommands, "allowedCommands"));
            Objects.requireNonNull(routing, "routing");
            Objects.requireNonNull(waitIn, "waitIn");
            Objects.requireNonNull(limboFile, "limboFile");
            Objects.requireNonNull(limboSpawn, "limboSpawn");
            Objects.requireNonNull(redis, "redis");
            if (enabled && secret.isBlank()) {
                throw new IllegalArgumentException("network.enabled is true and network.secret is empty. The proxy"
                        + " writes the secret into its own data folder on its first start: copy it here.");
            }
        }

        /** Never printed: the secret is what keeps a forged claim out. */
        @Override
        public String toString() {
            return "Network[enabled=" + enabled + ", claimFor=" + claimFor + "]";
        }
    }

    /**
     * Where the bulletins of a network travel: a Redis every member reaches, and the channel on it. An empty
     * address leaves Redis off.
     */
    public record Redis(String uri, String channel) {
        public Redis {
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(channel, "channel");
            if (channel.isBlank()) {
                throw new IllegalArgumentException("network.redis.channel is empty. Name the channel every member"
                        + " of this network listens on.");
            }
        }

        /** Whether the operator wrote an address. */
        public boolean on() {
            return !uri.isBlank();
        }

        /** Never printed: the address may carry the Redis password. */
        @Override
        public String toString() {
            return "Redis[on=" + on() + ", channel=" + channel + "]";
        }
    }

    /** A point a player stands on, and the way they face there. */
    public record Spot(double x, double y, double z, float yaw, float pitch) {}

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
        BANNED("banned"),
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
