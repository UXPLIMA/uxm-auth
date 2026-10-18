package com.uxplima.auth.application.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Turning the app on and off: nothing is kept until a code from the app proves it was set up. */
final class SetUpTotpTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final PlayerName NAME = new PlayerName("Steve");
    private static final Totp TOTP = new Totp(6, Duration.ofSeconds(30), 1);

    private Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final MemoryFactors factors = new MemoryFactors();
    private final List<String> failedFrom = new ArrayList<>();
    private final LogIn.Failures failures = (address, host) -> {
        failedFrom.add(address + "@" + host);
        return Optional.empty();
    };
    private SetUpTotp.Settings settings =
            new SetUpTotp.Settings("Uxplima Network", "{player}", TOTP, Duration.ofMinutes(10));
    private final SetUpTotp setUp =
            new SetUpTotp(factors, () -> settings, failures, new SplittableRandom(3), () -> now);

    private String codeAt(TotpSecret secret, Instant at) {
        return TOTP.code(secret.bytes(), TOTP.step(at));
    }

    @Test
    @DisplayName("setup shows a fresh secret and the link an app reads, and keeps nothing yet")
    void setupShowsTheSecretAndTheLink() {
        SetUpTotp.Outcome.Started started = (SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME);

        assertThat(started.secret().bytes()).hasSize(Totp.SECRET_BYTES);
        assertThat(started.link())
                .isEqualTo("otpauth://totp/Uxplima%20Network:Steve?secret="
                        + started.secret().base32() + "&issuer=Uxplima%20Network&algorithm=SHA1&digits=6&period=30");
        assertThat(factors.app(STEVE)).isEmpty();
        assertThat(setUp.active(STEVE)).isFalse();
        SetUpTotp.Outcome.Started again = (SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME);
        assertThat(again.secret()).isNotEqualTo(started.secret());
    }

    @Test
    @DisplayName("the label is the operator's, with the player's name where it says {player}, and both are encoded")
    void theLabelIsTheOperators() {
        settings = new SetUpTotp.Settings("A&B", "mc/{player}?", TOTP, Duration.ofMinutes(10));

        String link = ((SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME)).link();

        assertThat(link)
                .startsWith("otpauth://totp/A%26B:mc%2FSteve%3F?secret=")
                .contains("&issuer=A%26B&");
    }

    @Test
    @DisplayName("a code from the new secret turns the app on, and that code is spent")
    void aRightCodeTurnsItOn() {
        SetUpTotp.Outcome.Started started = (SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME);
        String code = codeAt(started.secret(), now);

        assertThat(setUp.confirm(STEVE, code)).isInstanceOf(SetUpTotp.Outcome.On.class);

        assertThat(setUp.active(STEVE)).isTrue();
        assertThat(factors.app(STEVE)).hasValueSatisfying(app -> {
            assertThat(app.secret()).isEqualTo(started.secret());
            assertThat(app.lastStep()).isEqualTo(TOTP.step(now));
        });
        assertThat(setUp.confirm(STEVE, code)).isInstanceOf(SetUpTotp.Outcome.NothingStarted.class);
    }

    @Test
    @DisplayName("a wrong code keeps the setup open, and an earlier setup's code does not confirm a later one")
    void aWrongCodeKeepsTheSetupOpen() {
        SetUpTotp.Outcome.Started first = (SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME);
        SetUpTotp.Outcome.Started second = (SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME);

        assertThat(setUp.confirm(STEVE, codeAt(first.secret(), now))).isInstanceOf(SetUpTotp.Outcome.Wrong.class);
        assertThat(factors.app(STEVE)).isEmpty();
        assertThat(setUp.confirm(STEVE, codeAt(second.secret(), now))).isInstanceOf(SetUpTotp.Outcome.On.class);
        assertThat(failedFrom).isEmpty();
    }

    @Test
    @DisplayName("a setup left open past its time, or by a player who left, confirms nothing")
    void anOldSetupConfirmsNothing() {
        SetUpTotp.Outcome.Started started = (SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME);
        now = now.plus(Duration.ofMinutes(10)).plusSeconds(1);

        assertThat(setUp.confirm(STEVE, codeAt(started.secret(), now)))
                .isInstanceOf(SetUpTotp.Outcome.NothingStarted.class);

        SetUpTotp.Outcome.Started next = (SetUpTotp.Outcome.Started) setUp.start(STEVE, NAME);
        setUp.forget(STEVE);
        assertThat(setUp.confirm(STEVE, codeAt(next.secret(), now)))
                .isInstanceOf(SetUpTotp.Outcome.NothingStarted.class);
    }

    @Test
    @DisplayName("an account with the app on starts no second setup until it is turned off")
    void anActiveAppStartsNoSetup() {
        factors.setApp(STEVE, new TotpSecret(new byte[20]));

        assertThat(setUp.start(STEVE, NAME)).isInstanceOf(SetUpTotp.Outcome.AlreadyOn.class);
    }

    @Test
    @DisplayName(
            "turning it off takes a code from the app, removes the secret and the backup codes, and a wrong code counts")
    void turningItOffTakesACode() {
        TotpSecret secret = new TotpSecret(new byte[20]);
        factors.setApp(STEVE, secret);
        factors.setBackupCodes(STEVE, List.of(new PasswordHash("$hash$one")));

        assertThat(setUp.off(STEVE, "000000", "203.0.113.7", "play.example.net"))
                .isInstanceOf(SetUpTotp.Outcome.Wrong.class);
        assertThat(failedFrom).containsExactly("203.0.113.7@play.example.net");
        assertThat(setUp.active(STEVE)).isTrue();

        assertThat(setUp.off(STEVE, codeAt(secret, now), "203.0.113.7", "play.example.net"))
                .isInstanceOf(SetUpTotp.Outcome.Off.class);
        assertThat(factors.app(STEVE)).isEmpty();
        assertThat(factors.backupCodes(STEVE)).isEmpty();
        assertThat(setUp.off(STEVE, codeAt(secret, now), "203.0.113.7", "play.example.net"))
                .isInstanceOf(SetUpTotp.Outcome.NotOn.class);
    }

    @Test
    @DisplayName("a code already spent does not turn the app off")
    void aSpentCodeDoesNotTurnItOff() {
        TotpSecret secret = new TotpSecret(new byte[20]);
        factors.setApp(STEVE, secret);
        factors.useStep(STEVE, TOTP.step(now));

        assertThat(setUp.off(STEVE, codeAt(secret, now), "203.0.113.7", "play.example.net"))
                .isInstanceOf(SetUpTotp.Outcome.Wrong.class);
        assertThat(setUp.active(STEVE)).isTrue();

        factors.readsStale = true;
        assertThat(setUp.off(STEVE, codeAt(secret, now), "203.0.113.7", "play.example.net"))
                .as("a code another server took between the read and the take")
                .isInstanceOf(SetUpTotp.Outcome.Wrong.class);
        assertThat(setUp.active(STEVE)).isTrue();
    }

    @Test
    @DisplayName("a ban earned by a wrong code is said, and an issuer or a label left empty is refused")
    void aBanIsSaid() {
        factors.setApp(STEVE, new TotpSecret(new byte[20]));
        SetUpTotp banning = new SetUpTotp(
                factors,
                () -> settings,
                (address, host) -> Optional.of(Duration.ofMinutes(5)),
                new SplittableRandom(3),
                () -> now);

        assertThat(banning.off(STEVE, "000000", "203.0.113.7", "play.example.net"))
                .isEqualTo(new SetUpTotp.Outcome.Banned(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> new SetUpTotp.Settings(" ", "{player}", TOTP, Duration.ofMinutes(10)))
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> new SetUpTotp.Settings("Uxplima", "", TOTP, Duration.ofMinutes(10)))
                .hasMessageContaining("label");
        assertThatThrownBy(() -> new SetUpTotp.Settings("Uxplima", "{player}", TOTP, Duration.ZERO))
                .hasMessageContaining("setup-minutes");
    }
}
