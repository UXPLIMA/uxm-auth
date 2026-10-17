package com.uxplima.auth.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No plugin of ours depends on another plugin of ours.
 *
 * <p>It is the oldest rule in the workspace and the one a customer feels first. Each plugin is sold on
 * its own and has to work as the only plugin on a server, so the only thing they share is uxmLib. An
 * optional link is allowed and a dependency is not: a plugin may publish something and another may read
 * it <em>if it happens to be there</em>, through a marked service registration, never a load order and
 * never a line that stops this plugin working when the other is absent.
 *
 * <p>Two ways to break it, and both are checked.
 *
 * <ul>
 *   <li>A class of ours imports a class of another plugin of ours. That is a link error at load on every
 *       server that bought one and not the other, and it happens before a single line of ours runs.
 *   <li>The descriptor declares another plugin of ours as {@code required: true}. The server then refuses
 *       to enable this plugin at all, and the operator reads a line about a product they never bought.
 * </ul>
 *
 * <p>uxmLib is not another plugin: it is the library every one of them is built on, and it is shaded or
 * taken as modules rather than installed beside us.
 */
final class NoPluginDependsOnAnotherTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Path DESCRIPTOR = Path.of("src", "main", "resources", "paper-plugin.yml");

    /** This plugin's own root package. Everything under it is itself. */
    private static final String OURS = "com.uxplima.auth.";

    /** The library, which every plugin is built on and which is never installed as a plugin beside us. */
    private static final String LIBRARY = "com.uxplima.uxmlib.";

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?(com\\.uxplima\\.[^;]+);");

    /** A declared dependency, which sits four spaces in, and the two lines under it. */
    private static final Pattern DEPENDENCY =
            Pattern.compile("(?m)^ {4}([A-Za-z][A-Za-z0-9_-]*):\\s*$((?:\\n {6}.*)*)");

    /** The name this jar goes by, which is the one {@code uxm} name it is allowed to require. */
    private static final Pattern OWN_NAME = Pattern.compile("(?m)^name:\\s*(\\S+)");

    @Test
    @DisplayName("nothing here imports a class of another plugin of ours")
    void nothingImportsAnotherPlugin() throws IOException {
        List<String> borrowed = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher imported = IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (imported.find()) {
                    String named = imported.group(1);
                    if (!named.startsWith(OURS) && !named.startsWith(LIBRARY)) {
                        borrowed.add(file.getFileName() + " imports " + named);
                    }
                }
            }
        }

        assertThat(borrowed)
                .describedAs("A plugin that names another plugin's class dies at link time on the server"
                        + " that bought one and not the other. Read it through a service registration if"
                        + " it happens to be there, or write our own.")
                .isEmpty();
    }

    @Test
    @DisplayName("the descriptor requires no plugin of ours")
    void thedescriptorRequiresNothingOfOurs() throws IOException {
        String descriptor = Files.readString(DESCRIPTOR, StandardCharsets.UTF_8);
        List<String> required = new ArrayList<>();

        Matcher declared = DEPENDENCY.matcher(descriptor);
        while (declared.find()) {
            String named = declared.group(1);
            if (named.toLowerCase(java.util.Locale.ROOT).startsWith("uxm")
                    && declared.group(2).contains("required: true")
                    && !isOwnHost(descriptor, named)) {
                required.add(named);
            }
        }

        assertThat(required)
                .describedAs("A plugin of ours declared required is a plugin the operator has to buy to"
                        + " run this one. Declare it with required: false, or not at all.")
                .isEmpty();
    }

    /**
     * Whether the required name is this jar's own host rather than another product.
     *
     * <p>A companion jar carries the product's name and a suffix: {@code uxmEssentials-rest} requires
     * {@code uxmEssentials} because it is the same product in two jars, and an operator who has the
     * companion has the host by definition. Anything else is a second product.
     */
    private static boolean isOwnHost(String descriptor, String named) {
        Matcher own = OWN_NAME.matcher(descriptor);
        if (!own.find()) {
            return false;
        }
        String mine = own.group(1).toLowerCase(java.util.Locale.ROOT);
        String host = named.toLowerCase(java.util.Locale.ROOT);
        return mine.equals(host) || mine.startsWith(host + "-");
    }

    @Test
    @DisplayName("the descriptor and the sources are both read, so this cannot pass on nothing")
    void bothsidesAreRead() throws IOException {
        assertThat(Files.readString(DESCRIPTOR, StandardCharsets.UTF_8))
                .describedAs("a descriptor this guard reads names the plugin. Finding none means the path"
                        + " is wrong and the other half of this test is reading an empty string.")
                .contains("name:");
        try (Stream<Path> files = Files.walk(SOURCE)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java")).count())
                    .describedAs("a source scan that finds no Java is a broken scan")
                    .isGreaterThan(10);
        }
    }
}
