import java.util.zip.ZipFile

// The Velocity side: a proxy ping listener, a login listener, a command, and the wiring.
// It is the same shape as the Paper module, and it shares every rule with it.

plugins {
    id("uxm.java-conventions")
    alias(libs.plugins.shadow)
}

repositories {
    mavenLocal() // uxmLib during development, before the release reaches repo.uxplima.com
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.uxplima.com/repository/maven-public/") // uxmLib, served anonymously
    maven("https://maven.elytrium.net/repo/") {
        content { includeGroup("net.elytrium.limboapi") }
    }
}

dependencies {
    // Velocity provides the proxy API, Adventure, MiniMessage and Brigadier at runtime.
    compileOnly(libs.velocity.api)
    // LimboAPI is an optional link: its classes are touched only once the proxy says the plugin is there.
    // Its own dependencies are the proxy's, so none of them is taken.
    compileOnly(libs.limboapi.api) { isTransitive = false }
    // A world file's signature names an NBT tag; the proxy ships adventure-nbt beside Adventure.
    compileOnly(libs.adventure.nbt)

    implementation(project(":uxmAuth-common"))

    // uxmlib-common only. uxmlib-command builds a Bukkit command tree, and Velocity has a
    // command manager of its own, so the proxy module registers against that instead.
    implementation(libs.uxmlib.common)
    implementation(libs.configurate.hocon)

    testImplementation(libs.velocity.api)
    testImplementation(libs.limboapi.api) { isTransitive = false }
    testImplementation(libs.adventure.nbt)
    testImplementation(libs.fastutil)
    testImplementation(libs.archunit.junit)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    // velocity-plugin.json carries one ${version} token, and the same line filter replaces
    // it. Velocity reads this file and nothing else: the proxy never looks at the @Plugin
    // annotation, which only ever fed the annotation processor that writes this file.
    filesMatching("velocity-plugin.json") {
        filter { line -> line.replace("\${version}", pluginVersion) }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()

    relocate("com.uxplima.uxmlib", "com.uxplima.auth.libs.uxmlib")
    relocate("com.typesafe.config", "com.uxplima.auth.libs.typesafe")
    // Velocity ships Configurate itself, at a version it chose. Relocating our copy keeps
    // the two apart, so a proxy upgrade cannot break this plugin and this plugin cannot
    // pull the proxy onto a different Configurate.
    relocate("org.spongepowered.configurate", "com.uxplima.auth.libs.configurate")
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// Paper owns slf4j, and a second copy of the API with no binding behind it makes logging go
// quiet rather than fail. The other three are annotations that a compiler reads and a runtime
// never loads. geantyref is a real library: Configurate reads a generic type through it, so it
// is relocated rather than dropped.
tasks.shadowJar {
    dependencies {
        exclude(dependency("org.slf4j:.*:.*"))
        exclude(dependency("com.google.errorprone:.*:.*"))
        exclude(dependency("org.checkerframework:.*:.*"))
        exclude(dependency("org.jspecify:.*:.*"))
    }
    relocate("io.leangen.geantyref", "com.uxplima.auth.libs.geantyref")
    // BouncyCastle hashes the passwords. It ships as a signed jar, and a signature that names classes we
    // relocated no longer verifies, so the JVM would refuse the jar: the signature files stay out.
    relocate("org.bouncycastle", "com.uxplima.auth.libs.bouncycastle")
    // Lettuce carries the bulletins on Redis, and brings Netty and Reactor. The server ships a Netty of its own, at a
    // version it chose; relocating ours keeps the two from ever meeting.
    relocate("io.lettuce", "com.uxplima.auth.libs.lettuce")
    relocate("io.netty", "com.uxplima.auth.libs.netty")
    relocate("reactor", "com.uxplima.auth.libs.reactor")
    relocate("org.reactivestreams", "com.uxplima.auth.libs.reactivestreams")
    relocate("redis.clients.authentication", "com.uxplima.auth.libs.redisauth")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}

// Every builder of a plugin audited the jar by hand with unzip, and a hand audit is only correct
// on the day somebody ran it. These are the packages that must never ship inside a plugin jar:
// the server owns them, or they are shaded and must carry our namespace. The SQLite driver is the
// one exception, because the driver derives the path of its native library from its own package.
val forbiddenJarEntries =
    listOf(
        "net/kyori/" to "Paper owns Adventure. A shaded copy breaks the server's own serializers.",
        "org/slf4j/" to "Paper owns slf4j. A second API with no binding makes logging go quiet.",
        "com/uxplima/uxmlib/" to "uxmLib must be relocated. This entry is un-relocated.",
        "org/spongepowered/" to "Configurate must be relocated.",
        "com/typesafe/" to "Typesafe Config must be relocated.",
        "io/leangen/" to "geantyref must be relocated.",
        "org/bouncycastle/" to "BouncyCastle must be relocated, or two plugins that bundle it clash.",
        "io/lettuce/" to "Lettuce must be relocated.",
        "io/netty/" to "Netty must be relocated: the server ships its own, at another version.",
        "reactor/" to "Reactor must be relocated.",
        "org/reactivestreams/" to "Reactive Streams must be relocated.",
        "net/elytrium/" to "LimboAPI is a plugin of its own. A copy inside this jar is a second, broken LimboAPI.",
    )

// What a proxy jar must carry. Velocity ships no JDBC driver, so every database the brief names rides here.
val requiredJarEntries =
    listOf(
        "com/uxplima/auth/libs/lettuce/" to "Lettuce is missing, and a network's bulletins ride it on Redis.",
        "com/uxplima/auth/libs/uxmlib/" to "uxmLib is missing. The jar is not the shaded one.",
        "com/uxplima/auth/velocity/bootstrap/" to "The plugin's own classes are missing.",
        "org/sqlite/JDBC.class" to "The SQLite driver is missing, and it is the default database.",
        "org/mariadb/jdbc/Driver.class" to "The MariaDB driver is missing, and it opens MySQL and MariaDB.",
        "org/postgresql/Driver.class" to "The PostgreSQL driver is missing.",
        "org/h2/Driver.class" to "The H2 driver is missing.",
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
                if (names.none { name -> name.startsWith(prefix) }) {
                    missing.add("  " + prefix + ": " + why)
                }
            }
            if (missing.isNotEmpty()) {
                throw GradleException("The jar is missing packages it must carry:\n" + missing.joinToString("\n"))
            }
        }
    }

tasks.check { dependsOn(verifyJar) }
