package com.uxplima.auth.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The fences of the common module. Each rule states a boundary that the plugin must keep, and it fails the
 * build the moment somebody crosses it. A convention that nothing enforces is a convention that erodes.
 *
 * <p>The first rule is the one that makes this module worth having. The moment a Bukkit type or a Velocity
 * type reaches in here, one of the two platforms stops being able to load the class, and the plugin has
 * silently become two plugins that share a name.
 */
@AnalyzeClasses(
        packages = "com.uxplima.auth",
        importOptions = {ImportOption.DoNotIncludeTests.class, ArchitectureTest.NotTheApi.class})
final class ArchitectureTest {

    /**
     * The API module carries each platform's events on purpose and has its own fence. What is held here is that
     * the common module reaches none of them, which the rule after the first says.
     */
    static final class NotTheApi implements ImportOption {
        @Override
        public boolean includes(com.tngtech.archunit.core.importer.Location location) {
            return !location.contains("com/uxplima/auth/api/");
        }
    }

    // The whole point of the module. Paper and Velocity have almost nothing in common except Adventure, so
    // anything here that names one of them cannot run on the other.
    @ArchTest
    static final ArchRule commonKnowsNoPlatform = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.bukkit..", "io.papermc..", "com.destroystokyo..", "com.velocitypowered..", "com.mojang..");

    // The API's platform events are each platform's to fire; the common module answers the API, never an event.
    @ArchTest
    static final ArchRule commonFiresNoPlatformEvent = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.uxplima.auth.api.paper..", "com.uxplima.auth.api.velocity..");

    // The domain models the problem, not the server. Keeping the text library and the logger out of it is
    // what lets a plain JUnit test drive the rules with no server and no mock.
    @ArchTest
    static final ArchRule domainHasNoLibraries = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.kyori..", "org.slf4j..", "org.spongepowered..", "com.typesafe..");

    // The application layer orchestrates the domain through ports it declares itself. It renders nothing
    // and it reads no file, so it needs neither a text library nor a configuration library.
    @ArchTest
    static final ArchRule applicationHasNoLibraries = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.kyori..", "org.slf4j..", "org.spongepowered..", "com.typesafe..");

    // An adapter may name the layers inside it. Neither of those layers may name an adapter, or the
    // dependency that hexagonal architecture exists to invert has quietly been put back.
    @ArchTest
    static final ArchRule domainDoesNotKnowTheOutside = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..adapter..", "..application..");

    @ArchTest
    static final ArchRule applicationDoesNotKnowTheAdapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");

    // Legacy colour codes cannot express a gradient, a hover or a click, and they leak into the message
    // catalog where an operator then has two syntaxes to learn. Everything an operator writes is MiniMessage.
    @ArchTest
    static final ArchRule nothingUsesLegacyChatColour =
            noClasses().should().dependOnClassesThat().resideInAPackage("net.kyori.adventure.text.serializer.legacy..");
}
