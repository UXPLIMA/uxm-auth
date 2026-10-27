pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.uxplima.com/repository/maven-public/")
    }
    plugins {
        id("uxm.paper-plugin") version "0.0.3"
        id("uxm.java") version "0.0.3"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        maven("https://repo.uxplima.com/repository/maven-public/")
    }
    versionCatalogs {
        create("uxm") { from("com.uxplima.gradle:uxm-catalog:0.0.3") }
    }
}

// The plugin id and the jar name.
rootProject.name = "uxmAuth"

include(
    ":uxmAuth-api",
    ":uxmAuth-common",
    ":uxmAuth-handshake",
    ":uxmAuth-paper",
    ":uxmAuth-velocity",
)
