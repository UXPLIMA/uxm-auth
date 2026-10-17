package com.uxplima.auth.paper.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The fences. Each rule states a boundary that the plugin must keep, and it fails the build the moment
 * somebody crosses it. A convention that nothing enforces is a convention that erodes.
 */
@AnalyzeClasses(packages = "com.uxplima.auth", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    /**
     * Folia-safety: nothing moves an entity in place. Folia refuses {@code Entity.teleport} for every entity, a
     * player, an armour stand and a display alike, on its own region's thread too, with "Must use teleportAsync while
     * in region threading": proved on a Folia 26.2 server on 2026-09-23. An entity is moved with teleportAsync.
     */
    @ArchTest
    static final ArchRule noEntityIsTeleportedInPlace = noClasses()
            .should()
            .callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                            com.tngtech.archunit.core.domain.properties.HasName.Predicates.name("teleport"))
                    .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                            com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                    com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameStartingWith(
                                            "org.bukkit.entity.")))))
            .because("Folia refuses Entity.teleport; an entity is moved with teleportAsync");

    // The domain models the problem, not the server. Keeping Bukkit, Paper and Adventure out of it is what
    // lets a plain JUnit test drive the rules with no server and no mock.
    @ArchTest
    static final ArchRule domainHasNoServerTypes = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.kyori..", "org.slf4j..");

    // The application layer orchestrates the domain through ports. It translates nothing, so it needs no
    // server type either. An adapter is the only place that knows Bukkit exists.
    @ArchTest
    static final ArchRule applicationHasNoServerTypes = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.kyori..", "org.slf4j..");

    // Only the composition root may hold the JavaPlugin. Anywhere else it becomes a service locator, and
    // constructor injection quietly stops being true.
    @ArchTest
    static final ArchRule onlyBootstrapHoldsTheJavaPlugin = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.plugin.java.JavaPlugin");

    // BukkitScheduler is single-threaded-main-thread by design, and Folia has no such thread. Every
    // schedule goes through the uxmLib Scheduler port, which maps onto the right one.
    @ArchTest
    static final ArchRule nothingUsesBukkitScheduler =
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler");

    // A thread that the plugin starts itself is invisible to the server, and it outlives a reload. The same
    // is true of BukkitRunnable, which is the old scheduler in a different shape.
    @ArchTest
    static final ArchRule nothingStartsItsOwnThread =
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitRunnable");

    // Brigadier gives typed arguments, real tab completion and a usage line for free. The legacy handlers
    // give none of that, and a plugin that mixes both ends up with two different argument grammars.
    @ArchTest
    static final ArchRule nothingImplementsLegacyCommandHandlers = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.command.CommandExecutor")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.command.TabCompleter");

    // Legacy colour codes cannot express a gradient, a hover or a click, and they leak into the message
    // catalog where an operator then has two syntaxes to learn. Everything is MiniMessage.
    @ArchTest
    static final ArchRule nothingUsesLegacyChatColour =
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.ChatColor");
}
