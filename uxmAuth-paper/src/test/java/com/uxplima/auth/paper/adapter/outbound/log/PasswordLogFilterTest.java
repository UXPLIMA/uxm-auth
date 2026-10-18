package com.uxplima.auth.paper.adapter.outbound.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A password typed after a command never reaches the console or the log file. */
final class PasswordLogFilterTest {

    private final PasswordMask mask = new PasswordMask(() -> Set.of("login", "l", "register", "changepassword"));

    @Test
    @DisplayName("a logged login line keeps the command and loses the password, under every label")
    void aLoggedLoginLineHidesThePassword() {
        assertThat(mask.mask("Notch issued server command: /login hunter2"))
                .isEqualTo("Notch issued server command: /login ***");
        assertThat(mask.mask("Notch issued server command: /l hunter2")).doesNotContain("hunter2");
        assertThat(mask.mask("Notch issued server command: /LOGIN hunter2")).doesNotContain("hunter2");
        assertThat(mask.mask("Notch issued server command: /uxmauth:login hunter2"))
                .doesNotContain("hunter2");
        assertThat(mask.mask("Notch issued server command: /register hunter2 hunter2"))
                .isEqualTo("Notch issued server command: /register ***");
    }

    @Test
    @DisplayName("a staff branch that carries a password hides what follows the branch, and every other branch stays")
    void aStaffPasswordIsHidden() {
        PasswordMask staff = new PasswordMask(
                () -> Set.of("login"),
                () -> new PasswordMask.Branches(Set.of("uxmauth", "auth"), Set.of("setpassword", "sifreata")));

        assertThat(staff.mask("Notch issued server command: /uxmauth setpassword Steve hunter2"))
                .isEqualTo("Notch issued server command: /uxmauth setpassword ***");
        assertThat(staff.mask("Notch issued server command: /auth SIFREATA Steve hunter2"))
                .doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /uxmauth:uxmauth setpassword Steve hunter2"))
                .doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /uxmauth info Steve"))
                .isEqualTo("Notch issued server command: /uxmauth info Steve");
        assertThat(staff.mask("Notch issued server command: /uxmauth setpassword"))
                .isEqualTo("Notch issued server command: /uxmauth setpassword");
        assertThat(staff.mask("Notch issued server command: /login hunter2"))
                .isEqualTo("Notch issued server command: /login ***");
    }

    @Test
    @DisplayName("a password is hidden however the line is spaced, and inside a command that runs another")
    void aPasswordIsHiddenWhateverTheSpacing() {
        PasswordMask staff = new PasswordMask(
                () -> Set.of("login"),
                () -> new PasswordMask.Branches(Set.of("uxmauth", "auth"), Set.of("setpassword")));

        assertThat(staff.mask("Notch issued server command: /uxmauth  setpassword Steve hunter2"))
                .doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /uxmauth\tsetpassword Steve hunter2"))
                .doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /execute as @s run uxmauth setpassword Steve hunter2"))
                .doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /minecraft:execute run auth:auth setpassword S hunter2"))
                .doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /execute run login hunter2"))
                .doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /login  hunter2")).doesNotContain("hunter2");
        assertThat(staff.mask("Notch issued server command: /execute run uxmauth info Steve"))
                .isEqualTo("Notch issued server command: /execute run uxmauth info Steve");
    }

    @Test
    @DisplayName("a line that is not one of these commands is left as it was")
    void anUnrelatedLineIsUntouched() {
        assertThat(mask.mask("Notch issued server command: /spawn")).isEqualTo("Notch issued server command: /spawn");
        assertThat(mask.mask("Notch issued server command: /loginfo now"))
                .isEqualTo("Notch issued server command: /loginfo now");
        assertThat(mask.mask("Notch joined the game")).isEqualTo("Notch joined the game");
        assertThat(mask.mask("Notch issued server command: /login")).isEqualTo("Notch issued server command: /login");
    }

    @Test
    @DisplayName("another plugin's login under its own namespace is masked too, because it is a password all the same")
    void anotherNamespaceIsMaskedToo() {
        assertThat(mask.mask("Notch issued server command: /authme:login hunter2"))
                .doesNotContain("hunter2");
    }

    @Test
    @DisplayName("the filter stops the line that carries a password and lets every other line through")
    void theFilterDeniesOnlyThePasswordLine() {
        PasswordLogFilter filter = new PasswordLogFilter(mask);

        assertThat(filter.filter(event("Notch issued server command: /login hunter2")))
                .isEqualTo(Filter.Result.DENY);
        assertThat(filter.filter(event("Notch issued server command: /spawn"))).isEqualTo(Filter.Result.NEUTRAL);
        assertThat(filter.filter(event("Notch issued server command: /login ***")))
                .isEqualTo(Filter.Result.NEUTRAL);
    }

    private static LogEvent event(String line) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("Minecraft")
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage(line))
                .build();
    }
}
