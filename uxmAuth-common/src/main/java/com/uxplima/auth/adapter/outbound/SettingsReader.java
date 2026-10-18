package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.uxplima.auth.adapter.outbound.premium.PremiumSource;
import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.network.AfterLogin;
import com.uxplima.auth.domain.network.Balance;
import com.uxplima.auth.domain.network.Route;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.MarkedName;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.uxmlib.config.HoconConfig;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads each block of {@code config.conf} into its record, with the default the shipped file also says.
 *
 * <p>A value that cannot work is refused with its path, so the operator reads which line to fix rather than
 * a stack trace about something three calls away.
 */
final class SettingsReader {

    private SettingsReader() {}

    static AuthSettings.Names names(HoconConfig config) {
        return new AuthSettings.Names(
                pattern(config, "names.pattern", "[A-Za-z0-9_]+"),
                config.getInt("names.min-length", 3),
                config.getInt("names.max-length", 16),
                list(config, "names", "blocked", List.of()),
                config.getBoolean("names.reconnect-from-same-address", false));
    }

    static AuthSettings.Passwords passwords(HoconConfig config) {
        return new AuthSettings.Passwords(
                config.getInt("passwords.min-length", 6),
                config.getInt("passwords.max-length", 64),
                pattern(config, "passwords.allowed", "\\S+"),
                required(config),
                config.getBoolean("passwords.confirm-on-register", true),
                config.getBoolean("passwords.old-on-change", true),
                config.getInt("passwords.max-attempts", 3));
    }

    static AuthSettings.PasswordLists passwordLists(HoconConfig config) {
        return new AuthSettings.PasswordLists(
                config.getString("passwords.common-file", "passwords/common.txt")
                        .strip(),
                config.getBoolean("passwords.breach-check", false));
    }

    static AuthSettings.HashSettings hashing(HoconConfig config) {
        return new AuthSettings.HashSettings(
                algorithm(config.getString("hashing.algorithm", "argon2id")),
                config.getInt("hashing.argon2id.memory-kib", 65536),
                config.getInt("hashing.argon2id.iterations", 3),
                config.getInt("hashing.argon2id.parallelism", 1),
                config.getInt("hashing.bcrypt.cost", 12),
                config.getInt("hashing.pbkdf2.iterations", 600_000));
    }

    static AuthSettings.Lane lane(HoconConfig config) {
        return new AuthSettings.Lane(
                config.getInt("lane.threads", 2),
                config.getInt("lane.max-threads", 4),
                config.getInt("lane.queue", 256));
    }

    static AuthSettings.Hold hold(HoconConfig config) {
        return new AuthSettings.Hold(
                held(config, "movement"),
                held(config, "chat"),
                held(config, "commands"),
                list(config, "hold", "allowed-commands", List.of()),
                held(config, "blocks"),
                held(config, "interact"),
                held(config, "inventory"),
                held(config, "damage"),
                held(config, "vehicles"),
                held(config, "portals"),
                held(config, "teleports"),
                held(config, "hide-players"),
                held(config, "blindness"),
                held(config, "hide-inventory"),
                held(config, "hide-stats"));
    }

    static AuthSettings.Timeouts timeouts(HoconConfig config) {
        return new AuthSettings.Timeouts(
                seconds(config, "timeouts.register-seconds", 90),
                seconds(config, "timeouts.login-seconds", 60),
                seconds(config, "timeouts.reminder-seconds", 5));
    }

    static AuthSettings.Bedrock bedrock(HoconConfig config) {
        return new AuthSettings.Bedrock(config.getBoolean("bedrock.auto-login", true));
    }

