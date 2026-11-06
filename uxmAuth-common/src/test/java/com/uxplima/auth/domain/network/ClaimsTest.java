package com.uxplima.auth.domain.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.Test;

class ClaimsTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final NetworkSecret SECRET = new NetworkSecret("an-example-network-secret-of-enough-length");
    private static final UUID ID = UUID.fromString("6f8e2b1a-3c4d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final String NAME = "Steve";

    private final Claims proxy = new Claims(SECRET, Duration.ofSeconds(30));
    private final Claims lobby = new Claims(SECRET, Duration.ofSeconds(30), new Nonces());

    private static Claim claim(String server, String nonce) {
        return new Claim(ID, NAME, server, AuthMethod.PASSWORD, NOW, nonce);
    }

    private Claims.Reading readAtLobby(String sealed) {
        return lobby.read(sealed, Claim.Direction.TO_SERVER, ID, NAME, "lobby", NOW.plusSeconds(1));
    }

    @Test
    void aClaimForThisServerIsTaken() {
        String sealed = proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER);

        assertThat(readAtLobby(sealed)).isInstanceOfSatisfying(Claims.Reading.Valid.class, valid -> {
            assertThat(valid.claim().standing()).isEqualTo(new Claim.LoggedIn(AuthMethod.PASSWORD));
            assertThat(valid.claim().nonce()).isEqualTo("one");
        });
    }

    @Test
    void theServerNameIsReadWithoutItsCase() {
        String sealed = proxy.sign(claim("Lobby", "one"), Claim.Direction.TO_SERVER);

        assertThat(lobby.read(sealed, Claim.Direction.TO_SERVER, ID, NAME, " LOBBY ", NOW))
                .isInstanceOf(Claims.Reading.Valid.class);
    }

    @Test
    void aClaimWrittenForAnotherServerIsRefused() {
        String sealed = proxy.sign(claim("survival", "one"), Claim.Direction.TO_SERVER);

        assertThat(readAtLobby(sealed)).isEqualTo(new Claims.Reading.Refused(ClaimRefusal.WRONG_SERVER));
    }

    @Test
    void aCopyOfATakenClaimIsRefused() {
        String sealed = proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER);

        assertThat(readAtLobby(sealed)).isInstanceOf(Claims.Reading.Valid.class);
        assertThat(readAtLobby(sealed)).isEqualTo(new Claims.Reading.Refused(ClaimRefusal.REPLAYED));
    }

    @Test
    void theNoncesOutliveTheReaderThatTookThem() {
        Nonces seen = new Nonces();
        String sealed = proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER);

        new Claims(SECRET, Duration.ofSeconds(30), seen)
                .read(sealed, Claim.Direction.TO_SERVER, ID, NAME, "lobby", NOW);

        assertThat(new Claims(SECRET, Duration.ofSeconds(30), seen)
                        .read(sealed, Claim.Direction.TO_SERVER, ID, NAME, "lobby", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.REPLAYED));
    }

    @Test
    void aFullNonceMemoryRefusesRatherThanForgets() {
        Claims small = new Claims(SECRET, Duration.ofSeconds(30), new Nonces(1));

        assertThat(small.read(
                        proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER),
                        Claim.Direction.TO_SERVER,
                        ID,
                        NAME,
                        "lobby",
                        NOW))
                .isInstanceOf(Claims.Reading.Valid.class);
        assertThat(small.read(
                        proxy.sign(claim("lobby", "two"), Claim.Direction.TO_SERVER),
                        Claim.Direction.TO_SERVER,
                        ID,
                        NAME,
                        "lobby",
                        NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.REPLAYED));
    }

    @Test
    void aNonceIsForgottenOnceItsClaimIsStale() {
        Claims small = new Claims(SECRET, Duration.ofSeconds(30), new Nonces(1));
        small.read(
                proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER),
                Claim.Direction.TO_SERVER,
                ID,
                NAME,
                "lobby",
                NOW);
        Instant later = NOW.plusSeconds(60);
        Claim fresh = new Claim(ID, NAME, "lobby", AuthMethod.PASSWORD, later, "two");

        assertThat(small.read(
                        proxy.sign(fresh, Claim.Direction.TO_SERVER),
                        Claim.Direction.TO_SERVER,
                        ID,
                        NAME,
                        "lobby",
                        later))
                .isInstanceOf(Claims.Reading.Valid.class);
    }

    @Test
    void aReportDoesNotReadAsAClaim() {
        String report = lobby.sign(claim("lobby", "one"), Claim.Direction.TO_PROXY);

        assertThat(readAtLobby(report)).isEqualTo(new Claims.Reading.Refused(ClaimRefusal.BAD_SIGNATURE));
    }

    @Test
    void aClaimDoesNotReadAsAReport() {
        String sealed = proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER);

        assertThat(proxy.read(sealed, Claim.Direction.TO_PROXY, ID, NAME, "lobby", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.BAD_SIGNATURE));
    }

    @Test
    void aReportIsTakenOnlyFromTheServerItNames() {
        String report = lobby.sign(claim("lobby", "one"), Claim.Direction.TO_PROXY);

        assertThat(proxy.read(report, Claim.Direction.TO_PROXY, ID, NAME, "survival", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.WRONG_SERVER));
        assertThat(proxy.read(report, Claim.Direction.TO_PROXY, ID, NAME, "lobby", NOW))
                .isInstanceOf(Claims.Reading.Valid.class);
    }

    @Test
    void anotherSecretIsABadSignature() {
        Claims other =
                new Claims(new NetworkSecret("another-example-network-secret-of-length"), Duration.ofSeconds(30));
        String sealed = other.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER);

        assertThat(readAtLobby(sealed)).isEqualTo(new Claims.Reading.Refused(ClaimRefusal.BAD_SIGNATURE));
    }

    @Test
    void aClaimSignedWithTheBareSecretIsRefusedAsOutdated() throws Exception {
        String text = "v1|" + ID + "|" + NAME + "|L:PASSWORD|" + NOW.toEpochMilli() + "|one";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.encoded().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String sealed = encoder.encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));

        assertThat(readAtLobby(sealed)).isEqualTo(new Claims.Reading.Refused(ClaimRefusal.OUTDATED));
    }

    @Test
    void aStaleClaimIsRefused() {
        String sealed = proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER);

        assertThat(lobby.read(sealed, Claim.Direction.TO_SERVER, ID, NAME, "lobby", NOW.plusSeconds(31)))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.STALE));
    }

    @Test
    void aClaimAboutAnotherPlayerIsRefused() {
        String sealed = proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER);

        assertThat(lobby.read(sealed, Claim.Direction.TO_SERVER, ID, "Alex", "lobby", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.NOT_THIS_PLAYER));
    }

    @Test
    void aBulletinIsSealedWithAKeyNoClaimIsSignedWith() {
        Bulletins bulletins = new Bulletins(proxy);
        String sealed = bulletins.seal(new Bulletins.Sent(new Bulletin.LetIn(ID, "nonce"), UUID.randomUUID(), NOW));

        assertThat(bulletins.open(sealed, NOW)).map(Bulletins.Sent::bulletin).contains(new Bulletin.LetIn(ID, "nonce"));
        assertThat(proxy.read(sealed, Claim.Direction.TO_SERVER, ID, NAME, "lobby", NOW))
                .isEqualTo(new Claims.Reading.Refused(ClaimRefusal.BAD_SIGNATURE));
        assertThat(bulletins.open(proxy.sign(claim("lobby", "one"), Claim.Direction.TO_SERVER), NOW))
                .isEqualTo(Optional.empty());
    }
}
