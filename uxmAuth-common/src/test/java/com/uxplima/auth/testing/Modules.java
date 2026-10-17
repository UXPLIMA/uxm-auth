package com.uxplima.auth.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The three modules of this build, as the guards of the common module see them.
 *
 * <p>Gradle runs a module's tests in that module's folder, so a guard that reads {@code src/main/java} reads one
 * module only. A key declared here is sent from the Paper module and later from the Velocity one, and the boot
 * that brings a file up to date is in a platform module too. A guard that has to see the whole plugin asks this
 * class for every source root instead.
 */
public final class Modules {

    /** Every module of the build, in the order settings.gradle.kts includes them. */
    public static final List<String> NAMES = List.of("uxmAuth-common", "uxmAuth-paper", "uxmAuth-velocity");

    private Modules() {}

    /** The folder that holds the three modules and the documents. */
    public static Path repositoryRoot() {
        return java.util.Objects.requireNonNull(Path.of("").toAbsolutePath().getParent(), "parent");
    }

    /** The main source root of every module that has one. */
    public static List<Path> mainSources() {
        Path root = repositoryRoot();
        return NAMES.stream()
                .map(name -> root.resolve(name).resolve("src").resolve("main").resolve("java"))
                .filter(Files::isDirectory)
                .toList();
    }
}
