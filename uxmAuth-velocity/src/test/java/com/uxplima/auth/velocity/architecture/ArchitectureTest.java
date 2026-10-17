package com.uxplima.auth.velocity.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The fences of the Velocity module. The rules that guard the domain and the application layer live in the
 * common module, where those layers are. These are the ones that only a proxy module can break.
 */
@AnalyzeClasses(packages = "com.uxplima.auth.velocity", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    // The one that matters. A proxy has no Bukkit on its class path at all, so a single Bukkit type here
    // is a class that fails to link the first time the proxy touches it, at run time, on a live network.
    @ArchTest
    static final ArchRule velocityKnowsNothingOfBukkit = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "com.destroystokyo..");

    // Only the composition root may hold the proxy. Anywhere else it becomes a service locator, and
    // constructor injection quietly stops being true.
    @ArchTest
    static final ArchRule onlyBootstrapHoldsTheProxy = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.velocitypowered.api.proxy.ProxyServer");

    // Guice reaches the plugin shell and stops there. An @Inject anywhere else means the graph is being
    // built by a container rather than by hand, and a test can no longer build the class with a fake.
    @ArchTest
    static final ArchRule onlyBootstrapIsInjected = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.google.inject..", "jakarta.inject..", "javax.inject..");

    // Legacy colour codes cannot express a gradient, a hover or a click. Everything an operator writes is
    // MiniMessage.
    @ArchTest
    static final ArchRule nothingUsesLegacyChatColour =
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.ChatColor");
}
