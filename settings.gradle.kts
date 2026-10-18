pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "uxmAuth"

// Three modules, because a lone server and a network decide who a player is in two different places.
// The common module holds every rule and every use case; a platform module holds only the translation
// between a platform event and a use-case call. See docs/adr/0003. A fourth holds the one part of a lone
// server that the Paper API cannot reach, the login handshake, so a Minecraft update touches it alone.
// See docs/adr/0006.
include(
    ":uxmAuth-api",
    ":uxmAuth-common",
    ":uxmAuth-handshake",
    ":uxmAuth-paper",
    ":uxmAuth-velocity",
)
