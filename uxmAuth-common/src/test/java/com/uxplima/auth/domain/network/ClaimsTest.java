package com.uxplima.auth.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The proxy's word that a player logged in, which a backend takes only signed, fresh and about that player. */
final class ClaimsTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final Instant NOW = Instant.parse("2026-10-17T20:00:00Z");
    private static final Duration FRESH = Duration.ofSeconds(30);

    private final NetworkSecret secret = NetworkSecret.parse("q".repeat(43));
    private final Claims claims = new Claims(secret, FRESH);

    private String signedForSteve(Instant at) {
        return claims.sign(new Claim(STEVE, "Steve", AuthMethod.PASSWORD, at, "n0nce"));
    }

    @Test
    @DisplayName("a claim the proxy signed reads back whole on the backend")
    void aSignedClaimReadsBack() {
        Claims.Reading reading = claims.read(signedForSteve(NOW), STEVE, "Steve", NOW.plusSeconds(5));

        assertThat(reading)
                .isEqualTo(new Claims.Reading.Valid(new Claim(STEVE, "Steve", AuthMethod.PASSWORD, NOW, "n0nce")));
    }

    @Test
    @DisplayName("a claim older than the fresh time, or from the future past a little drift, is stale")
    void anOldClaimIsStale() {
        assertThat(claims.read(
                        signedForSteve(NOW), STEVE, "Steve", NOW.plus(FRESH).plusSeconds(1)))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.STALE));
        assertThat(claims.read(signedForSteve(NOW.plus(FRESH)), STEVE, "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.STALE));
    }

    @Test
    @DisplayName("a claim about another player, by id or by name, is not this player's")
    void anotherPlayersClaimIsRefused() {
        assertThat(claims.read(signedForSteve(NOW), UUID.randomUUID(), "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.NOT_THIS_PLAYER));
        assertThat(claims.read(signedForSteve(NOW), STEVE, "Alex", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.NOT_THIS_PLAYER));
    }

    @Test
    @DisplayName("a claim signed with another secret, or with its text changed, has a bad signature")
    void aForgedClaimIsRefused() {
        String foreign = new Claims(NetworkSecret.parse("z".repeat(43)), FRESH)
                .sign(new Claim(STEVE, "Steve", AuthMethod.PASSWORD, NOW, "n0nce"));
        assertThat(claims.read(foreign, STEVE, "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.BAD_SIGNATURE));

        String signed = signedForSteve(NOW);
        String text = new String(
                Base64.getUrlDecoder().decode(signed.substring(0, signed.indexOf('.'))), StandardCharsets.UTF_8);
        String changed = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(text.replace("PASSWORD", "PREMIUM").getBytes(StandardCharsets.UTF_8))
                + signed.substring(signed.indexOf('.'));
        assertThat(claims.read(changed, STEVE, "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.BAD_SIGNATURE));
    }

    @Test
    @DisplayName("a claim cut short, or not a claim at all, is malformed")
    void aBrokenClaimIsMalformed() {
        String signed = signedForSteve(NOW);
        assertThat(claims.read(signed.substring(0, signed.indexOf('.')), STEVE, "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.MALFORMED));
        assertThat(claims.read("not a claim", STEVE, "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.MALFORMED));
        String garbage = claims.seal("v1|nonsense");
        assertThat(claims.read(garbage, STEVE, "Steve", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.MALFORMED));
    }

    @Test
    @DisplayName(
            "a player still waiting for a password is vouched for with what the door decided, signed as a login is")
    void aWaitingPlayerReadsBack() {
        Claim waiting = new Claim(STEVE, "Steve+", new Claim.Waiting(true, false, true), NOW, "n0nce");

        assertThat(claims.read(claims.sign(waiting), STEVE, "Steve+", NOW))
                .isEqualTo(new Claims.Reading.Valid(waiting));
        assertThat(new Claim(STEVE, "Steve", AuthMethod.PREMIUM, NOW, "n0nce").standing())
                .isEqualTo(new Claim.LoggedIn(AuthMethod.PREMIUM));
    }

    @Test
    @DisplayName("a player whose first step passed and who owes the app's code reads back with the way they came")
    void aPlayerOwingTheCodeReadsBack() {
        Claim owing = new Claim(STEVE, "Steve", new Claim.Code(AuthMethod.SESSION), NOW, "n0nce");

        assertThat(claims.read(claims.sign(owing), STEVE, "Steve", NOW)).isEqualTo(new Claims.Reading.Valid(owing));
        assertThat(Claim.parse("v1|" + STEVE + "|Steve|C:NOPE|" + NOW.toEpochMilli() + "|n0nce"))
                .isEmpty();
        assertThat(Claim.parse("v1|" + STEVE + "|Steve|C:PREMIUM|" + NOW.toEpochMilli() + "|n0nce"))
                .hasValueSatisfying(
                        claim -> assertThat(claim.standing()).isEqualTo(new Claim.Code(AuthMethod.PREMIUM)));
    }

    @Test
    @DisplayName("a secret is at least 32 characters, and a fresh one is 43 that read back the same")
    void aSecretIsLongEnough() {
        assertThatThrownBy(() -> NetworkSecret.parse("short")).isInstanceOf(IllegalArgumentException.class);
        NetworkSecret fresh = NetworkSecret.generate();

        assertThat(fresh.encoded()).hasSize(43);
        assertThat(NetworkSecret.parse(fresh.encoded())).isEqualTo(fresh);
        assertThat(NetworkSecret.generate()).isNotEqualTo(fresh);
        assertThat(fresh.toString()).doesNotContain(fresh.encoded());
    }
}
