package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.uxplima.template.application.message.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tile that answers no click draws no click line.
 *
 * <p>The last block of a tile is the click line, and UI-STYLE says what it is for in four words: it names
 * one action. A tile where clicking does nothing has no action to name, so it ends after its facts.
 *
 * <p>Two of them did not. The preview drew {@code "Looking costs nothing."} under an arrow on every
 * reward, and the keys window drew {@code "Keys are spent on a crate."} under one on every key. Neither is
 * an action and neither answers a question a player asked: the first is worse than filler, because a
 * sentence saying that looking is free implies a server where looking is not, and this plugin has never
 * had such a thing. The owner read it in game and asked which system it belonged to. It belonged to none.
 *
 * <p>So the rule is held here rather than remembered: no key of ours sits at a path ending in
 * {@code action-none}, and no catalogue carries one. A state a player needs to know is a fact row with a
 * coloured word, which is what {@code menu.reward.state-label} is.
 */
final class NoEmptyClickLineTest {

    private static final Path MESSAGES = Path.of("src", "main", "resources", "messages");

    @Test
    @DisplayName("no key exists only to fill the click line of a tile that answers no click")
    void nokeyFillsTheClickLine() {
        List<String> filler = new ArrayList<>();
        for (Keys key : Keys.values()) {
            if (key.path().endsWith("action-none")) {
                filler.add(key.name() + " (" + key.path() + ")");
            }
        }

        assertThat(filler)
                .describedAs("A tile with nothing to say about a click says nothing. Draw the facts and"
                        + " stop, or make the line a state row that carries something.")
                .isEmpty();
    }

    @Test
    @DisplayName("and no catalogue carries one either, in any language")
    void nocatalogueCarriesOne() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MESSAGES)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.strip().startsWith("action-none")) {
                        found.add(file.getFileName() + ": " + line.strip());
                    }
                }
            }
        }

        assertThat(found)
                .describedAs("A line nobody draws is a line a translator paid for.")
                .isEmpty();
    }
}
