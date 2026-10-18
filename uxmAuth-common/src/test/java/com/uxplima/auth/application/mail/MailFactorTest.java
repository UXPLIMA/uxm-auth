package com.uxplima.auth.application.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.testing.InMemoryEmails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The code by mail as a second factor: on with a linked address, off only with a code that passes. */
final class MailFactorTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final InMemoryEmails emails = new InMemoryEmails();
    private final List<String> failed = new ArrayList<>();
    private Optional<Duration> ban = Optional.empty();
    private final MailFactor factor =
            new MailFactor(emails, (account, typed) -> "424242".equals(typed), (address, host) -> {
                failed.add(address);
                return ban;
            });

    @Test
    @DisplayName("on needs a linked address, and says so twice over")
    void onNeedsAnAddress() {
        assertThat(factor.on(STEVE)).isEqualTo(new MailFactor.Outcome.NoAddress());

        emails.setAddress(STEVE, EmailAddress.parse("steve@example.net").orElseThrow());
        assertThat(factor.on(STEVE)).isEqualTo(new MailFactor.Outcome.On("s***@e***.net"));
        assertThat(emails.mailsLoginCode(STEVE)).isTrue();
        assertThat(factor.on(STEVE)).isEqualTo(new MailFactor.Outcome.AlreadyOn());
    }

    @Test
    @DisplayName("off takes a code that passes; a wrong one counts against the address, and a ban is said")
    void offNeedsACode() {
        emails.setAddress(STEVE, EmailAddress.parse("steve@example.net").orElseThrow());
        assertThat(factor.off(STEVE, "424242", "203.0.113.7", "h")).isEqualTo(new MailFactor.Outcome.NotOn());
        factor.on(STEVE);

        assertThat(factor.off(STEVE, "000000", "203.0.113.7", "h")).isEqualTo(new MailFactor.Outcome.Wrong());
        assertThat(failed).containsExactly("203.0.113.7");
        ban = Optional.of(Duration.ofMinutes(5));
        assertThat(factor.off(STEVE, "000000", "203.0.113.7", "h"))
                .isEqualTo(new MailFactor.Outcome.Banned(Duration.ofMinutes(5)));
        emails.changeCode(
                STEVE,
                Emails.Purpose.LOGIN,
                before -> Optional.of(new Emails.Code(
                        EmailAddress.parse("steve@example.net").orElseThrow(),
                        new PasswordHash("$live$"),
                        Instant.parse("2030-01-01T00:00:00Z"),
                        5,
                        Instant.parse("2026-01-01T00:00:00Z"))));
        assertThat(factor.off(STEVE, "424242", "203.0.113.7", "h")).isEqualTo(new MailFactor.Outcome.Off());
        assertThat(emails.mailsLoginCode(STEVE)).isFalse();
        assertThat(emails.code(STEVE, Emails.Purpose.LOGIN))
                .as("a code out when it went off answers nothing later")
                .isEmpty();
    }
}
