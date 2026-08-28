pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The plugin id. scripts/new-plugin.sh rewrites it, along with the package root
// and the jar name, when the template becomes a real plugin.
rootProject.name = "uxmTemplate"
