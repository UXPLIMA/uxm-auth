package com.uxplima.template.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import com.uxplima.uxmlib.command.annotation.CommandMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The command layer writes lines this plugin did not, and they are the one screen no style guard can read:
 * they live in a jar rather than in {@code src/main/resources}. This test is the guard for that gap.
 *
 * <p>It fails the day uxmLib adds a method to {@link CommandMessages} that {@code CommandLines} does not
 * answer, because a method left at its default is a line a player reads in English, in vanilla colours.
 */
class CommandLinesTest {

    @Test
    @DisplayName("every line the command layer writes is answered from our own catalog")
    void answersEveryCommandMessage() {
        List<String> written = Arrays.stream(CommandMessages.class.getDeclaredMethods())
                .filter(Method::isDefault)
                .map(Method::getName)
                .sorted()
                .toList();
        List<String> answered = Arrays.stream(CommandLines.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();
        assertThat(answered)
                .describedAs("uxmLib writes a line this plugin does not word. Add the key to every catalog"
                        + " and override the method in CommandLines, or a player reads it in English.")
                .containsAll(written);
    }
}
