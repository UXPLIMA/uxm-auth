package com.uxplima.auth.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The API names nothing of the plugin behind it, so the plugin can change inside without breaking a plugin that
 * compiled against it, and each platform's events name nothing of the other platform.
 */
final class TheApiNamesNothingOfThePluginTest {

    private static final JavaClasses API = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.uxplima.auth.api");

    @Test
    @DisplayName("no api class names a type of the plugin or of uxmLib")
    void noPluginType() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.uxplima.auth.domain..",
                        "com.uxplima.auth.application..",
                        "com.uxplima.auth.adapter..",
                        "com.uxplima.auth.paper..",
                        "com.uxplima.auth.velocity..",
                        "com.uxplima.uxmlib..")
                .check(API);
    }

    @Test
    @DisplayName("the Paper events name no Velocity type, the Velocity events no Bukkit type, the core neither")
    void platformsStayApart() {
        noClasses()
                .that()
                .resideInAPackage("com.uxplima.auth.api.paper..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.velocitypowered..")
                .check(API);
        noClasses()
                .that()
                .resideInAPackage("com.uxplima.auth.api.velocity..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.bukkit..", "io.papermc..")
                .check(API);
        noClasses()
                .that()
                .resideInAPackage("com.uxplima.auth.api")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.bukkit..", "io.papermc..", "com.velocitypowered..")
                .check(API);
    }
}
