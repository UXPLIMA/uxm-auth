package com.uxplima.auth.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What every member of a network hears at once, taken only signed and fresh. */
final class BulletinsTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ORIGIN = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final Duration FRESH = Duration.ofSeconds(30);

    private final Claims claims = new Claims(NetworkSecret.parse("q".repeat(43)), FRESH);
    private final Bulletins bulletins = new Bulletins(claims);
    private final Bulletins.Sent ended =
            new Bulletins.Sent(new Bulletin.SessionsEnded(STEVE, Optional.of("4f1c-visit")), ORIGIN, NOW);

    @Test
    @DisplayName("a bulletin a member sealed opens whole on another")
    void aSealedBulletinOpens() {
        assertThat(bulletins.open(bulletins.seal(ended), NOW.plusSeconds(3))).contains(ended);
        Bulletins.Sent dropping = new Bulletins.Sent(new Bulletin.SessionsEnded(STEVE, Optional.empty()), ORIGIN, NOW);
        assertThat(bulletins.open(bulletins.seal(dropping), NOW)).contains(dropping);
    }

    @Test
    @DisplayName("staff letting an account in travels whole too, and is never read as an ended session")
    void aLetInOpens() {
        Bulletins.Sent letIn = new Bulletins.Sent(new Bulletin.LetIn(STEVE, "n0nce-1"), ORIGIN, NOW);

        assertThat(bulletins.open(bulletins.seal(letIn), NOW)).contains(letIn);
        assertThat(bulletins.open(claims.seal("b1|L|" + ORIGIN + "|" + NOW.toEpochMilli() + "|" + STEVE + "|"), NOW))
                .as("a let-in carries the nonce that makes it one of a kind")
                .isEmpty();
    }

    @Test
    @DisplayName("an account staff removed travels whole too, and carries nothing after the account")
    void anUnregisteredOpens() {
        Bulletins.Sent removed = new Bulletins.Sent(new Bulletin.Unregistered(STEVE), ORIGIN, NOW);

        assertThat(bulletins.open(bulletins.seal(removed), NOW)).contains(removed);
        assertThat(bulletins.open(claims.seal("b1|U|" + ORIGIN + "|" + NOW.toEpochMilli() + "|" + STEVE + "|x"), NOW))
                .isEmpty();
    }

    @Test
    @DisplayName("who removed or switched the account travels with it, so every member sends its player away in"
            + " the right words")
    void whoActedTravels() {
        for (Bulletin bulletin : java.util.List.of(
                new Bulletin.Unregistered(STEVE, Bulletin.By.OWNER),
                new Bulletin.Rejoin(STEVE, Bulletin.By.OWNER),
                new Bulletin.Unregistered(STEVE, Bulletin.By.STAFF))) {
            Bulletins.Sent sent = new Bulletins.Sent(bulletin, ORIGIN, NOW);
            assertThat(bulletins.open(bulletins.seal(sent), NOW)).contains(sent);
        }
        assertThat(new Bulletin.Rejoin(STEVE)).isEqualTo(new Bulletin.Rejoin(STEVE, Bulletin.By.STAFF));
    }

    @Test
    @DisplayName("an account whose player must join again travels whole too")
    void aRejoinOpens() {
        Bulletins.Sent rejoin = new Bulletins.Sent(new Bulletin.Rejoin(STEVE), ORIGIN, NOW);

        assertThat(bulletins.open(bulletins.seal(rejoin), NOW)).contains(rejoin);
        assertThat(bulletins.open(claims.seal("b1|R|" + ORIGIN + "|" + NOW.toEpochMilli() + "|" + STEVE + "|x"), NOW))
                .isEmpty();
    }

    @Test
    @DisplayName("a let-in opens once: the same sealed text again inside the fresh time lets nobody in twice")
    void aLetInIsNotReplayed() {
        Bulletins.Sent letIn = new Bulletins.Sent(new Bulletin.LetIn(STEVE, "n0nce-2"), ORIGIN, NOW);
        String sealed = bulletins.seal(letIn);

        assertThat(bulletins.open(sealed, NOW)).contains(letIn);
        assertThat(bulletins.open(sealed, NOW.plusSeconds(1))).isEmpty();
        Bulletins.Sent another = new Bulletins.Sent(new Bulletin.LetIn(STEVE, "n0nce-3"), ORIGIN, NOW);
        assertThat(bulletins.open(bulletins.seal(another), NOW.plusSeconds(1))).contains(another);
        assertThat(bulletins.open(bulletins.seal(ended), NOW)).contains(ended);
        assertThat(bulletins.open(bulletins.seal(ended), NOW))
                .as("an ended session lets nobody in")
                .contains(ended);
    }

    @Test
    @DisplayName("a bulletin signed with another secret, changed, cut or made up is not opened")
    void aForgedBulletinIsNotOpened() {
        Bulletins other = new Bulletins(new Claims(NetworkSecret.parse("r".repeat(43)), FRESH));
        String sealed = bulletins.seal(ended);
        int dot = sealed.indexOf('.');

        assertThat(bulletins.open(other.seal(ended), NOW)).isEmpty();
        assertThat(bulletins.open("x" + sealed.substring(1), NOW)).isEmpty();
        assertThat(bulletins.open(sealed.substring(0, dot), NOW)).isEmpty();
        assertThat(bulletins.open(sealed.substring(0, dot + 1), NOW)).isEmpty();
        assertThat(bulletins.open("not a bulletin", NOW)).isEmpty();
        assertThat(bulletins.open(claims.seal("b1|S|nonsense"), NOW)).isEmpty();
        String body = "|S|" + ORIGIN + "|" + NOW.toEpochMilli() + "|" + STEVE + "|";
        assertThat(bulletins.open(claims.seal("b1" + body + "v1"), NOW)).isPresent();
        assertThat(bulletins.open(claims.seal("b1" + body), NOW)).isPresent();
        assertThat(bulletins.open(claims.seal("v1" + body + "v1"), NOW)).isEmpty();
        assertThat(bulletins.open(claims.seal("b1" + body + "a visit"), NOW)).isEmpty();
    }

    @Test
    @DisplayName("a bulletin older than the fresh time, or from the future past a little drift, is not opened")
    void aStaleBulletinIsNotOpened() {
        assertThat(bulletins.open(bulletins.seal(ended), NOW.plus(FRESH).plusSeconds(1)))
                .isEmpty();
        assertThat(bulletins.open(bulletins.seal(ended), NOW.minusSeconds(6))).isEmpty();
    }

    @Test
    @DisplayName("a claim is never read as a bulletin, and a bulletin never as a claim")
    void claimsAndBulletinsAreApart() {
        String claim = claims.sign(new Claim(STEVE, "Steve", AuthMethod.PASSWORD, NOW, "n0nce"));

        assertThat(bulletins.open(claim, NOW)).isEmpty();
        assertThat(claims.read(bulletins.seal(ended), STEVE, "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.MALFORMED));
    }

    @Test
    @DisplayName("the session kept is named by a visit token, and nothing else passes for one")
    void theKeptSessionIsAVisitToken() {
        assertThatThrownBy(() -> new Bulletin.SessionsEnded(STEVE, Optional.of("a|b")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bulletin.SessionsEnded(STEVE, Optional.of("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("every alert travels whole, a name or a target with the separators in it too")
    void alertsOpen() {
        for (Bulletin alert : java.util.List.<Bulletin>of(
                new Bulletin.FailedLogin("203.0.113.7"),
                new Bulletin.Banned("2001:db8::1", Duration.ofMinutes(10)),
                new Bulletin.SharedAddress(new PlayerName("Ste|ve;2"), "203.0.113.7", 4),
                new Bulletin.StaffActed(Optional.of(new PlayerName("Mod")), "unregister", "Al ex|;"),
                new Bulletin.StaffActed(Optional.empty(), "unban", "203.0.113.7"))) {
            Bulletins.Sent sent = new Bulletins.Sent(alert, ORIGIN, NOW);

            assertThat(bulletins.open(bulletins.seal(sent), NOW)).contains(sent);
        }
    }

    @Test
    @DisplayName("an alert that names no known kind, or misses a part, is not opened")
    void aBrokenAlertIsNotOpened() {
        String head = "b1|A|" + ORIGIN + "|" + NOW.toEpochMilli() + "|" + new UUID(0, 0) + "|";

        assertThat(bulletins.open(claims.seal(head + "F;203.0.113.7"), NOW)).isPresent();
        assertThat(bulletins.open(claims.seal(head + "X;203.0.113.7"), NOW)).isEmpty();
        assertThat(bulletins.open(claims.seal(head + "B;203.0.113.7"), NOW)).isEmpty();
        assertThat(bulletins.open(claims.seal(head + "B;203.0.113.7;soon"), NOW))
                .isEmpty();
        assertThat(bulletins.open(claims.seal(head + "S;Steve;203.0.113.7"), NOW))
                .isEmpty();
        assertThat(bulletins.open(claims.seal(head + "A;;unban"), NOW)).isEmpty();
        assertThat(bulletins.open(claims.seal(head + "F;203.0.113.7;more"), NOW))
                .isEmpty();
        assertThat(bulletins.open(claims.seal(head + "F;"), NOW)).isEmpty();
    }

    @Test
    @DisplayName("an alert names an address and a count that mean something")
    void anAlertIsWhole() {
        assertThatThrownBy(() -> new Bulletin.FailedLogin(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bulletin.Banned("203.0.113.7", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bulletin.SharedAddress(new PlayerName("Steve"), "203.0.113.7", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bulletin.StaffActed(Optional.empty(), "Un Ban", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
