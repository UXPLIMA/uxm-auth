package com.uxplima.auth.application.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryEmails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The login code by mail, sent as the second step opens, once while it is good. */
final class MailLoginTest {

    private static final EmailAddress TO =
            EmailAddress.parse("steve@example.net").orElseThrow();

    private final Account steve = AuthFixture.account("Steve", new PasswordHash("$hash$x"));
    private final InMemoryEmails emails = new InMemoryEmails();
    private final HashingLane lane = new HashingLane(1, 1, 16);
    private final List<Mailer.Kind> kinds = new ArrayList<>();
    private final List<Map<String, String>> mailed = new ArrayList<>();
    private final MailLogin login = new MailLogin(
            emails,
            new MailCodes(
                    emails,
                    (kind, to, language, values) -> {
                        kinds.add(kind);
                        mailed.add(values);
                        return CompletableFuture.completedFuture(Mailer.Outcome.SENT);
                    },
                    new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4))),
                    lane,
                    () -> new MailCodes.Settings(Duration.ofMinutes(15), 5, Duration.ofSeconds(60), 6),
                    (address, host) -> Optional.empty(),
                    new SplittableRandom(5),
                    () -> Instant.parse("2026-10-18T12:00:00Z")));

    @AfterEach
    void close() {
        lane.close();
    }

    @Test
    @DisplayName("the login code goes to the linked address with the player's name, and not again while it is good")
    void theCodeGoesOnce() {
        emails.setAddress(steve.id(), TO);

        assertThat(login.send(steve, Locale.ENGLISH).join()).isInstanceOf(MailCodes.Issued.Sent.class);
        assertThat(login.send(steve, Locale.ENGLISH).join()).isEqualTo(new MailCodes.Issued.Live(TO));

        assertThat(kinds).containsExactly(Mailer.Kind.LOGIN_CODE);
        assertThat(mailed.getFirst()).containsEntry("player", "Steve").containsKey("code");
        assertThat(emails.code(steve.id(), Emails.Purpose.LOGIN)).isPresent();
    }

    @Test
    @DisplayName("an account with no linked address is mailed nothing")
    void noAddressNoMail() {
        assertThat(login.send(steve, Locale.ENGLISH).join()).isEqualTo(new MailCodes.Issued.Off());
        assertThat(kinds).isEmpty();
    }
}
