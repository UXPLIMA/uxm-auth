package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmlib.content.ContentHooks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every content plugin this one reads is declared, so it is loaded first.
 *
 * <p>This is the Bedrock failure with a different name. A content seam is resolved once, while this plugin
 * enables: {@code ContentHooks.customItems(server)} asks the plugin manager which vendors are up, and
 * whatever it decides then is what the whole run uses. A vendor that loads after us is a vendor this plugin
 * never sees, so a server running Oraxen prices its ruby ore as deepslate for the rest of the session and
 * nothing anywhere says why.
 *
 * <p>Which vendors matter is asked of the library rather than written here. A seam this plugin calls brings
 * every vendor that seam knows, and a vendor uxmLib learns about tomorrow is covered on the day it learns.
 *
 * <p>Not one of them is a dependency. Every entry is {@code required: false} and this plugin runs fully
 * with none of them installed, which is the case almost every customer is in.
 */
final class EveryVendorLoadsFirstTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Path DESCRIPTOR = Path.of("src", "main", "resources", "paper-plugin.yml");

    /** Each content seam, and the vendors it asks the plugin manager about. */
    private static final Map<String, java.util.function.Supplier<Set<String>>> SEAMS = Map.of(
            "customItems", ContentHooks::customItemVendors,
            "customMobs", ContentHooks::customMobVendors,
            "customHarvests", ContentHooks::customHarvestVendors,
            "skillLevels", ContentHooks::skillVendors,
            "petOwners", ContentHooks::petVendors,
            "foreignEnchantments", ContentHooks::enchantmentVendors);

    /** A declared dependency, which sits four spaces in under {@code dependencies.server}. */
    private static final Pattern DECLARED = Pattern.compile("^ {4}([A-Za-z][A-Za-z0-9_-]*):", Pattern.MULTILINE);

    @Test
    @DisplayName("every content vendor this plugin can read is declared, so it is up before we ask")
    void everyVendorIsDeclared() throws IOException {
        Set<String> declared = declaredLowerCase();
        List<String> missing = new ArrayList<>();

        for (String vendor : vendorsThisPluginAsksAbout()) {
            if (!declared.contains(vendor)) {
                missing.add(vendor);
            }
        }

        assertThat(missing)
                .describedAs("A seam is resolved once at enable, so a vendor that loads after us is one this"
                        + " plugin never sees. Declare it under dependencies.server with load: BEFORE and"
                        + " required: false.")
                .isEmpty();
    }

    @Test
    @DisplayName("every vendor is declared to load before this plugin, and not one is required")
    void novendorIsRequired() throws IOException {
        String text = Files.readString(DESCRIPTOR, StandardCharsets.UTF_8);

        assertThat(text)
                .describedAs("This plugin runs fully with none of these installed, which is where almost"
                        + " every customer is.")
                .doesNotContain("required: true");
        assertThat(count(text, "load: BEFORE"))
                .describedAs("A declared plugin that is not loaded first is a coin flip on the order.")
                .isEqualTo(count(text, "required: false"));
    }

    /** The vendors of every seam this plugin's own code calls, in lower case as the library names them. */
    private static Set<String> vendorsThisPluginAsksAbout() throws IOException {
        String source = allSource();
        Set<String> wanted = new LinkedHashSet<>();
        SEAMS.forEach((seam, vendors) -> {
            if (source.contains("ContentHooks." + seam + "(")) {
                vendors.get().forEach(vendor -> wanted.add(vendor.toLowerCase(Locale.ROOT)));
            }
        });
        return wanted;
    }

    private static Set<String> declaredLowerCase() throws IOException {
        String text = Files.readString(DESCRIPTOR, StandardCharsets.UTF_8);
        Set<String> found = new LinkedHashSet<>();
        Matcher declared = DECLARED.matcher(text);
        while (declared.find()) {
            found.add(declared.group(1).toLowerCase(Locale.ROOT));
        }
        return found;
    }

    private static String allSource() throws IOException {
        StringBuilder joined = new StringBuilder();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                joined.append(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return joined.toString();
    }

    private static int count(String text, String needle) {
        int found = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            found++;
            at = text.indexOf(needle, at + needle.length());
        }
        return found;
    }
}
