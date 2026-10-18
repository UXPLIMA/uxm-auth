package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.SplittableRandom;
import java.util.UUID;

import com.uxplima.auth.domain.name.PlayerName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A captcha before registration, after failures, or both: a fresh code each showing, gone with the connection. */
final class CaptchasTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final PlayerName NAME = new PlayerName("Steve");
    private static final String CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private Captchas.Settings settings = settings(Captchas.When.BOTH);
    private Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final Captchas captchas = new Captchas(() -> settings, new SplittableRandom(7), () -> now);

    private static Captchas.Settings settings(Captchas.When when) {
        return new Captchas.Settings(when, 3, 5, CHARACTERS, Duration.ofMinutes(60), Captchas.JavaScreen.DIALOG);
    }

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            captchas.failed(NAME);
        }
    }

    @Test
    @DisplayName("off, nothing is ever asked")
    void offAsksNothing() {
        settings = settings(Captchas.When.OFF);
        fail(10);

        assertThat(captchas.blocksRegistration(STEVE)).isFalse();
        assertThat(captchas.blocks(STEVE, NAME)).isFalse();
    }

    @Test
    @DisplayName("at registration, a registration waits for the code of this showing and a login does not")
    void registrationWaitsForTheCode() {
        settings = settings(Captchas.When.REGISTER);
        fail(5);

        assertThat(captchas.blocks(STEVE, NAME)).isFalse();
        assertThat(captchas.blocksRegistration(STEVE)).isTrue();
        String code = captchas.show(STEVE);

        assertThat(captchas.solve(STEVE, code.toLowerCase(java.util.Locale.ROOT) + " "))
                .isTrue();
        assertThat(captchas.blocksRegistration(STEVE)).isFalse();
        assertThat(captchas.blocksRegistration(ALEX)).isTrue();
    }

    @Test
    @DisplayName("after failures, a login waits for a code once the name failed so often, and a login forgives it")
    void failuresBringTheCaptcha() {
        settings = settings(Captchas.When.FAILURES);
        fail(2);
        assertThat(captchas.blocks(STEVE, NAME)).isFalse();
        assertThat(captchas.blocksRegistration(STEVE)).isFalse();

        captchas.failed(new PlayerName("steve"));

        assertThat(captchas.blocks(STEVE, NAME)).isTrue();
        assertThat(captchas.blocks(ALEX, new PlayerName("Alex"))).isFalse();
        captchas.passed(NAME);
        assertThat(captchas.blocks(STEVE, NAME)).isFalse();
    }

    @Test
    @DisplayName("both: a registration and a login after failures each wait, and a solved captcha frees the connection")
    void bothAskAtEachMoment() {
        fail(3);

        assertThat(captchas.blocksRegistration(STEVE)).isTrue();
        assertThat(captchas.blocks(STEVE, NAME)).isTrue();
        captchas.solve(STEVE, captchas.show(STEVE));

        assertThat(captchas.blocks(STEVE, NAME)).isFalse();
        assertThat(captchas.blocksRegistration(STEVE)).isFalse();
        assertThat(captchas.blocks(ALEX, NAME)).isTrue();
    }

    @Test
    @DisplayName("a showing after the captcha was solved does not take the solved captcha back")
    void aLaterShowingKeepsTheSolvedCaptcha() {
        captchas.solve(STEVE, captchas.show(STEVE));

        captchas.show(STEVE);

        assertThat(captchas.blocksRegistration(STEVE)).isFalse();
    }

    @Test
    @DisplayName("failures are forgotten once the name stayed quiet for the memory")
    void failuresAreForgotten() {
        settings = settings(Captchas.When.FAILURES);
        fail(3);
        now = now.plus(Duration.ofMinutes(59));
        assertThat(captchas.blocks(STEVE, NAME)).isTrue();

        now = now.plus(Duration.ofMinutes(2));

        assertThat(captchas.blocks(STEVE, NAME)).isFalse();
        captchas.failed(NAME);
        assertThat(captchas.blocks(STEVE, NAME)).isFalse();
    }

    @Test
    @DisplayName("every so many failures the names that went quiet are let go, and the busy one is kept")
    void quietNamesAreLetGo() {
        settings = settings(Captchas.When.FAILURES);
        captchas.failed(new PlayerName("Alex"));
        now = now.plus(Duration.ofMinutes(61));

        fail(1023);

        assertThat(captchas.kept()).isEqualTo(1);
        assertThat(captchas.blocks(STEVE, NAME)).isTrue();
    }

    @Test
    @DisplayName("a code from an earlier showing is not this showing's, and a wrong answer spends the code")
    void aStaleCodeFails() {
        String first = captchas.show(STEVE);
        String second = captchas.show(STEVE);
        assertThat(second).isNotEqualTo(first);

        assertThat(captchas.solve(STEVE, first)).isFalse();
        assertThat(captchas.solve(STEVE, second)).isFalse();
        assertThat(captchas.code(STEVE)).isEmpty();
        assertThat(captchas.blocksRegistration(STEVE)).isTrue();

        String third = captchas.show(STEVE);
        assertThat(captchas.code(STEVE)).contains(third);
        assertThat(captchas.solve(STEVE, third)).isTrue();
        assertThat(captchas.solve(STEVE, third)).isFalse();
        assertThat(captchas.blocksRegistration(STEVE)).isFalse();
    }

    @Test
    @DisplayName("a reconnect starts over: the code and the solved captcha go with the connection")
    void aReconnectStartsOver() {
        String code = captchas.show(STEVE);
        captchas.solve(STEVE, code);

        captchas.forget(STEVE);

        assertThat(captchas.blocksRegistration(STEVE)).isTrue();
        assertThat(captchas.code(STEVE)).isEmpty();
        assertThat(captchas.solve(STEVE, code)).isFalse();
    }

    @Test
    @DisplayName("a code is as long as the setting and drawn from its characters, and nothing was shown is no answer")
    void aCodeFitsTheSettings() {
        settings =
                new Captchas.Settings(Captchas.When.BOTH, 3, 7, "AB", Duration.ofMinutes(60), Captchas.JavaScreen.MAP);

        String code = captchas.show(STEVE);

        assertThat(code).hasSize(7).matches("[AB]+");
        assertThat(captchas.solve(ALEX, code)).isFalse();
        assertThat(captchas.solve(ALEX, "")).isFalse();
    }

    @Test
    @DisplayName("a setting the map cannot draw, or a count below one, is refused with the name of the setting")
    void badSettingsAreRefused() {
        assertThatThrownBy(() -> new Captchas.Settings(
                        Captchas.When.BOTH, 3, 8, CHARACTERS, Duration.ofMinutes(60), Captchas.JavaScreen.MAP))
                .hasMessageContaining("length");
        assertThatThrownBy(() -> new Captchas.Settings(
                        Captchas.When.BOTH, 3, 2, CHARACTERS, Duration.ofMinutes(60), Captchas.JavaScreen.MAP))
                .hasMessageContaining("length");
        assertThatThrownBy(() -> new Captchas.Settings(
                        Captchas.When.BOTH, 3, 5, "AB-", Duration.ofMinutes(60), Captchas.JavaScreen.MAP))
                .hasMessageContaining("characters");
        assertThatThrownBy(() -> new Captchas.Settings(
                        Captchas.When.BOTH, 3, 5, "", Duration.ofMinutes(60), Captchas.JavaScreen.MAP))
                .hasMessageContaining("characters");
        assertThatThrownBy(() -> new Captchas.Settings(
                        Captchas.When.BOTH, 0, 5, CHARACTERS, Duration.ofMinutes(60), Captchas.JavaScreen.MAP))
                .hasMessageContaining("after-failures");
        assertThatThrownBy(() -> new Captchas.Settings(
                        Captchas.When.BOTH, 3, 5, CHARACTERS, Duration.ZERO, Captchas.JavaScreen.MAP))
                .hasMessageContaining("failure-memory");
    }
}
