package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.PasswordHasher;
import com.uxplima.auth.domain.hash.ScryptHasher;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * A Blysis website whose members are the password accounts: its {@code "User"} table holds the password, a member
 * logs in under their username, and a registration in game makes a member there. Reached through a pool of its own,
 * so this plugin's tables may live in any other database.
 *
 * @param jdbcUrl the Blysis database, which Blysis keeps in PostgreSQL
 * @param poolSize how many connections the pool holds
 * @param timeout how long a lookup waits for a connection before the login it serves fails
 * @param memberRole the name of the role a member made in game is given
 * @param email the address a member made in game is given, with {@code {name}} for the name in lower case
 * @param locale the language a member made in game is given
 * @param currency the currency a member made in game is given
 * @param hash the format new passwords are written in, the one the website is set to
 */
public record BlysisSettings(
        boolean enabled,
        String jdbcUrl,
        String username,
        String password,
        int poolSize,
        Duration timeout,
        String memberRole,
        String email,
        String locale,
        String currency,
        Hash hash) {

    /** What {@link #email} holds where the name goes. */
    static final String NAME = "{name}";

    /** The formats Blysis writes, by the word the operator writes. */
    public enum Hash {
        BCRYPT("bcrypt"),
        SCRYPT("scrypt");

        private final String key;

        Hash(String key) {
            this.key = key;
        }

        /**
         * The hasher that writes this format as Blysis writes it. bcrypt takes the operator's cost, which Blysis
         * writes as 12; a member's hash under another cost is rewritten at their next login.
         */
        public PasswordHasher hasher(int bcryptCost) {
            return switch (this) {
                case BCRYPT -> new BcryptHasher(bcryptCost);
                case SCRYPT -> new ScryptHasher();
            };
        }
    }

    public BlysisSettings {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(memberRole, "memberRole");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(hash, "hash");
        if (poolSize < 1) {
            throw new IllegalArgumentException("blysis.pool-size must be at least 1, got: " + poolSize);
        }
        if (enabled) {
            if (jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("blysis is on and blysis.jdbc-url is empty: name the Blysis"
                        + " database, such as jdbc:postgresql://127.0.0.1:5432/blysis");
            }
            AuthDatabase.driverOf(jdbcUrl, "blysis.jdbc-url");
            int at = email.indexOf('@');
            if (!email.contains(NAME) || at < 1 || at == email.length() - 1) {
                throw new IllegalArgumentException("blysis.email is " + email
                        + ", and it must be an address with {name} in it, such as {name}@players.example.com");
            }
            if (memberRole.isBlank() || locale.isBlank() || currency.isBlank()) {
                throw new IllegalArgumentException("blysis.member-role, blysis.locale and blysis.currency may not be"
                        + " empty while blysis is on");
            }
        }
    }

    /** The address a member made in game under this name is given. */
    public String emailOf(String name) {
        return email.replace(NAME, Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT));
    }

    /** Neither the password nor the parts of the address that may hold one. */
    @Override
    public String toString() {
        return "BlysisSettings[enabled=" + enabled + ", jdbcUrl=" + AuthDatabase.shown(jdbcUrl) + ", username="
                + username + ", password=hidden, poolSize=" + poolSize + ", hash=" + hash.key + "]";
    }

    static BlysisSettings read(HoconConfig config) {
        int seconds = config.getInt("blysis.timeout-seconds", 5);
        if (seconds < 1) {
            throw new IllegalArgumentException("blysis.timeout-seconds must be at least 1, got: " + seconds);
        }
        return new BlysisSettings(
                config.getBoolean("blysis.enabled", false),
                config.getString("blysis.jdbc-url", "").trim(),
                config.getString("blysis.username", ""),
                config.getString("blysis.password", ""),
                config.getInt("blysis.pool-size", 4),
                Duration.ofSeconds(seconds),
                config.getString("blysis.member-role", "member").trim(),
                config.getString("blysis.email", "{name}@players.invalid").trim(),
                config.getString("blysis.locale", "en").trim(),
                config.getString("blysis.currency", "USD").trim(),
                hash(config.getString("blysis.hash", "bcrypt")));
    }

    private static Hash hash(String written) {
        for (Hash hash : Hash.values()) {
            if (hash.key.equalsIgnoreCase(written.trim())) {
                return hash;
            }
        }
        throw new IllegalArgumentException(
                "blysis.hash is " + written + ", and Blysis writes bcrypt or scrypt: set the one the website uses");
    }
}
