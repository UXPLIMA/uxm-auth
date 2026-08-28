plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // The version-catalog accessor type that the precompiled script uses.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // A plugin that the convention plugin applies with id("...") must be on the
    // buildSrc compile classpath. These versions match gradle/libs.versions.toml.
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.7.0")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
    implementation("net.ltgt.gradle:gradle-nullaway-plugin:3.1.0")
}
