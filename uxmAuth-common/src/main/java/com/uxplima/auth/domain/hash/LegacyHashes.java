package com.uxplima.auth.domain.hash;

import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Rewrites a hash another login plugin stored into a form this plugin reads, once, at the import.
 *
 * <p>A source names how it writes its hashes with a {@link Style}; every style also passes a hash the plugin
 * reads as it is, because a source that moved some of its players to bcrypt keeps both. A hash nothing here reads
 * is answered with its prefix alone, so a report can say which format it was without holding any of it.
 */
public final class LegacyHashes {

    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");
    private static final Pattern DOLLAR_SHA = Pattern.compile("^\\$SHA\\$([^$]+)\\$([0-9a-fA-F]+)$");
    private static final Pattern HASH_FIRST = Pattern.compile("^\\$SHA(?:512)?\\$([0-9a-fA-F]+)\\$([^$]+)$");
    private static final Pattern NAME_SALT_HASH = Pattern.compile("^(SHA256|SHA512)\\$([^$]*)\\$([0-9a-fA-F]+)$");
    private static final Pattern AUTHME = Pattern.compile("^\\$SHA\\$[0-9A-Za-z]+\\$[0-9a-fA-F]{64}$");
    private static final Pattern SPLIT_BCRYPT = Pattern.compile("^(\\d{2})\\$([./A-Za-z0-9]{31})$");
    private static final Pattern BCRYPT_SALT = Pattern.compile("[./A-Za-z0-9]{22}");
    private static final Pattern SPLIT_ARGON2 = Pattern.compile("^(\\d+),(\\d+),(\\d+)\\$([A-Za-z0-9+/=]+)$");
    private static final Pattern WORD = Pattern.compile("[^A-Za-z0-9$_-]");

    /** How a source writes its hashes, by the word its layout file uses. */
    public enum Style {
        AS_IS("as-is"),
        DOLLAR_SHA("dollar-sha"),
        HASH_FIRST("hash-first"),
        NAME_SALT_HASH("name-salt-hash"),
        SALT_COLUMN("salt-column"),
        SALTED("salted"),
        PLAIN("plain"),
        TWICE("twice"),
        ALGORITHM_COLUMN("algorithm-column");

        private final String word;

        Style(String word) {
            this.word = word;
        }

        public String word() {
            return word;
        }

        /** The style a layout file names, or a refusal that lists every word. */
        public static Style of(String word) {
            Objects.requireNonNull(word, "word");
            return Arrays.stream(values())
                    .filter(style -> style.word.equals(word.trim().toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("hash-style is " + word + ", and the styles are "
                            + Arrays.stream(values()).map(Style::word).collect(Collectors.joining(", "))));
        }
    }

    /** What a source's hash became. */
    public sealed interface Read permits Known, Unknown {}

    /** A hash the plugin reads. */
    public record Known(PasswordHash hash) implements Read {
        public Known {
            Objects.requireNonNull(hash, "hash");
        }
    }

    /**
     * A hash nothing here reads, named by its prefix or its algorithm's name. The hash is kept as the source wrote
     * it, so a later version that reads it finds it where it was; a report prints the format and never the hash.
     */
    public record Unknown(String format, @Nullable PasswordHash kept) implements Read {
        public Unknown {
            Objects.requireNonNull(format, "format");
        }
    }

    private LegacyHashes() {}

    /**
     * The hash this plugin reads for a source's stored one.
     *
     * @param salt the salt, for a source that keeps it in a column of its own
     * @param algorithm the algorithm's name, for a source that keeps it in a column of its own
     */
    public static Read rewrite(Style style, String raw, @Nullable String salt, @Nullable String algorithm) {
        Objects.requireNonNull(style, "style");
        String written = Objects.requireNonNull(raw, "raw").trim();
        if (written.isEmpty()) {
            return new Unknown("empty", null);
        }
        Optional<PasswordHash> read = styled(style, written, salt, algorithm).or(() -> asItIs(written));
        if (read.isPresent()) {
            return new Known(read.get());
        }
        String format = style == Style.ALGORITHM_COLUMN && algorithm != null ? named(algorithm) : prefixOf(written);
        return new Unknown(format, new PasswordHash(written));
    }

    private static Optional<PasswordHash> styled(
            Style style, String raw, @Nullable String salt, @Nullable String algorithm) {
        return switch (style) {
            case AS_IS -> Optional.empty();
            case DOLLAR_SHA -> dollarSha(raw);
            case HASH_FIRST -> matched(HASH_FIRST, raw).flatMap(parts -> digest("double-salted", parts[1], parts[0]));
            case NAME_SALT_HASH -> nameSaltHash(raw);
            case SALT_COLUMN -> salt == null ? Optional.empty() : digest("double-salted", salt, raw);
            case SALTED -> lastTwo(raw).flatMap(parts -> digest("salted", parts[0], parts[1]));
            case PLAIN -> digest("plain", "", lastOf(raw));
            case TWICE -> digest("twice", "", lastOf(raw));
            case ALGORITHM_COLUMN -> algorithmColumn(raw, salt, algorithm);
        };
    }

    /** {@code $SHA$salt$hex}: SHA-256 is read as the website writes it, SHA-512 is rewritten. */
    private static Optional<PasswordHash> dollarSha(String raw) {
        return matched(DOLLAR_SHA, raw)
                .flatMap(parts -> parts[1].length() == LegacyDigestHasher.length("sha256")
                        ? Optional.of(new PasswordHash(raw))
                        : digest("double-salted", parts[0], parts[1]));
    }

