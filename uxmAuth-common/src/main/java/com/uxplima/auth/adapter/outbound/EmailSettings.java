package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.uxplima.auth.application.mail.MailCodes;
import com.uxplima.auth.domain.mail.DomainRule;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The email block of config.conf: which domains an address may be at, and how the mailed codes behave.
 *
 * @param domains the allowed and the denied domains
 * @param codes how long a code lasts, how many tries it takes, how soon another may go, and its length
 */
public record EmailSettings(DomainRule domains, MailCodes.Settings codes) {
    public EmailSettings {
        Objects.requireNonNull(domains, "domains");
        Objects.requireNonNull(codes, "codes");
    }

    /** Reads the block, naming it in any value it refuses. */
    static EmailSettings read(HoconConfig config) {
        return new EmailSettings(
                new DomainRule(
                        SettingsReader.list(config, "email", "allowed-domains", List.of()),
                        SettingsReader.list(config, "email", "denied-domains", List.of())),
                new MailCodes.Settings(
                        Duration.ofMinutes(config.getInt("email.code-minutes", 15)),
                        config.getInt("email.code-attempts", 5),
                        Duration.ofSeconds(config.getInt("email.cooldown-seconds", 60)),
                        config.getInt("email.code-length", 6)));
    }
}
