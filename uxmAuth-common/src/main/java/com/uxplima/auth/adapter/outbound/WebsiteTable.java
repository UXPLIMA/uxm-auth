package com.uxplima.auth.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.hash.Argon2iHasher;
import com.uxplima.auth.domain.hash.AuthMeSha256Hasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.PasswordHasher;
import com.uxplima.auth.domain.hash.Pbkdf2HexHasher;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * A website's login table, in the format many login plugins share and websites such as LeaderOS and Minexon read.
 * On, every password account is written there too, the password in the website's own format, and that table is
 * where a password lives: a password changed on the website works at the next login.
 *
 * @param table the table's name, used as it is when it exists and made when it does not
 * @param hash the format the website reads, which every new password is written in while the table is on
 * @param columns the name of each column the website reads
 */
public record WebsiteTable(boolean enabled, String table, Hash hash, Columns columns) {

    /** What a table or column name may be: a plain SQL name, so it can stand in a statement unquoted. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    /** The formats a website reads, by the word the operator writes. */
    public enum Hash {
        SHA256("sha256"),
        BCRYPT("bcrypt"),
        PBKDF2("pbkdf2"),
        ARGON2("argon2");

        private final String key;

        Hash(String key) {
            this.key = key;
        }

        /** The hasher that writes this format with the parameters the website writes it with. */
        public PasswordHasher hasher() {
            return switch (this) {
                case SHA256 -> new AuthMeSha256Hasher();
                case BCRYPT -> new BcryptHasher(10, "2a");
                case PBKDF2 -> new Pbkdf2HexHasher(10_000);
                case ARGON2 -> new Argon2iHasher(65_536, 2, 1);
            };
        }
    }

    /** The name of each column the website reads. */
    public record Columns(
            String name, String realName, String password, String ip, String lastLogin, String regDate, String regIp) {
        public Columns {
            for (String column : new String[] {name, realName, password, ip, lastLogin, regDate, regIp}) {
                requireName(column);
            }
        }
    }

    public WebsiteTable {
        requireName(table);
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(columns, "columns");
    }

    /**
     * What the start says when the table is on in a weak format. Every password is written in the website's format,
     * the plugin's own row too, because one hash is what both tables hold and what tells an account's row from
     * somebody else's; so sha256 here downgrades every account at its next login, and the operator must hear it.
     */
    public Optional<String> weakHashWarning() {
        if (!enabled || hash != Hash.SHA256) {
            return Optional.empty();
        }
        return Optional.of("website-table is on with hash = sha256, so every new password, and every older one at its"
                + " player's next login, is kept as one salted SHA-256 in this plugin's table as well as the"
                + " website's, whatever hashing.algorithm says. A copy of the database is then cracked in hours"
                + " rather than years. Set website-table.hash to bcrypt, pbkdf2 or argon2 if the website reads it.");
    }

    private static void requireName(String name) {
        if (!NAME.matcher(Objects.requireNonNull(name, "name")).matches()) {
            throw new IllegalArgumentException("not a plain SQL name: " + name);
        }
    }

    static WebsiteTable read(HoconConfig config) {
        return new WebsiteTable(
                config.getBoolean("website-table.enabled", false),
                name(config, "website-table.table", "authme"),
                hash(config.getString("website-table.hash", "sha256")),
                new Columns(
                        name(config, "website-table.columns.name", "username"),
                        name(config, "website-table.columns.real-name", "realname"),
                        name(config, "website-table.columns.password", "password"),
                        name(config, "website-table.columns.ip", "ip"),
                        name(config, "website-table.columns.last-login", "lastlogin"),
                        name(config, "website-table.columns.reg-date", "regdate"),
                        name(config, "website-table.columns.reg-ip", "regip")));
    }

    private static String name(HoconConfig config, String path, String fallback) {
        String written = config.getString(path, fallback);
        if (!NAME.matcher(written).matches()) {
            throw new IllegalArgumentException(
                    path + " is " + written + ", and a name is letters, digits and underscores, up to 64");
        }
        return written;
    }

    private static Hash hash(String written) {
        for (Hash hash : Hash.values()) {
            if (hash.key.equalsIgnoreCase(written.trim())) {
                return hash;
            }
        }
        throw new IllegalArgumentException(
                "website-table.hash is " + written + ", and a website reads sha256, bcrypt, pbkdf2 or argon2");
    }
}
