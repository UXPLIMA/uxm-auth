package com.uxplima.auth.paper.architecture;

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
import java.util.stream.Stream;

import org.bukkit.Material;

import com.uxplima.auth.paper.testing.Modules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every material a shipped file names is a material this server has.
 *
 * <p>A window is a file, and a file names its squares by material. A name the server does not know draws
 * nothing: the tile is empty, the window looks half built, and the only sign of it is one line in a log
 * an operator reads when something else has already gone wrong. It is the cheapest defect in the estate
 * to ship, because a material can be renamed by the platform between two versions and a typo looks like
 * a material.
 *
 * <p>An operator's own file may name anything: their server is theirs, and the loader tells them. This is
 * about the files in the jar, which are read by somebody who has installed nothing yet.
 *
 * <p>Four shapes are not a plain material and are read as what they are. A value with a per cent sign is
 * a placeholder, and what it resolves to is decided while a window is drawn. A value with a colon carries
 * a provider in front of the material, as in {@code tinted:LEATHER_CHESTPLATE}, so the material is the
 * last part. A value that is empty names nothing on purpose. And a value this plugin's own code writes as
 * a string is a name it answers itself: an icon provider draws the tile of a cosmetic, a box or a preset,
 * and {@code material = cosmetic} is that name and not a material.
 */
final class EveryShippedMaterialExistsTest {

    private static final Path RESOURCES = Modules.commonResources();

    /**
     * {@code material = "CHEST"}, quoted or bare, wherever a shipped file writes one.
     *
     * <p>A key may open its line or follow a brace or a comma. uxmPlayerHider writes a whole icon on one
     * line, as {@code all { material = "LIME_DYE", glow = false }}, and a pattern anchored to the start
     * of a line read none of its three and said so by passing.
     */
    private static final Pattern NAMED =
            Pattern.compile("(?m)(?:^|[{,])\\s*(?:material|icon)\\s*=\\s*\"?([^\"\\n,}]+)\"?");

    @Test
    @DisplayName("every material a shipped file names is one the server knows")
    void everyshippedMaterialExists() throws IOException {
        List<String> unknown = new ArrayList<>();
        String code = everyJavaFile();

        for (Path file : shippedFiles()) {
            Matcher named = NAMED.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (named.find()) {
                String written = named.group(1).trim();
                if (written.isEmpty() || written.indexOf('%') >= 0) {
                    continue;
                }
                if (code.contains('"' + written + '"')) {
                    continue;
                }
                String material =
                        written.substring(written.lastIndexOf(':') + 1).trim();
                if (material.isEmpty() || Material.matchMaterial(material) != null) {
                    continue;
                }
                unknown.add(file.getFileName() + ": " + written);
            }
        }

        assertThat(unknown)
                .describedAs("A material the server does not know draws an empty tile, and the window looks"
                        + " half built to the first player who opens it.")
                .isEmpty();
    }

    @Test
    @DisplayName("the shipped files really name materials, so this cannot pass by reading nothing")
    void thefilesNameMaterials() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (Path file : shippedFiles()) {
            Matcher named = NAMED.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (named.find()) {
                found.add(named.group(1).trim());
            }
        }

        assertThat(found)
                .describedAs("this plugin ships windows, so a scan that finds no material is a broken scan")
                .hasSizeGreaterThan(3);
    }

    /** Every line of Java this plugin holds, which is where the names it answers itself are written. */
    private static String everyJavaFile() throws IOException {
        StringBuilder joined = new StringBuilder();
        for (Path root : Modules.mainSources()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file :
                        files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    joined.append(Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        }
        return joined.toString();
    }

    /**
     * Every file a first boot writes out, apart from the words.
     *
     * <p>A language file has a key called {@code material} too, and its value is the word a player reads
     * above a material, not a material. Reading those as materials is how this guard first reported that
     * uxmCrates ships a tile made of "Materyal".
     */
    private static List<Path> shippedFiles() throws IOException {
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".conf"))
                    .filter(path -> !path.toString().contains("messages"))
                    .toList();
        }
    }
}
