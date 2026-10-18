package com.uxplima.auth.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.auth.paper.testing.Modules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every id a shipped window names is one this plugin registers.
 *
 * <p>A menu spec reaches the code by name. The tile that chooses a colour says
 * {@code ["uxmauth:<id>"]} and the code says {@code bindings.action("uxmauth:<id>", ...)}, and
 * nothing but the spelling holds the two together. A typo in either is a window that opens and does
 * nothing when it is clicked: the engine logs an unknown ref once and the operator reads a file that looks
 * right.
 *
 * <p>The check is derived rather than listed. It reads every {@code uxmauth:} token out of the shipped specs
 * and asks the production sources for that exact string, so a new binding needs no edit here and a renamed
 * one fails on both sides at once. Only this plugin's own namespace is read: {@code close}, {@code sound},
 * {@code perm:} and the rest belong to the engine, which registers them itself.
 */
final class EveryMenuIdTheSpecNamesIsRegisteredTest {

    /** The namespace this plugin registers its own ids under, which is the only one it must answer for. */
    private static final String NAMESPACE = "uxmauth";

    private static final Path MENUS = Modules.commonResources().resolve("menus");

    /** One namespaced id as a spec writes it, in quotes or bare after an {@code =}. */
    private static final Pattern ID = Pattern.compile(NAMESPACE + ":[a-z0-9-]+");

    @Test
    @DisplayName("every id the shipped windows name is registered in the code")
    void everyIdIsRegistered() {
        String sources = sources();
        List<String> unregistered = new ArrayList<>();

        for (String id : idsInSpecs()) {
            if (!sources.contains('"' + id + '"')) {
                unregistered.add(id);
            }
        }

        assertThat(unregistered)
                .describedAs("a window names an id nothing registers, so the click reaches nothing. Fix the"
                        + " spelling on whichever side is wrong, or register the binding")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan reads both sides, so it cannot pass by finding nothing")
    void bothSidesAreRead() {
        assertThat(idsInSpecs())
                .describedAs("this plugin ships a window that names its own ids, so finding none is a broken read")
                .isNotEmpty();
        assertThat(sources())
                .describedAs("this plugin registers bindings, so finding none is a broken read")
                .contains("bindings.action(");
    }

    /** Every {@code <namespace>:<id>} token the shipped windows name, comments included. */
    private static Set<String> idsInSpecs() {
        Set<String> ids = new LinkedHashSet<>();
        for (Path file : under(MENUS, ".conf")) {
            Matcher found = ID.matcher(read(file));
            while (found.find()) {
                ids.add(found.group());
            }
        }
        return ids;
    }

    private static String sources() {
        StringBuilder all = new StringBuilder();
        for (Path root : Modules.mainSources()) {
            for (Path file : under(root, ".java")) {
                all.append(read(file)).append('\n');
            }
        }
        return all.toString();
    }

    private static List<Path> under(Path root, String suffix) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + root, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
