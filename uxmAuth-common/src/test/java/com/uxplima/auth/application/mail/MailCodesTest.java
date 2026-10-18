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

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.testing.InMemoryEmails;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A code by mail: one at a time per purpose, sent no faster than the cooldown, good once, for a while, for a few tries. */
final class MailCodesTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final EmailAddress TO =
            EmailAddress.parse("steve@example.net").orElseThrow();

    private Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private Mailer.Outcome answer = Mailer.Outcome.SENT;
    private final List<Map<String, String>> mailed = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();
    private @Nullable Duration ban;
    private final InMemoryEmails emails = new InMemoryEmails();
    private final HashingLane lane = new HashingLane(1, 1, 16);
    private final Mailer mailer = (kind, to, language, values) -> {
        mailed.add(values);
        return CompletableFuture.completedFuture(answer);
    };
    private final LogIn.Failures failures = (address, host) -> {
        failed.add(address + "@" + host);
        return Optional.ofNullable(ban);
    };
    private final MailCodes codes = new MailCodes(
            emails,
            mailer,
            new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4))),
            lane,
            () -> new MailCodes.Settings(Duration.ofMinutes(15), 3, Duration.ofSeconds(60), 6),
            failures,
            new SplittableRandom(7),
            () -> now);

    @AfterEach
    void close() {
        lane.close();
    }

    private MailCodes.Issued issue() {
        return codes.issue(
                        STEVE, Emails.Purpose.VERIFY, TO, Mailer.Kind.VERIFY, Locale.ENGLISH, Map.of("player", "Steve"))
                .join();
    }

    private String lastCode() {
        return Objects.requireNonNull(mailed.getLast().get("code"), "code");
    }

    private MailCodes.Checked check(String code) {
        return codes.check(STEVE, Emails.Purpose.VERIFY, code, "203.0.113.7", "play.example.net")
                .join();
    }

    @Test
    @DisplayName("a code is mailed with its digits and its minutes, and kept only as a hash")
    void aCodeIsMailed() {
        assertThat(issue()).isEqualTo(new MailCodes.Issued.Sent(TO, Duration.ofMinutes(15)));

        assertThat(lastCode()).matches("\\d{6}");
        assertThat(mailed.getLast()).containsEntry("minutes", "15").containsEntry("player", "Steve");
        assertThat(emails.code(STEVE, Emails.Purpose.VERIFY))
                .hasValueSatisfying(kept -> assertThat(kept.hash().encoded()).doesNotContain(lastCode()));
    }

    @Test
    @DisplayName("the right code passes once and is gone; it names the address it was sent to")
    void theRightCodePassesOnce() {
        issue();
        String code = lastCode();

        assertThat(check(code)).isEqualTo(new MailCodes.Checked.Right(TO));
        assertThat(check(code)).isEqualTo(new MailCodes.Checked.NothingPending());
    }

    @Test
    @DisplayName("a wrong code counts against the address and says the tries left; the last wrong one ends the code")
    void wrongCodesRunOut() {
        issue();
        String code = lastCode();

        assertThat(check("000000")).isEqualTo(new MailCodes.Checked.Wrong(2));
        assertThat(check("000000")).isEqualTo(new MailCodes.Checked.Wrong(1));
        assertThat(check("000000")).isEqualTo(new MailCodes.Checked.TooManyAttempts());
        assertThat(failed).hasSize(3).containsOnly("203.0.113.7@play.example.net");
        assertThat(check(code))
                .as("the right code after the last try")
                .isEqualTo(new MailCodes.Checked.NothingPending());
    }

    @Test
    @DisplayName("a wrong code that bans the address says for how long")
    void aBanIsSaid() {
        issue();
        ban = Duration.ofMinutes(10);

        assertThat(check("000000")).isEqualTo(new MailCodes.Checked.Banned(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("a code past its minutes is refused even when right, and goes")
    void anExpiredCodeIsRefused() {
        issue();
        String code = lastCode();
        now = now.plus(Duration.ofMinutes(15));

        assertThat(check(code)).isEqualTo(new MailCodes.Checked.Expired());
        assertThat(emails.code(STEVE, Emails.Purpose.VERIFY)).isEmpty();
    }

    @Test
    @DisplayName("a second code inside the cooldown sends nothing and says when; after it a new code replaces the old")
    void theCooldownHolds() {
        issue();
        String first = lastCode();
        now = now.plusSeconds(20);

        assertThat(issue()).isEqualTo(new MailCodes.Issued.Cooldown(Duration.ofSeconds(40)));
        assertThat(mailed).hasSize(1);

        now = now.plusSeconds(40);
        assertThat(issue()).isInstanceOf(MailCodes.Issued.Sent.class);
        assertThat(mailed).hasSize(2);
        assertThat(check(first)).as("the old code is void").isInstanceOf(MailCodes.Checked.Wrong.class);
        assertThat(check(lastCode())).isEqualTo(new MailCodes.Checked.Right(TO));
    }

    @Test
    @DisplayName("mail that is off or fails keeps no code, so the player may ask again at once")
    void noMailNoCode() {
        answer = Mailer.Outcome.OFF;
        assertThat(issue()).isEqualTo(new MailCodes.Issued.Off());
        assertThat(emails.code(STEVE, Emails.Purpose.VERIFY)).isEmpty();

        answer = Mailer.Outcome.FAILED;
        assertThat(issue()).isEqualTo(new MailCodes.Issued.Failed());
        assertThat(emails.code(STEVE, Emails.Purpose.VERIFY)).isEmpty();

        answer = Mailer.Outcome.SENT;
        assertThat(issue()).isInstanceOf(MailCodes.Issued.Sent.class);
    }

    @Test
    @DisplayName("a code of one purpose answers nothing of another, and spaces around a typed code are read past")
    void purposesAreApart() {
        issue();
        String code = lastCode();

        assertThat(codes.check(STEVE, Emails.Purpose.RECOVERY, code, "203.0.113.7", "h")
                        .join())
                .isEqualTo(new MailCodes.Checked.NothingPending());
        assertThat(check(" " + code + " ")).isEqualTo(new MailCodes.Checked.Right(TO));
    }

    @Test
    @DisplayName("a code a newer one replaced while it was checked is not taken, and the newer one stands")
    void aReplacedCodeIsNotTaken() {
        issue();
        String first = lastCode();
        Emails.Code newer = new Emails.Code(
                TO, new com.uxplima.auth.domain.hash.PasswordHash("$newer$"), now.plusSeconds(600), 3, now);
        Emails racing = new Emails() {
            private int changes;

            @Override
            public Optional<EmailAddress> address(UUID account) {
                return emails.address(account);
            }

            @Override
            public void setAddress(UUID account, EmailAddress address) {
                emails.setAddress(account, address);
            }

            @Override
            public void removeAddress(UUID account) {
                emails.removeAddress(account);
            }

            @Override
            public boolean mailsLoginCode(UUID account) {
                return emails.mailsLoginCode(account);
            }

            @Override
            public void setMailsLoginCode(UUID account, boolean on) {
                emails.setMailsLoginCode(account, on);
            }

            @Override
            public Optional<Emails.Code> code(UUID account, Emails.Purpose purpose) {
                return emails.code(account, purpose);
            }

            @Override
            public Optional<Emails.Code> changeCode(
                    UUID account,
                    Emails.Purpose purpose,
                    java.util.function.UnaryOperator<Optional<Emails.Code>> change) {
                if (++changes == 2) {
                    // Another server sends a fresh code between the check and the take.
                    emails.changeCode(account, purpose, before -> Optional.of(newer));
                }
                return emails.changeCode(account, purpose, change);
            }
        };
        MailCodes checking = new MailCodes(
                racing,
                mailer,
                new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4))),
                lane,
                () -> new MailCodes.Settings(Duration.ofMinutes(15), 3, Duration.ofSeconds(60), 6),
                failures,
                new SplittableRandom(7),
                () -> now);

        assertThat(checking.check(STEVE, Emails.Purpose.VERIFY, first, "203.0.113.7", "h")
                        .join())
                .isEqualTo(new MailCodes.Checked.NothingPending());
        assertThat(emails.code(STEVE, Emails.Purpose.VERIFY)).contains(newer);
    }

    @Test
    @DisplayName("a login code is mailed only while none is out: a code still good is not replaced by a new mail")
    void aLiveCodeIsNotReplaced() {
        MailCodes.Issued first = codes.issueUnlessLive(
                        STEVE, Emails.Purpose.LOGIN, TO, Mailer.Kind.LOGIN_CODE, Locale.ENGLISH, Map.of())
                .join();
        now = now.plusSeconds(120);

        assertThat(first).isInstanceOf(MailCodes.Issued.Sent.class);
        assertThat(codes.issueUnlessLive(
                                STEVE, Emails.Purpose.LOGIN, TO, Mailer.Kind.LOGIN_CODE, Locale.ENGLISH, Map.of())
                        .join())
                .isEqualTo(new MailCodes.Issued.Live(TO));
        assertThat(mailed).hasSize(1);
        now = now.plus(Duration.ofMinutes(15));
        assertThat(codes.issueUnlessLive(
                                STEVE, Emails.Purpose.LOGIN, TO, Mailer.Kind.LOGIN_CODE, Locale.ENGLISH, Map.of())
                        .join())
                .as("once it is past its time a new one goes")
                .isInstanceOf(MailCodes.Issued.Sent.class);
        assertThat(mailed).hasSize(2);
    }

    @Test
    @DisplayName("taking a code as a second factor spends it once and counts nothing against the address itself")
    void aCodeIsTakenAsAFactor() {
        codes.issueUnlessLive(STEVE, Emails.Purpose.LOGIN, TO, Mailer.Kind.LOGIN_CODE, Locale.ENGLISH, Map.of())
                .join();
        String code = lastCode();

        assertThat(codes.takes(STEVE, Emails.Purpose.LOGIN, "000000")).isFalse();
        assertThat(codes.takes(STEVE, Emails.Purpose.LOGIN, code)).isTrue();
        assertThat(codes.takes(STEVE, Emails.Purpose.LOGIN, code)).isFalse();
        assertThat(failed).isEmpty();
    }
}
