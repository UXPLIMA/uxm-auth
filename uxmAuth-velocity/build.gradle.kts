import java.util.zip.ZipFile

plugins {
    id("uxm.java")
    id("com.gradleup.shadow")
}

repositories {
    maven("https://maven.elytrium.net/repo/") {
        content { includeGroup("net.elytrium.limboapi") }
    }
}

dependencies {
    // Velocity provides the proxy API, Adventure, MiniMessage and Brigadier at runtime.
    compileOnly(libs.velocity.api)
    // LimboAPI is a plugin of its own, reached only once the proxy says it is there. Its own dependencies are the
    // proxy's, so none of them is taken.
    compileOnly(libs.limboapi.api) { isTransitive = false }
    compileOnly(libs.adventure.nbt)

    implementation(project(":uxmAuth-common"))
    implementation(project(":uxmAuth-api"))
    implementation(uxm.uxmlib.common)
    implementation(uxm.configurate.hocon)
}

// The waiting world exists for two LimboAPI lines that cannot share a classpath: 2 for the main source set and
// limboTwo, 1.1 for limboOne. Both end up in the one jar and the plugin picks the line the proxy runs.
val mainSet = sourceSets.main.get()
val limboTwo =
    sourceSets.create("limboTwo") {
        compileClasspath += mainSet.output + mainSet.compileClasspath
    }
val limboOne =
    sourceSets.create("limboOne") {
        compileClasspath += mainSet.output
    }

dependencies {
    "limboOneCompileOnly"(project(":uxmAuth-common"))
    "limboOneCompileOnly"(libs.velocity.api)
    "limboOneCompileOnly"(libs.limboapi.one.api) { isTransitive = false }
    "limboOneCompileOnly"(libs.adventure.nbt)
    "limboOneCompileOnly"(uxm.jspecify)
    "limboOneCompileOnly"(uxm.errorprone.annotations)
}

tasks.jar {
    archiveClassifier = "thin"
    from(limboOne.output, limboTwo.output)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("velocity-plugin.json") {
        filter { line -> line.replace("\${version}", pluginVersion) }
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    from(limboOne.output, limboTwo.output)
    mergeServiceFiles()

    relocate("com.uxplima.uxmlib", "com.uxplima.auth.libs.uxmlib")
    relocate("com.typesafe.config", "com.uxplima.auth.libs.typesafe")
    relocate("org.spongepowered.configurate", "com.uxplima.auth.libs.configurate")
    relocate("io.leangen.geantyref", "com.uxplima.auth.libs.geantyref")
    // BouncyCastle ships signed, and a signature that names relocated classes no longer verifies.
    relocate("org.bouncycastle", "com.uxplima.auth.libs.bouncycastle")
    relocate("io.lettuce", "com.uxplima.auth.libs.lettuce")
    relocate("io.netty", "com.uxplima.auth.libs.netty")
    relocate("reactor", "com.uxplima.auth.libs.reactor")
    relocate("org.reactivestreams", "com.uxplima.auth.libs.reactivestreams")
    relocate("redis.clients.authentication", "com.uxplima.auth.libs.redisauth")
    relocate("com.zaxxer.hikari", "com.uxplima.auth.libs.hikari")
    relocate("com.github.benmanes.caffeine", "com.uxplima.auth.libs.caffeine")
    relocate("com.maxmind.db", "com.uxplima.auth.libs.maxmind")
    relocate("com.google.zxing", "com.uxplima.auth.libs.zxing")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    dependencies {
        exclude(dependency("org.slf4j:.*:.*"))
        exclude(dependency("net.kyori:.*:.*"))
        exclude(dependency("com.google.errorprone:.*:.*"))
        exclude(dependency("org.checkerframework:.*:.*"))
        exclude(dependency("org.jspecify:.*:.*"))
    }
}

tasks.assemble { dependsOn(tasks.shadowJar) }

val verifyJar =
    tasks.register("verifyJar") {
        description = "Fails when the plugin jar carries an unrelocated library or lacks a part."
        group = "verification"
        val jar = tasks.shadowJar.flatMap { shadow -> shadow.archiveFile }
        dependsOn(tasks.shadowJar)
        inputs.file(jar)
        doLast {
            val forbidden =
                listOf(
                    "net/kyori/",
                    "org/slf4j/",
                    "com/uxplima/uxmlib/",
                    "org/spongepowered/",
                    "com/typesafe/",
                    "io/leangen/",
                    "org/bouncycastle/",
                    "io/lettuce/",
                    "io/netty/",
                    "reactor/",
                    "org/reactivestreams/",
                    "com/zaxxer/hikari/",
                    "com/github/benmanes/caffeine/",
                    "com/maxmind/",
                    "com/google/zxing/",
                    "net/elytrium/",
                )
            val required =
                listOf(
                    "com/uxplima/auth/libs/lettuce/",
                    "com/uxplima/auth/libs/maxmind/",
                    "com/uxplima/auth/libs/zxing/",
                    "com/uxplima/auth/libs/uxmlib/",
                    "com/uxplima/auth/velocity/bootstrap/",
                    "com/uxplima/auth/velocity/adapter/outbound/limbo/one/LimboOne.class",
                    "com/uxplima/auth/velocity/adapter/outbound/limbo/two/LimboTwo.class",
                    "com/uxplima/auth/api/AuthApi.class",
                    "org/sqlite/JDBC.class",
                    "org/mariadb/jdbc/Driver.class",
                    "org/postgresql/Driver.class",
                    "org/h2/Driver.class",
                )
            val names = ZipFile(jar.get().asFile).use { archive -> archive.entries().toList().map { entry -> entry.name } }
            val problems = mutableListOf<String>()
            forbidden
                .filter { prefix -> names.any { name -> name.startsWith(prefix) } }
                .forEach { prefix -> problems.add("$prefix must not be in the jar, or must be relocated") }
            required
                .filter { prefix -> names.none { name -> name.startsWith(prefix) } }
                .forEach { prefix -> problems.add("$prefix is missing") }
            if (problems.isNotEmpty()) {
                throw GradleException("The plugin jar is wrong:\n" + problems.joinToString("\n") { "  $it" })
            }
        }
    }

tasks.check { dependsOn(verifyJar) }
