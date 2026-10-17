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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every package carries a {@code package-info.java} that marks it null marked.
 *
 * <p>This is not a tidiness rule. NullAway runs with {@code onlyNullMarked}, so a package with no
 * {@code package-info.java} is a package it does not check at all: every field, parameter and return in it
 * is unannotated as far as the analysis is concerned, and a null that would fail the build anywhere else
 * passes there in silence. The build stays green and the guarantee is simply absent.
 *
 * <p>Nothing says which packages are checked and which are not. A package is created by putting a file in
 * a folder, and the {@code package-info.java} is a second file somebody has to remember. Two packages in
 * this estate were found without one on 2026-09-10, both of them adapters holding real code.
 */
final class EveryPackageIsNullMarkedTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    /** The annotation, written as the estate writes it: fully qualified, so the file needs no import. */
    private static final String MARK = "NullMarked";

    @Test
    @DisplayName("every package that holds code carries a package-info")
    void everypackageCarriesAPackageInfo() throws IOException {
        List<String> missing = new ArrayList<>();

        for (Path folder : packages()) {
            if (!Files.isRegularFile(folder.resolve("package-info.java"))) {
                missing.add(SOURCE.relativize(folder).toString());
            }
        }

        assertThat(missing)
                .describedAs("NullAway runs onlyNullMarked, so a package with no package-info is a package"
                        + " nothing checks. The build stays green and the guarantee is absent.")
                .isEmpty();
    }

    @Test
    @DisplayName("every package-info marks its package null marked")
    void everypackageInfoMarksIt() throws IOException {
        List<String> unmarked = new ArrayList<>();

        for (Path folder : packages()) {
            Path info = folder.resolve("package-info.java");
            if (Files.isRegularFile(info)
                    && !Files.readString(info, StandardCharsets.UTF_8).contains(MARK)) {
                unmarked.add(SOURCE.relativize(folder).toString());
            }
        }

        assertThat(unmarked)
                .describedAs("A package-info without the mark is a file that looks like the rule is kept"
                        + " and keeps nothing.")
                .isEmpty();
    }

    @Test
    @DisplayName("the walk finds the packages, so neither test passes by looking at nothing")
    void thewalkFindsThePackages() throws IOException {
        assertThat(packages())
                .describedAs("A walk that finds no package is two guards that always pass.")
                .hasSizeGreaterThanOrEqualTo(5);
    }

    /** Every folder under the source root that holds a Java file, which is every package. */
    private static Set<Path> packages() throws IOException {
        Set<Path> folders = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                folders.add(file.getParent());
            }
        }
        return folders;
    }
}
