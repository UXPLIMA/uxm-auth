package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The mail block of config.conf: the server mail leaves through, how it is reached, and who it is from.
 *
 * @param security plain, TLS from the first byte, or upgraded by STARTTLS
 * @param username the login at the server; empty for a server that takes mail without one
 * @param timeout how long a mail waits on the server before it fails
 * @param threads how many mails go out at once, read at start
 */
public record MailSettings(
        boolean enabled,
        String host,
        int port,
        Security security,
        String username,
        String password,
        String fromAddress,
        String fromName,
        Duration timeout,
        int threads) {

    /** How the connection to the mail server is secured. */
    public enum Security {
        PLAIN,
        SSL,
        STARTTLS
    }

    private static final int HIGHEST_PORT = 65_535;

    public MailSettings {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(security, "security");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(fromAddress, "fromAddress");
        Objects.requireNonNull(fromName, "fromName");
        Objects.requireNonNull(timeout, "timeout");
        if (port < 1 || port > HIGHEST_PORT) {
            throw new IllegalArgumentException("mail.port must be 1 to 65535, not " + port);
        }
        if (threads < 1) {
            throw new IllegalArgumentException("mail.threads must be at least 1, not " + threads);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("mail.timeout-seconds must be at least 1");
        }
        if (enabled && host.isBlank()) {
            throw new IllegalArgumentException("mail.host must name the mail server when mail is enabled");
        }
        if (enabled && EmailAddress.parse(fromAddress).isEmpty()) {
            throw new IllegalArgumentException("mail.from-address must be an address when mail is enabled");
        }
    }

    /** Reads the block, naming it in any value it refuses. */
    static MailSettings read(HoconConfig config) {
        String security = config.getString("mail.security", "starttls");
        try {
            return new MailSettings(
                    config.getBoolean("mail.enabled", false),
                    config.getString("mail.host", "").strip(),
                    config.getInt("mail.port", 587),
                    Security.valueOf(security.strip().toUpperCase(Locale.ROOT)),
                    config.getString("mail.username", ""),
                    config.getString("mail.password", ""),
                    config.getString("mail.from-address", "").strip(),
                    config.getString("mail.from-name", "Minecraft"),
                    Duration.ofSeconds(config.getInt("mail.timeout-seconds", 10)),
                    config.getInt("mail.threads", 2));
        } catch (IllegalArgumentException wrong) {
            String message = wrong.getMessage() != null && wrong.getMessage().startsWith("mail.")
                    ? wrong.getMessage()
                    : "mail.security must be plain, ssl or starttls, not " + security;
            throw new IllegalArgumentException(message, wrong);
        }
    }

    /** Never the password. */
    @Override
    public String toString() {
        return "MailSettings[enabled=" + enabled + ", host=" + host + ", port=" + port + ", security=" + security
                + ", username=" + username + ", password=***, fromAddress=" + fromAddress + ", fromName=" + fromName
                + ", timeout=" + timeout + ", threads=" + threads + "]";
    }
}
