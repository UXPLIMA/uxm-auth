// No root `plugins {}` block. Spotless, Error Prone and NullAway reach every
// subproject through the buildSrc convention plugin. Declaring them again here
// with `apply false` makes Gradle 9 fail with "plugin already on classpath".

plugins {
    id("uxm.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

group = "com.uxplima"
version = project.findProperty("projectVersion")?.toString() ?: "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") // uxmLib
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
}

dependencies {
    // Paper provides the server API, Adventure and Brigadier at runtime. Never shade them.
    compileOnly(libs.paper.api)

    // uxmLib is our foundation, and it is shaded into the jar. The plugin must run as a
    // standalone jar, without another plugin on the server. See docs/02-build.md.
    // The bundle is a starting point, not a rule. A plugin with no menu takes the four
    // modules one by one and leaves uxmlib-gui out. See docs/02-build.md.
    implementation(libs.bundles.uxmlib)
    implementation(libs.configurate.hocon)

    // Soft dependencies. Touched only past a plugin-present guard, so the plugin loads
    // on a server that does not have them.
    compileOnly(libs.placeholderapi)

    // Paper is compileOnly for the jar, because the server provides it. A test still has to
    // link against it: a fake that implements a uxmLib port sees Location and Entity in the
    // signatures it has to write out.
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.archunit.junit)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    // paper-plugin.yml carries one ${version} token. A line filter replaces it, rather
    // than expand(), which would also try to read every other ${...} in the file.
    filesMatching("paper-plugin.yml") {
        filter { line -> line.replace("\${version}", pluginVersion) }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")

    // Configurate finds its type serializers through the ServiceLoader, and a JDBC driver
    // registers itself the same way. Without this, the several META-INF/services files of
    // the merged dependencies overwrite each other and the loser goes quiet at runtime
    // rather than failing at build time.
    mergeServiceFiles()

    // Relocate everything we bundle into our own namespace, so that two plugins that
    // shade the same library cannot clash. Never relocate Adventure or Kyori: Paper
    // bundles them, and relocating breaks the server's own serializers.
    relocate("com.uxplima.uxmlib", "com.uxplima.template.libs.uxmlib")
    relocate("com.typesafe.config", "com.uxplima.template.libs.typesafe")
    relocate("org.spongepowered.configurate", "com.uxplima.template.libs.configurate")

    // These three arrive with the libraries above and nothing here names them. They were
    // left in their own namespace by the first version of this list, which is exactly the
    // clash the list exists to prevent. geantyref is the one that matters: Configurate
    // reads a generic type through it at runtime.
    relocate("io.leangen.geantyref", "com.uxplima.template.libs.geantyref")
    relocate("org.checkerframework", "com.uxplima.template.libs.checkerframework")
    relocate("com.google.errorprone.annotations", "com.uxplima.template.libs.errorprone")

    // minimize() is deliberately not enabled. uxmLib reaches Brigadier, the registries
    // and MiniMessage reflectively, and Configurate finds its codecs through the
    // ServiceLoader. The minimizer cannot see either, and it strips what it cannot see.
}

tasks.assemble { dependsOn(tasks.shadowJar) }

tasks.runServer {
    minecraftVersion("1.21.11")
}
