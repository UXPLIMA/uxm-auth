package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmlib.content.ContentHooks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every line an operator writes is read through the library's one resolver, {@code Operands}.
 *
 * <p>An operator writes {@code {if=%mcmmo_level_mining% >= 50}} after an effect, or the same comparison
 * as a requirement. {@code Operands} answers a skill level off the skill plugin itself and hands
 * everything else to PlaceholderAPI. A context that goes straight to PlaceholderAPI answers the level
 * only when the server also runs the mcMMO expansion, and otherwise compares the token as twenty
 * characters of text: the line is never true and nothing is logged. On 2026-09-22 five plugins of the
 * estate read the level and twenty one did not, so one line meant two things depending on which
 * plugin it was written in.
 *
 * <p>uxmLib is relocated into every plugin, so the skill source is a static each plugin holds for
 * itself. Nothing installed by another plugin of ours reaches this one. A plugin that reads through
 * {@code Operands} installs the source at enable, forgets it at disable, and asks the server to load
 * every skill plugin the library reads first, or the source it installs answers nothing.
 */
final class EveryOperatorLineIsReadThroughTheStandardOperandsTest {

    private static final List<String> BUILDERS = List.of("ActionContext.builder(", "ConditionRequest.builder(");

    /** A resolver handed in from outside, whose maker answers for it under the second case. */
    private static final Pattern INJECTED = Pattern.compile("\\bOperandResolver\\s+resolver\\s*[,)]");

    private static final List<String> BYPASSES = List.of(
            "builder(OperandResolver.ofBiFunction(PlaceholderApi::apply))", "builder(OperandResolver.identity())");

    @Test
    @DisplayName("every context and every request reads an operator's line through Operands")
    void everyContextReadsThroughTheStandardOperands() throws IOException {
        List<String> bypassing = new ArrayList<>();
        for (Path file : productionSources()) {
            String source = Files.readString(file);
            if (BUILDERS.stream().noneMatch(source::contains) || !source.contains(".player(")) {
                // A context with no player has no skill level to read: a backup that finished is said to
                // the server, and Operands would hand every token straight back.
                continue;
            }
            for (String bypass : BYPASSES) {
                if (source.contains(bypass)) {
                    bypassing.add(file.getFileName() + " reads its lines with " + bypass);
                }
            }
            if (!source.contains("Operands.") && !INJECTED.matcher(source).find()) {
                bypassing.add(file.getFileName() + " builds a context and never reads through Operands");
            }
        }

        assertThat(bypassing)
                .describedAs("a skill level an operator names is never true in a context that skips Operands")
                .isEmpty();
    }

    @Test
    @DisplayName("a plugin that reads through Operands installs the skill source and forgets it again")
    void theSkillSourceIsInstalledWhereItIsRead() throws IOException {
        String all = everySource();
        if (!all.contains("Operands.")) {
            return;
        }

        assertThat(all)
                .describedAs("uxmLib is relocated, so no other plugin installs the skill source for this one")
                .contains("Operands.readingSkills(");
        assertThat(all)
                .describedAs("a reload that keeps the old source holds the old server's plugins")
                .contains("Operands.forgetEverything(");
    }

    @Test
    @DisplayName("a plugin that installs the skill source declares every skill plugin the library reads, first")
    void everySkillPluginLoadsFirstWhereASkillIsRead() throws IOException {
        if (!everySource().contains("Operands.readingSkills(")) {
            return;
        }
        String descriptor = Files.readString(Path.of("src", "main", "resources", "paper-plugin.yml"))
                .toLowerCase(Locale.ROOT);
        Set<String> vendors = ContentHooks.skillVendors();
        List<String> missing = new ArrayList<>();
        for (String vendor : vendors) {
            Pattern first = Pattern.compile("\\n\\s+" + Pattern.quote(vendor.toLowerCase(Locale.ROOT))
                    + ":\\s*\\n\\s+load: before\\s*\\n\\s+required: false");
            if (!first.matcher(descriptor).find()) {
                missing.add(vendor);
            }
        }

        assertThat(vendors)
                .describedAs("the library reads at least one skill plugin, or this case proves nothing")
                .isNotEmpty();
        assertThat(missing)
                .describedAs("a source installed before its skill plugin enables finds nothing and answers nothing."
                        + " The list is the library's, so a skill plugin it learns tomorrow is asked for here."
                        + " Declare each with load: BEFORE and required: false")
                .isEmpty();
    }

    private static String everySource() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path file : productionSources()) {
            all.append(Files.readString(file)).append('\n');
        }
        return all.toString();
    }

    private static List<Path> productionSources() throws IOException {
        try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
            return sources.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
