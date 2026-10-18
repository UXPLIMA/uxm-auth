package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** screens.conf: where a player waits, and the words and order of every screen, all the operator's. */
final class ScreenFileTest {

    @Test
    @DisplayName("a file that says nothing waits in the dialog, kicks on cancel, and has no pad and no rules")
    void defaults(@TempDir Path folder) throws IOException {
        ScreenSettings screens = screensFrom(folder, "");

        assertThat(screens.waitingRoom()).isEqualTo(ScreenSettings.WaitingRoom.DIALOG);
        assertThat(screens.cancel()).isEqualTo(ScreenSettings.Cancel.KICK);
        assertThat(screens.canClose()).isFalse();
        assertThat(screens.pinPad()).isFalse();
        assertThat(screens.rules().enabled()).isFalse();
        assertThat(screens.register().title()).isEqualTo("@screen.register.title");
        assertThat(screens.register().label("password")).isEqualTo("@screen.register.password");
        assertThat(screens.login().label("submit")).isEqualTo("@screen.login.submit");
        assertThat(screens.loginWorld()).isEqualTo("uxmauth_login");
        assertThat(screens.code().title()).isEqualTo("@screen.code.title");
        assertThat(screens.code().label("code")).isEqualTo("@screen.code.code");
        assertThat(screens.code().label("submit")).isEqualTo("@screen.code.submit");
        assertThat(screens.code().label("cancel")).isEqualTo("@screen.code.cancel");
    }

    @Test
    @DisplayName("the login world is named by the file")
    void theLoginWorldIsNamedByTheFile(@TempDir Path folder) throws IOException {
        ScreenSettings screens =
                screensFrom(folder, "waiting-room = \"login-world\"\nlogin-world-name = \"lobby_wait\"\n");

        assertThat(screens.waitingRoom()).isEqualTo(ScreenSettings.WaitingRoom.LOGIN_WORLD);
        assertThat(screens.loginWorld()).isEqualTo("lobby_wait");
    }

    @Test
    @DisplayName("the shipped file says exactly what the code defaults to")
    void theShippedFileSaysTheDefaults(@TempDir Path folder) throws IOException {
        Path shipped = folder.resolve("screens.conf");
        try (InputStream file = Objects.requireNonNull(getClass().getResourceAsStream("/screens.conf"))) {
            Files.copy(file, shipped);
        }
        Path empty = Files.createDirectories(folder.resolve("empty"));

        assertThat(new ScreenFile(shipped).current()).isEqualTo(screensFrom(empty, ""));
    }

    @Test
    @DisplayName("an operator's own words are kept as written, beside a key from the catalogue")
    void ownWordsAreKept(@TempDir Path folder) throws IOException {
        ScreenSettings screens = screensFrom(folder, """
                login {
                  title = "<gold>Welcome to Survival"
                  body  = ["@screen.login.body", "<gray>Our discord: example.org"]
                }
                """);

        assertThat(screens.login().title()).isEqualTo("<gold>Welcome to Survival");
        assertThat(screens.login().body()).containsExactly("@screen.login.body", "<gray>Our discord: example.org");
    }

    @Test
    @DisplayName("a waiting room this plugin does not have is refused, naming the line")
    void anUnknownWaitingRoomIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> screensFrom(folder, "waiting-room = \"lobby\"\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("waiting-room")
                .hasMessageContaining("lobby");
    }

    @Test
    @DisplayName("an old file gains the keys a release adds and keeps what the operator wrote")
    void anOldFileIsBroughtUpToDate(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("screens.conf");
        Files.writeString(file, "pin-pad = true\n", StandardCharsets.UTF_8);

        assertThat(ScreenFile.bringUpToDate(file, getClass().getClassLoader())).isTrue();

        ScreenSettings screens = new ScreenFile(file).current();
        assertThat(screens.pinPad()).isTrue();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("waiting-room");
    }

    @Test
    @DisplayName("a label nobody named is an empty line, never an exception in front of a player")
    void anUnnamedLabelIsEmpty(@TempDir Path folder) throws IOException {
        assertThat(screensFrom(folder, "").login().label("nothing-here")).isEmpty();
        assertThat(screensFrom(folder, "").rules().lines()).isEqualTo(List.of("@rules.line-1", "@rules.line-2"));
    }

    private static ScreenSettings screensFrom(Path folder, String body) throws IOException {
        Path file = folder.resolve("screens.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return new ScreenFile(file).current();
    }
}
