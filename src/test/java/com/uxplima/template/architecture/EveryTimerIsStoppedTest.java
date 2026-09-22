package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.template.testing.SourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every repeating task this plugin starts is stopped when it stops.
 *
 * <p>A reload disables the plugin and enables it again. A timer the composition root started and nobody
 * cancelled keeps running, holding the objects of the old graph, and the new enable starts a second one
 * beside it. Two reloads and there are three, each writing the same rows from the stale half of a graph
 * whose database is closed. The symptom reads as a storage defect and the cause is one missing line in
 * {@code shutdown}.
 *
 * <p>Which tasks exist is asked of the code rather than written here: they are the fields the root
 * assigns from a {@code Timer} call on the scheduler. A task somebody adds tomorrow is covered on the day
 * they add the field.
 *
 * <p>The stop may be anywhere {@code shutdown} reaches, one call deep, because a root that cancels four
 * things usually does it in a method called {@code stopWhatRuns} rather than inline. A task cancelled two
 * calls away fails this and should: the point is that a reader of {@code shutdown} can see what stops.
 */
final class EveryTimerIsStoppedTest {

    private static final Path ROOT = SourcePaths.of(EveryTimerIsStoppedTest.class, "bootstrap", "PluginModule.java");

    /** A field the root assigns from a repeating task: {@code this.saveTask = scheduler.asyncTimer(...)}. */
    private static final Pattern STARTED =
            Pattern.compile("this\\.([A-Za-z0-9_]+)\\s*=\\s*scheduler\\.\\w*[Tt]imer\\(");

    /**
     * The other shape: a root with a dozen tasks keeps them in one list.
     *
     * <p>{@code tasks.add(scheduler.asyncTimer(...))}. The thing that has to reach the shutdown is then
     * the list rather than a field per task, and a list nobody walks leaks every task in it at once.
     */
    private static final Pattern COLLECTED = Pattern.compile("([A-Za-z0-9_]+)\\.add\\(\\s*scheduler\\.\\w*[Tt]imer\\(");

    /** A private method the shutdown path calls, which is where a root with several tasks stops them. */
    private static final Pattern CALLED = Pattern.compile("(?m)^\\s{8}([a-z][A-Za-z0-9_]*)\\(");

    @Test
    @DisplayName("every repeating task the root starts is stopped by the shutdown path")
    void everytimerIsStopped() throws IOException {
        String source = Files.readString(ROOT, StandardCharsets.UTF_8);
        String reach = shutdownReach(source);

        List<String> running = new ArrayList<>();
        for (String task : started(source)) {
            if (!reach.contains(task)) {
                running.add(task);
            }
        }

        assertThat(running)
                .describedAs("A timer nobody cancels outlives the reload that replaced it, and the second"
                        + " enable starts another beside it. Cancel it in shutdown, or in a method shutdown"
                        + " calls.")
                .isEmpty();
    }

    @Test
    @DisplayName("the root really starts a task, so this cannot pass by finding nothing")
    void therootStartsSomething() throws IOException {
        String source = Files.readString(ROOT, StandardCharsets.UTF_8);

        assertThat(started(source))
                .describedAs("this plugin schedules a repeating task, so a scan that finds none is a broken" + " scan")
                .isNotEmpty();
        assertThat(shutdownReach(source))
                .describedAs("a shutdown this guard cannot read is a shutdown it cannot hold anything to")
                .isNotBlank();
    }

    /** Every field the root assigns from a repeating task. */
    private static Set<String> started(String source) {
        Set<String> found = new LinkedHashSet<>();
        Matcher assigned = STARTED.matcher(source);
        while (assigned.find()) {
            found.add(assigned.group(1));
        }
        Matcher collected = COLLECTED.matcher(source);
        while (collected.find()) {
            found.add(collected.group(1));
        }
        return found;
    }

    /** The text of {@code shutdown}, plus the body of every method it calls by name. */
    private static String shutdownReach(String source) {
        String shutdown = bodyOf(source, declaration("shutdown"));
        StringBuilder reach = new StringBuilder(shutdown);
        Matcher called = CALLED.matcher(shutdown);
        while (called.find()) {
            reach.append('\n').append(bodyOf(source, declaration(called.group(1))));
        }
        return reach.toString();
    }

    /**
     * Where a method is declared, which is not where it is called.
     *
     * <p>A call sits eight spaces in and a declaration four, and the call comes first in the file. Looking
     * for the name alone found the call and then read the next block after it, which is how this guard
     * first reported that a root cancelling four tasks cancelled none of them.
     */
    private static Pattern declaration(String method) {
        return Pattern.compile("(?m)^ {4}[A-Za-z@][^\n(]*\\b" + Pattern.quote(method) + "\\s*\\(");
    }

    /** The body of the first method the pattern declares, braces matched. */
    private static String bodyOf(String source, Pattern declaration) {
        Matcher found = declaration.matcher(source);
        if (!found.find()) {
            return "";
        }
        int at = found.start();
        int open = source.indexOf('{', at);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i);
                }
            }
        }
        return source.substring(open);
    }
}
