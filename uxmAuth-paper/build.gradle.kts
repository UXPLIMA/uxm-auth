import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.zip.ZipFile

plugins {
    id("uxm.paper-plugin")
}

uxplima {
    slug = "auth"
}

repositories {
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
}

dependencies {
    // Shaded and relocated: an operator installs one jar, and the platform jars share no third file.
    implementation(project(":uxmAuth-common"))
    // The login handshake, compiled against the server and touched only on a server that runs it.
    implementation(project(":uxmAuth-handshake"))
    // The API another plugin compiles against, carried unrelocated so its classes are where that plugin looks.
    implementation(project(":uxmAuth-api"))
    implementation(uxm.uxmlib.common)
    implementation(uxm.uxmlib.command)
    implementation(uxm.uxmlib.item)
    implementation(uxm.uxmlib.gui)
    implementation(uxm.uxmlib.condition)
    implementation(uxm.uxmlib.integration)
    implementation(uxm.uxmlib.bedrock)
    implementation(uxm.uxmlib.menu)
    implementation(uxm.configurate.hocon)
    implementation(uxm.bstats.bukkit)

    // Soft dependencies, touched only past a plugin-present guard.
    compileOnly(uxm.placeholderapi)
    // The server's own Log4j, which the password filter attaches to.
    compileOnly(libs.log4j.core)
}

// Lettuce brings Netty and Reactor. The server ships a Netty of its own, so ours must carry our namespace. The
// handshake, though, reaches into the server's own Netty pipeline and must keep naming io.netty. One relocation of
// io.netty in the plugin jar would rewrite both, so Lettuce and what it brings are relocated here first, in a jar of
// their own, and the plugin jar takes that jar whole.
val redisRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    redisRuntime(libs.lettuce.core)
}

val redisLibraries =
    tasks.register<ShadowJar>("redisLibraries") {
        description = "Lettuce, Netty and Reactor, relocated into the plugin's namespace."
        archiveClassifier = "redis"
        configurations = listOf(redisRuntime)
        mergeServiceFiles()
        relocate("io.lettuce", "com.uxplima.auth.libs.lettuce")
        relocate("io.netty", "com.uxplima.auth.libs.netty")
        relocate("reactor", "com.uxplima.auth.libs.reactor")
        relocate("org.reactivestreams", "com.uxplima.auth.libs.reactivestreams")
        relocate("redis.clients.authentication", "com.uxplima.auth.libs.redisauth")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
        dependencies {
            exclude(dependency("org.slf4j:.*:.*"))
        }
    }

tasks.shadowJar {
    dependsOn(redisLibraries)
    from(redisLibraries.flatMap { libraries -> libraries.archiveFile }.map { file -> zipTree(file) })
    manifest {
        attributes("paperweight-mappings-namespace" to "mojang")
    }
    // BouncyCastle ships signed, and a signature that names relocated classes no longer verifies.
    relocate("org.bouncycastle", "com.uxplima.auth.libs.bouncycastle")
    // Our own classes and uxmLib's name Lettuce as io.lettuce; the copy inside the jar moved already.
    relocate("io.lettuce", "com.uxplima.auth.libs.lettuce")
    relocate("com.maxmind.db", "com.uxplima.auth.libs.maxmind")
    relocate("com.google.zxing", "com.uxplima.auth.libs.zxing")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    dependencies {
        exclude(dependency("io.lettuce:.*:.*"))
        exclude(dependency("io.netty:.*:.*"))
        exclude(dependency("io.projectreactor:.*:.*"))
        exclude(dependency("org.reactivestreams:.*:.*"))
        exclude(dependency("redis.clients.authentication:.*:.*"))
    }
}

// What the shared jar rules do not know: the libraries this plugin bundles beyond uxmLib's set, the JDBC drivers, and
// the handshake's Netty.
val verifyAuthJar =
    tasks.register("verifyAuthJar") {
        description = "Fails when the plugin jar carries an unrelocated library or lacks a driver."
        group = "verification"
        val jar = tasks.shadowJar.flatMap { shadow -> shadow.archiveFile }
        dependsOn(tasks.shadowJar)
        inputs.file(jar)
        doLast {
            val forbidden =
                listOf(
                    "org/bouncycastle/",
                    "io/lettuce/",
                    "io/netty/",
                    "reactor/",
                    "org/reactivestreams/",
                    "com/maxmind/",
                    "com/google/zxing/",
                )
            val required =
                listOf(
                    "com/uxplima/auth/libs/lettuce/",
                    "com/uxplima/auth/libs/maxmind/",
                    "com/uxplima/auth/libs/zxing/",
                    "com/uxplima/auth/api/AuthApi.class",
                    "org/sqlite/JDBC.class",
                    "org/mariadb/jdbc/Driver.class",
                    "org/postgresql/Driver.class",
                    "org/h2/Driver.class",
                )
            val problems = mutableListOf<String>()
            var serverNetty = 0
            ZipFile(jar.get().asFile).use { archive ->
                val names = archive.entries().toList().map { entry -> entry.name }
                forbidden
                    .filter { prefix -> names.any { name -> name.startsWith(prefix) } }
                    .forEach { prefix -> problems.add("$prefix must be relocated") }
                required
                    .filter { prefix -> names.none { name -> name.startsWith(prefix) } }
                    .forEach { prefix -> problems.add("$prefix is missing") }
                // The handshake names the server's io.netty, never the relocated copy: the server refuses a channel
                // listener whose types it does not know.
                for (entry in archive.entries().toList()) {
                    if (!entry.name.startsWith("com/uxplima/auth/handshake/") || !entry.name.endsWith(".class")) {
                        continue
                    }
                    val text = archive.getInputStream(entry).use { it.readBytes() }.toString(Charsets.ISO_8859_1)
                    if (text.contains("com/uxplima/auth/libs/netty/")) {
                        problems.add("${entry.name} names the relocated Netty")
                    }
                    if (text.contains("io/netty/channel/")) {
                        serverNetty++
                    }
                }
            }
            if (serverNetty == 0) {
                problems.add("the handshake does not name the server's io.netty")
            }
            if (problems.isNotEmpty()) {
                throw GradleException("The plugin jar is wrong:\n" + problems.joinToString("\n") { "  $it" })
            }
        }
    }

tasks.check { dependsOn(verifyAuthJar) }