    static AuthSettings.Network network(HoconConfig config) {
        return new AuthSettings.Network(
                config.getBoolean("network.enabled", false),
                config.getString("network.secret", "").trim(),
                Duration.ofSeconds(positive(config, "network.claim-seconds", 30)),
                list(config, "network", "allowed-commands", List.of()).stream()
                        .map(command -> command.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", ""))
                        .toList(),
                routing(config),
                word(config, "network.wait-in", "server", AuthSettings.WaitIn.class),
                config.getString("network.limbo-file", "limbo.schem").trim(),
                spot(list(config, "network", "limbo-spawn", List.of("0.5", "100", "0.5")), "network.limbo-spawn"),
                new AuthSettings.Redis(
                        config.getString("network.redis.uri", "").trim(),
                        config.getString("network.redis.channel", "uxmauth").trim()));
    }

    /** A point written as x, y and z, with a yaw and a pitch after them when the operator gives a facing. */
    private static AuthSettings.Spot spot(List<String> written, String path) {
        if (written.size() != 3 && written.size() != 5) {
            throw new IllegalArgumentException(
                    path + " holds " + written.size() + " numbers: write x, y, z, and a yaw and a pitch if you like");
        }
        double[] numbers = new double[5];
        for (int i = 0; i < written.size(); i++) {
            try {
                numbers[i] = Double.parseDouble(written.get(i).trim());
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException(
                        path + " holds " + written.get(i) + ", which is not a number", notANumber);
            }
        }
        return new AuthSettings.Spot(numbers[0], numbers[1], numbers[2], (float) numbers[3], (float) numbers[4]);
    }

    private static AuthSettings.Routing routing(HoconConfig config) {
        return new AuthSettings.Routing(
                list(config, "network", "auth-servers", List.of()),
                list(config, "network", "lobbies", List.of()),
                word(config, "network.balance", "least-players", Balance.class),
                word(config, "network.after-login", "lobby", AfterLogin.class),
                routes(config));
    }

    /** The routes by domain, in the order written: the first whose address matches is taken. */
    private static List<Route> routes(HoconConfig config) {
        ConfigurationNode written = config.nodeAt("network", "by-domain");
        List<Route> routes = new ArrayList<>();
        for (ConfigurationNode entry : written.childrenList()) {
            String host = entry.node("host").getString("").trim();
            String server = entry.node("server").getString("").trim();
            if (host.isEmpty() || server.isEmpty()) {
                throw new IllegalArgumentException("network.by-domain holds an entry without a host or a server;"
                        + " write each as { host = \"eu.*\", server = lobby-eu }");
            }
            routes.add(new Route(host, server));
        }
        return routes;
    }

    /** Mojang refuses past this many requests in ten minutes. */
    private static final int MOJANG_LIMIT = 600;

    static AuthSettings.Premium premium(HoconConfig config) {
        int cap = config.getInt("premium.mojang-cap-per-10-minutes", 550);
        if (cap < 1 || cap >= MOJANG_LIMIT) {
            throw new IllegalArgumentException("premium.mojang-cap-per-10-minutes must be from 1 to "
                    + (MOJANG_LIMIT - 1) + ", below Mojang's own limit, got: " + cap);
        }
        Map<PremiumSource, String> addresses = new EnumMap<>(PremiumSource.class);
        for (PremiumSource source : PremiumSource.values()) {
            String path = "premium.addresses." + source.key();
            String address = config.getString(path, source.shippedAddress());
            if (!address.contains("{name}")) {
                throw new IllegalArgumentException(path + " must hold {name} where the name goes, got: " + address);
            }
            addresses.put(source, address);
        }
        return new AuthSettings.Premium(
                sources(config),
                Duration.ofMinutes(positive(config, "premium.cache-minutes", 30)),
                cap,
                Duration.ofMillis(positive(config, "premium.timeout-millis", 3000)),
                addresses);
    }

    static AuthSettings.Ownership ownership(HoconConfig config) {
        NameRule rule = word(config, "ownership.rule", "automatic", NameRule.class);
        List<String> premiumDomains = list(config, "ownership.domains", "premium", List.of());
        if (rule == NameRule.DOMAIN && premiumDomains.isEmpty()) {
            throw new IllegalArgumentException(
                    "ownership.domains.premium is empty, and the domain rule needs the address paid accounts join through");
        }
        return new AuthSettings.Ownership(
                rule,
                word(config, "ownership.when-every-source-fails", "cracked", WhenUnknown.class),
                Duration.ofSeconds(positive(config, "ownership.failed-memory-seconds", 300)),
                Duration.ofMinutes(positive(config, "ownership.claim-minutes", 10)),
                premiumDomains,
                list(config, "ownership.domains", "cracked", List.of()),
                word(config, "ownership.uuid-scheme", "keep-first", UuidScheme.class),
                word(config, "ownership.conflict", "rename", ConflictStrategy.class),
                renameMark(config),
                crackedMark(config));
    }

    private static DomainMark crackedMark(HoconConfig config) {
        String mark = config.getString("ownership.domains.cracked-mark", "+");
        String at = config.getString("ownership.domains.mark-at", "end").trim().toLowerCase(Locale.ROOT);
        if (!at.equals("start") && !at.equals("end")) {
            throw new IllegalArgumentException("ownership.domains.mark-at is " + at + ", and it is start or end");
        }
        try {
            return new DomainMark(mark, at.equals("start"));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("ownership.domains.cracked-mark: " + wrong.getMessage(), wrong);
        }
    }

    private static String renameMark(HoconConfig config) {
        String mark = config.getString("ownership.rename-mark", "_");
        if (mark.isEmpty() || mark.length() > MarkedName.LONGEST - 3) {
            throw new IllegalArgumentException("ownership.rename-mark must be from 1 to " + (MarkedName.LONGEST - 3)
                    + " characters, got: \"" + mark + "\"");
        }
        // A renamed player types the marked name at their next login, so the operator's own pattern must take it.
        if (!Pattern.compile(pattern(config, "names.pattern", "[A-Za-z0-9_]+"))
                .matcher("Steve" + mark)
                .matches()) {
            throw new IllegalArgumentException("ownership.rename-mark \"" + mark
                    + "\" makes names that names.pattern refuses, so a renamed player could never log in again");
        }
        return mark;
    }

    /** One of an enum's constants, written in config.conf in lower case with dashes. */
    private static <E extends Enum<E>> E word(HoconConfig config, String path, String fallback, Class<E> type) {
        String written = config.getString(path, fallback);
        String name = written.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        List<String> known = Arrays.stream(type.getEnumConstants())
                .map(constant -> constant.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .toList();
        throw new IllegalArgumentException(
                path + " is " + written + ", and this plugin knows " + String.join(", ", known));
    }

    private static List<PremiumSource> sources(HoconConfig config) {
        List<String> shipped =
                Arrays.stream(PremiumSource.values()).map(PremiumSource::key).toList();
        List<PremiumSource> sources = new ArrayList<>();
        for (String written : list(config, "premium", "sources", shipped)) {
            sources.add(PremiumSource.byKey(written)
                    .orElseThrow(() -> new IllegalArgumentException("premium.sources holds " + written
                            + ", and this plugin knows " + String.join(", ", shipped))));
        }
        return sources;
    }

    static int positive(HoconConfig config, String path, int fallback) {
        int value = config.getInt(path, fallback);
        if (value < 1) {
            throw new IllegalArgumentException(path + " must be at least 1, got: " + value);
        }
        return value;
    }

    static AuthSettings.Effects effects(HoconConfig config) {
        Map<AuthSettings.Moment, List<String>> lists = new EnumMap<>(AuthSettings.Moment.class);
        for (AuthSettings.Moment moment : AuthSettings.Moment.values()) {
            lists.put(moment, list(config, "effects", moment.key(), ShippedEffects.of(moment)));
        }
        return new AuthSettings.Effects(lists);
    }

    private static boolean held(HoconConfig config, String name) {
        return config.getBoolean("hold." + name, true);
    }

    /**
     * A list as the operator wrote it, or the shipped one when the file says nothing.
     *
     * <p>A list written as {@code []} is an operator's choice of nothing, and a list nobody wrote is the
     * default, so the node is asked whether it exists before it is read.
     */
    static List<String> list(HoconConfig config, String block, String name, List<String> shipped) {
        if (config.nodeAt((Object[]) (block + "." + name).split("\\.")).virtual()) {
            return shipped;
        }
        return List.copyOf(config.getList(block + "." + name, String.class));
    }

    private static String pattern(HoconConfig config, String path, String fallback) {
        String written = config.getString(path, fallback);
        try {
            Pattern.compile(written);
        } catch (PatternSyntaxException broken) {
            throw new IllegalArgumentException(
                    path + " is not a pattern Java can read: " + broken.getDescription(), broken);
        }
        return written;
    }

    private static Set<CharClass> required(HoconConfig config) {
        Set<CharClass> required = EnumSet.noneOf(CharClass.class);
        for (String name : list(config, "passwords", "required", List.of())) {
            try {
                required.add(CharClass.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException(
                        "passwords.required holds " + name + ", which is not lower, upper, digit or symbol", unknown);
            }
        }
        return required;
    }

    private static HashAlgorithm algorithm(String written) {
        String name = written.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return HashAlgorithm.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "hashing.algorithm is " + written
                            + ", and this plugin has argon2id, bcrypt, pbkdf2 and authme-sha256",
                    unknown);
        }
    }

    private static Duration seconds(HoconConfig config, String path, int fallback) {
        int value = config.getInt(path, fallback);
        if (value < 1) {
            throw new IllegalArgumentException(path + " must be at least 1, got: " + value);
        }
        return Duration.ofSeconds(value);
    }
}
