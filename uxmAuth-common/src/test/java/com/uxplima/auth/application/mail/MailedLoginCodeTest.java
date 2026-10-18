package com.uxplima.auth.application.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.testing.InMemoryEmails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The mailed login code answers the second step only while the account takes its code by mail. */
final class MailedLoginCodeTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final EmailAddress TO =
            EmailAddress.parse("steve@example.net").orElseThrow();

    private final InMemoryEmails emails = new InMemoryEmails();
    private final HashingLane lane = new HashingLane(1, 1, 16);
    private final List<Map<String, String>> mailed = new ArrayList<>();
    private final MailCodes codes = new MailCodes(
            emails,
            (kind, to, language, values) -> {
                mailed.add(values);
                return CompletableFuture.completedFuture(Mailer.Outcome.SENT);
            },
            new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4))),
            lane,
            () -> new MailCodes.Settings(Duration.ofMinutes(15), 5, Duration.ofSeconds(60), 6),
            (address, host) -> Optional.empty(),
            new SplittableRandom(9),
            () -> Instant.parse("2026-10-18T12:00:00Z"));
    private final MailedLoginCode login = new MailedLoginCode(emails, codes);

    @AfterEach
    void close() {
        lane.close();
    }

    private String sent() {
        codes.issueUnlessLive(STEVE, Emails.Purpose.LOGIN, TO, Mailer.Kind.LOGIN_CODE, Locale.ENGLISH, Map.of())
                .join();
        return Objects.requireNonNull(mailed.getLast().get("code"), "code");
    }

    @Test
    @DisplayName("while it is on the mailed code passes once")
    void onItPasses() {
        emails.setAddress(STEVE, TO);
        emails.setMailsLoginCode(STEVE, true);
        String code = sent();

        assertThat(login.takes(STEVE, code)).isTrue();
        assertThat(login.takes(STEVE, code)).isFalse();
    }

    @Test
    @DisplayName("a code left out after the mail was turned off passes nothing, and is not even spent")
    void offItPassesNothing() {
        emails.setAddress(STEVE, TO);
        String code = sent();

        assertThat(login.takes(STEVE, code)).isFalse();
        assertThat(emails.code(STEVE, Emails.Purpose.LOGIN))
                .hasValueSatisfying(left -> assertThat(left.attemptsLeft()).isEqualTo(5));
    }
}
