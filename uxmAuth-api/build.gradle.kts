// What another plugin compiles against: the API that answers whether a player is in and how, and the events
// on both platforms. It names no type of the plugin itself, so the plugin can change inside without breaking
// anybody, and both platform jars carry it unrelocated, so a plugin that compiled against it finds it at runtime.

plugins {
    id("uxm.java-conventions")
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Each platform provides its own API at runtime; the events of each are touched only on that platform.
    compileOnly(libs.paper.api)
    compileOnly(libs.velocity.api)

    testImplementation(libs.paper.api)
    testImplementation(libs.velocity.api)
    testImplementation(libs.archunit.junit)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "uxmauth-api"
            from(components["java"])
        }
    }
}

// Every type and every method a reader needs is written; a tag repeating a record component's name is not.
// Doclint keeps every other check: a broken link or a malformed tag still fails the javadoc.
tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}
