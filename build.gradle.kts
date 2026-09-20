import java.util.zip.ZipFile

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
    mavenLocal() // uxmLib, while a release is still on its way to JitPack
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
    // bStats, so the estate can see how many servers run this and on what. The operator opts out in
    // plugins/bStats/config.yml, which is the switch every bStats plugin shares.
    implementation(libs.bstats.bukkit)

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
    relocate("org.bstats", "com.uxplima.template.libs.bstats")
    relocate("com.typesafe.config", "com.uxplima.template.libs.typesafe")
    relocate("org.spongepowered.configurate", "com.uxplima.template.libs.configurate")

    // geantyref arrives with Configurate, which reads a generic type through it at runtime,
    // so it is a real library and it is relocated like the rest.
    relocate("io.leangen.geantyref", "com.uxplima.template.libs.geantyref")

    dependencies {
        // Paper already has slf4j, and a second copy of the API with no binding behind it makes
        // logging go quiet rather than fail. Take the server's.
        exclude(dependency("org.slf4j:.*:.*"))
        // Annotations that a compiler reads and a runtime never loads. They arrive with the
        // libraries above and only add weight.
        exclude(dependency("com.google.errorprone:.*:.*"))
        exclude(dependency("org.checkerframework:.*:.*"))
        exclude(dependency("org.jspecify:.*:.*"))
    }

    // minimize() is deliberately not enabled. uxmLib reaches Brigadier, the registries
    // and MiniMessage reflectively, and Configurate finds its codecs through the
    // ServiceLoader. The minimizer cannot see either, and it strips what it cannot see.
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// The plain jar takes a classifier of its own. Both tasks used to write
// build/libs/<name>.jar, so whichever ran last won, and a build that happened to end with
// the plain jar shipped a small file with no library in it. The server then loaded the
// plugin and failed at the first uxmLib class. Found on the first install of uxmBackup.
tasks.jar { archiveClassifier.set("thin") }

// Every builder of a plugin audited the jar by hand with unzip, and a hand audit is only
// correct on the day somebody ran it. These are the packages that must never ship inside a
// plugin jar: the server owns them, or they are shaded and must carry our namespace. The
// SQLite driver is the one exception, and it is named as such.
val forbiddenJarEntries =
    listOf(
        "net/kyori/" to "Paper owns Adventure. A shaded copy breaks the server's own serializers.",
        "org/slf4j/" to "Paper owns slf4j. A second API with no binding makes logging go quiet.",
        "com/uxplima/uxmlib/" to "uxmLib must be relocated. This entry is un-relocated.",
        "org/spongepowered/" to "Configurate must be relocated.",
        "com/typesafe/" to "Typesafe Config must be relocated.",
        "io/leangen/" to "geantyref must be relocated.",
        "org/bstats/" to "bstats must be relocated when it is taken at all.",
    )

// What the jar must carry. A plugin that runs standalone holds a relocated copy of the
// library, and a jar without one loads and dies at its first uxmLib class. The forbidden
// list alone cannot see this: a jar that carries nothing passes it.
val requiredJarEntries =
    listOf(
        "com/uxplima/template/libs/uxmlib/" to "uxmLib is missing. The jar is not the shaded one.",
        "com/uxplima/template/bootstrap/" to "The plugin's own classes are missing.",
    )

val verifyJar =
    tasks.register("verifyJar") {
        description = "Fail the build when the shaded jar carries a package it must not."
        group = "verification"
        // shadowJar writes over the plain jar, because its classifier is empty, so Gradle sees the
        // file as the output of both tasks and refuses to guess which one has to run first.
        dependsOn(tasks.shadowJar, tasks.jar)
        val jar = tasks.shadowJar.flatMap { shadow -> shadow.archiveFile }
        inputs.file(jar)
        doLast {
            val names = mutableListOf<String>()
            ZipFile(jar.get().asFile).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    names.add(entries.nextElement().name)
                }
            }
            val found = mutableListOf<String>()
            for ((prefix, why) in forbiddenJarEntries) {
                var count = 0
                for (name in names) {
                    if (name.startsWith(prefix)) {
                        count++
                    }
                }
                if (count > 0) {
                    found.add("  " + prefix + " (" + count + " entries): " + why)
                }
            }
            if (found.isNotEmpty()) {
                throw GradleException("The jar carries packages it must not:\n" + found.joinToString("\n"))
            }
            val missing = mutableListOf<String>()
            for ((prefix, why) in requiredJarEntries) {
                var count = 0
                for (name in names) {
                    if (name.startsWith(prefix)) {
                        count++
                    }
                }
                if (count == 0) {
                    missing.add("  " + prefix + ": " + why)
                }
            }
            if (missing.isNotEmpty()) {
                throw GradleException("The jar is missing packages it must carry:\n" + missing.joinToString("\n"))
            }
        }
    }

tasks.check { dependsOn(verifyJar) }

tasks.runServer {
    minecraftVersion("1.21.11")
}