    private static Optional<PasswordHash> nameSaltHash(String raw) {
        if (raw.startsWith("BCRYPT$")) {
            return Optional.of(new PasswordHash("$2a$" + raw.substring("BCRYPT$".length())));
        }
        return matched(NAME_SALT_HASH, raw).flatMap(parts -> {
            String algorithm = parts[0].toLowerCase(Locale.ROOT);
            return parts[2].length() == LegacyDigestHasher.length(algorithm)
                    ? digest("double-salted", parts[1], parts[2])
                    : Optional.empty();
        });
    }

    /** A hash split over columns: the hash, its salt, and the algorithm's name beside them. */
    private static Optional<PasswordHash> algorithmColumn(
            String raw, @Nullable String salt, @Nullable String algorithm) {
        if (algorithm == null) {
            return Optional.empty();
        }
        String name = algorithm.trim().toUpperCase(Locale.ROOT);
        if (name.equals("SHA-256") || name.equals("SHA-512")) {
            String algorithmWord = name.equals("SHA-256") ? "sha256" : "sha512";
            if (raw.length() != LegacyDigestHasher.length(algorithmWord)) {
                return Optional.empty();
            }
            return salt == null || salt.isEmpty() ? digest("plain", "", raw) : digest("double-salted", salt, raw);
        }
        if (salt == null) {
            return Optional.empty();
        }
        if (name.startsWith("BCRYPT-")) {
            return splitBcrypt(name.substring("BCRYPT-".length()).toLowerCase(Locale.ROOT), raw, salt);
        }
        return name.equals("ARGON-2ID") ? splitArgon2id(raw, salt) : Optional.empty();
    }

    /** bcrypt kept as {@code cost$hash} beside a 22-character salt: put back together. */
    private static Optional<PasswordHash> splitBcrypt(String version, String raw, String salt) {
        return matched(SPLIT_BCRYPT, raw)
                .filter(parts -> BCRYPT_SALT.matcher(salt).matches())
                .map(parts -> new PasswordHash("$" + version + "$" + parts[0] + "$" + salt + parts[1]));
    }

    /** Argon2id kept as {@code version,iterations,memory$hash} beside its salt, both in Base64: written as PHC. */
    private static Optional<PasswordHash> splitArgon2id(String raw, String salt) {
        return matched(SPLIT_ARGON2, raw).flatMap(parts -> {
            try {
                Base64.Encoder bare = Base64.getEncoder().withoutPadding();
                Base64.Decoder padded = Base64.getDecoder();
                String phc = "$argon2id$v=" + parts[0] + "$m=" + parts[2] + ",t=" + parts[1] + ",p=1$"
                        + bare.encodeToString(padded.decode(salt)) + "$" + bare.encodeToString(padded.decode(parts[3]));
                return Optional.of(new PasswordHash(phc));
            } catch (IllegalArgumentException notBase64) {
                return Optional.empty();
            }
        });
    }

    /** A hash this plugin reads as the source wrote it. */
    private static Optional<PasswordHash> asItIs(String raw) {
        if (raw.startsWith("BCRYPT$")) {
            return nameSaltHash(raw);
        }
        if (raw.startsWith("pbkdf2$")) {
            // PBKDF2-HMAC-SHA256 with a Base64 salt and key: the plugin's own form under a shorter name.
            return asItIs("pbkdf2-sha256$" + raw.substring("pbkdf2$".length()));
        }
        PasswordHash hash = new PasswordHash(raw);
        Optional<HashAlgorithm> recognised = HashFormats.recognise(hash);
        if (recognised.isEmpty()
                || (recognised.get() == HashAlgorithm.AUTHME_SHA256
                        && !AUTHME.matcher(raw).matches())) {
            return Optional.empty();
        }
        return Optional.of(hash);
    }

    /** The digest the parts make, its algorithm read off the length of the hex. */
    private static Optional<PasswordHash> digest(String mode, String salt, String hex) {
        String algorithm =
                switch (hex.length()) {
                    case 32 -> "md5";
                    case 64 -> "sha256";
                    case 128 -> "sha512";
                    default -> "";
                };
        if (algorithm.isEmpty()
                || !HEX.matcher(hex).matches()
                || !LegacyDigestHasher.readable(algorithm, mode, salt, hex)) {
            return Optional.empty();
        }
        return Optional.of(LegacyDigestHasher.encode(algorithm, mode, salt, hex));
    }

    private static Optional<String[]> matched(Pattern pattern, String raw) {
        Matcher parts = pattern.matcher(raw);
        if (!parts.matches()) {
            return Optional.empty();
        }
        String[] groups = new String[parts.groupCount()];
        for (int group = 0; group < groups.length; group++) {
            groups[group] = parts.group(group + 1);
        }
        return Optional.of(groups);
    }

    /** The salt and the hex a {@code [$][SHA$]salt$hex} hash ends with. */
    private static Optional<String[]> lastTwo(String raw) {
        int last = raw.lastIndexOf('$');
        if (last < 1) {
            return Optional.empty();
        }
        int before = raw.lastIndexOf('$', last - 1);
        return Optional.of(new String[] {raw.substring(before + 1, last), raw.substring(last + 1)});
    }

    private static String lastOf(String raw) {
        return raw.substring(raw.lastIndexOf('$') + 1);
    }

    /** The prefix that names a format: {@code $name$}, or {@code name$}, and never the hash itself. */
    private static String prefixOf(String raw) {
        int end = raw.indexOf('$', raw.startsWith("$") ? 1 : 0);
        if (end < 0 || end > 20) {
            return "no prefix";
        }
        return named(raw.substring(0, end + 1));
    }

    private static String named(String text) {
        String clean = WORD.matcher(text).replaceAll("");
        return clean.length() > 24 ? clean.substring(0, 24) : clean;
    }
}
