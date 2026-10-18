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
import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.mail.DomainRule;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.testing.InMemoryEmails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Linking an address: a code goes to it, and only the code saves it. */
final class LinkEmailTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final PlayerName NAME = new PlayerName("Steve");

    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final List<EmailAddress> sentTo = new ArrayList<>();
    private final List<Map<String, String>> mailed = new ArrayList<>();
    private final InMemoryEmails emails = new InMemoryEmails();
    private final HashingLane lane = new HashingLane(1, 1, 16);
    private DomainRule domains = new DomainRule(List.of(), List.of("mailinator.com"));
    private final MailCodes codes = new MailCodes(
            emails,
            (kind, to, language, values) -> {
                sentTo.add(to);
                mailed.add(values);
                return CompletableFuture.completedFuture(Mailer.Outcome.SENT);
            },
            new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4))),
            lane,
            () -> new MailCodes.Settings(Duration.ofMinutes(15), 5, Duration.ofSeconds(60), 6),
            (address, host) -> Optional.empty(),
            new SplittableRandom(3),
            () -> now);
    private final LinkEmail link = new LinkEmail(emails, codes, () -> domains, lane);

    @AfterEach
    void close() {
        lane.close();
    }

    private String code() {
        return Objects.requireNonNull(mailed.getFirst().get("code"), "code");
    }

    private LinkEmail.Outcome add(String typed) {
        return link.add(STEVE, NAME, typed, Locale.ENGLISH).join();
    }

    private LinkEmail.Outcome confirm(String code) {
        return link.confirm(STEVE, code, "203.0.113.7", "play.example.net").join();
    }

    @Test
    @DisplayName("an address gets a code, shown back masked, and is saved only when the code comes back")
    void anAddressIsSavedByItsCode() {
        assertThat(add("berke@example.com"))
                .isEqualTo(new LinkEmail.Outcome.Sent("b***@e***.com", Duration.ofMinutes(15)));
        assertThat(sentTo)
                .containsExactly(EmailAddress.parse("berke@example.com").orElseThrow());
        assertThat(mailed.getFirst()).containsEntry("player", "Steve");
        assertThat(link.linked(STEVE)).isEmpty();

        assertThat(confirm(code())).isEqualTo(new LinkEmail.Outcome.Confirmed("b***@e***.com"));
        assertThat(link.linked(STEVE))
                .contains(EmailAddress.parse("berke@example.com").orElseThrow());
    }

    @Test
    @DisplayName("not an address, or at a domain the operator refuses: no code is sent")
    void badAddressesSendNothing() {
        assertThat(add("not an address")).isEqualTo(new LinkEmail.Outcome.NotAnAddress());
        assertThat(add("steve@mailinator.com")).isEqualTo(new LinkEmail.Outcome.DomainRefused());
        domains = new DomainRule(List.of("example.org"), List.of());
        assertThat(add("steve@example.com")).isEqualTo(new LinkEmail.Outcome.DomainRefused());
        assertThat(sentTo).isEmpty();
    }

    @Test
    @DisplayName("a new address replaces the linked one only once its own code comes back")
    void aNewAddressWaitsForItsCode() {
        emails.setAddress(STEVE, EmailAddress.parse("old@example.com").orElseThrow());

        add("new@example.com");

        assertThat(link.linked(STEVE))
                .contains(EmailAddress.parse("old@example.com").orElseThrow());
        confirm(code());
        assertThat(link.linked(STEVE))
                .contains(EmailAddress.parse("new@example.com").orElseThrow());
    }

    @Test
    @DisplayName("the address linked already is said so, and no code is sent")
    void theSameAddressIsSaid() {
        emails.setAddress(STEVE, EmailAddress.parse("berke@example.com").orElseThrow());

        assertThat(add("berke@EXAMPLE.com")).isEqualTo(new LinkEmail.Outcome.AlreadyLinked("b***@e***.com"));
        assertThat(sentTo).isEmpty();
    }

    @Test
    @DisplayName("a wrong code says the tries left and saves nothing; a confirm with nothing sent says so")
    void aWrongCodeSavesNothing() {
        assertThat(confirm("123456")).isEqualTo(new LinkEmail.Outcome.NothingPending());
        add("berke@example.com");

        assertThat(confirm("000000")).isEqualTo(new LinkEmail.Outcome.Wrong(4));
        assertThat(link.linked(STEVE)).isEmpty();
    }

    @Test
    @DisplayName("remove takes the linked address away and says which; with none it says so")
    void removeTakesItAway() {
        assertThat(link.remove(STEVE).join()).isEqualTo(new LinkEmail.Outcome.NoneLinked());
        emails.setAddress(STEVE, EmailAddress.parse("berke@example.com").orElseThrow());

        assertThat(link.remove(STEVE).join()).isEqualTo(new LinkEmail.Outcome.Removed("b***@e***.com"));
        assertThat(link.linked(STEVE)).isEmpty();
    }

    @Test
    @DisplayName("while the login code goes by mail, the address can be neither replaced nor removed")
    void theFactorAddressIsKept() {
        EmailAddress old = EmailAddress.parse("old@example.com").orElseThrow();
        emails.setAddress(STEVE, old);
        emails.setMailsLoginCode(STEVE, true);

        assertThat(add("new@example.com")).isEqualTo(new LinkEmail.Outcome.FactorOn());
        assertThat(link.remove(STEVE).join()).isEqualTo(new LinkEmail.Outcome.FactorOn());
        assertThat(sentTo).isEmpty();
        assertThat(link.linked(STEVE)).contains(old);
        assertThat(emails.mailsLoginCode(STEVE)).isTrue();
    }
}
