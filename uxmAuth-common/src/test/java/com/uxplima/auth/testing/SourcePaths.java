package com.uxplima.auth.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where a guard finds a source file of this plugin, without spelling this plugin's name.
 *
 * <p>A guard that reads a source off the disk used to write the path out piece by piece:
 *
 * <pre>{@code
 *   Path.of("src", "main", "java", "com", "uxplima", "template", "bootstrap", "PluginModule.java")
 * }</pre>
 *
 * <p>{@code scripts/new-plugin.sh} rewrites two spellings of the package, {@code com.uxplima.auth}
 * and {@code com/uxplima/auth}. **Neither one appears in that line.** The word sits alone in a
 * string argument, so the rename walks straight past it and the new plugin's guard keeps pointing at a
 * package that no longer exists. uxmPlots met exactly that on its first build: four failing tests in two
 * guards, on untouched code.
 *
 * <p>It hid because <b>the template builds green</b>. The defect exists only in the output of the
 * rename, and nothing builds that output except a new plugin on its first day. The older note
 * {@code 2026-08-28-the-template-must-be-built-once.md} is the same lesson and its item 4 is this
 * defect's sibling: a path spelling the rename could not see, repaired then for the slash form and not
 * for this one.
 *
 * <p>So the answer is not a third spelling for the script to chase. **The package is derived from the
 * test class that asks for it**, which is right in the template, right in every plugin made from it, and
 * right again in a plugin renamed a second time.
 */
public final class SourcePaths {

    private static final String SOURCE_ROOT_PREFIX = "src/main/java";

    /** The last package segment of a test, which is the folder the guards sit in rather than the plugin. */
    private static final String TEST_FOLDER = "architecture";

    private SourcePaths() {}

    /**
     * The path of a main source file, under this plugin's own package.
     *
     * @param asker any class in this plugin's test tree, which is where the package is read from
     * @param segments the package segments under the plugin's root, then the file name
     */
    public static Path of(Class<?> asker, String... segments) {
        Objects.requireNonNull(asker, "asker");
        Objects.requireNonNull(segments, "segments");
        List<String> parts = new ArrayList<>(List.of(SOURCE_ROOT_PREFIX.split("/")));
        parts.addAll(List.of(pluginPackage(asker).split("\\.")));
        parts.addAll(List.of(segments));
        return Path.of(parts.get(0), parts.subList(1, parts.size()).toArray(new String[0]));
    }

    /**
     * This plugin's root package, read off {@code asker}.
     *
     * <p>A guard lives in {@code <plugin>.architecture}, so the root is its package with that last
     * segment removed. A caller from anywhere else gets its own package, which is what it asked for.
     */
    public static String pluginPackage(Class<?> asker) {
        Objects.requireNonNull(asker, "asker");
        String name = asker.getPackageName();
        return name.endsWith("." + TEST_FOLDER) ? name.substring(0, name.length() - TEST_FOLDER.length() - 1) : name;
    }
}
