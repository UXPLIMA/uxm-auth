// Everything that is not a platform. The domain, the use cases, the ports, the hashing, the settings model
// and the catalogue live here, and an ArchUnit fence proves that no Bukkit type and no Velocity type
// reaches them.

plugins {
    id("uxm.java-conventions")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal() // uxmLib, while a release is still on its way to the repository
    maven("https://repo.uxplima.com/repository/maven-public/") // uxmLib
}

dependencies {
    // api, not implementation: HoconConfig, Messages and ConfigurationNode appear in the
    // signatures that a platform module has to name, so they belong on its compile classpath.
    api(libs.uxmlib.common)
    api(libs.configurate.hocon)
    // api: Database is in the signature of the repository a platform module opens.
    api(libs.uxmlib.storage)
    // The drivers of the databases the brief names. uxmLib carries SQLite; the rest ride in both platform jars,
    // because Velocity ships no driver at all and Paper ships only MySQL's and SQLite's.
    runtimeOnly(libs.mariadb.jdbc)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)
    // Argon2id and BCrypt in pure Java. Shaded and relocated by each platform module.
    implementation(libs.bouncycastle)
    // The bulletins of a network travel on Redis when the operator names one. Lettuce brings Netty and Reactor;
    // each platform module relocates all three, so they never meet the copies the server ships.
    implementation(libs.uxmlib.redis)
    implementation(libs.lettuce.core)
    implementation(libs.maxmind.db)

    // Adventure is compileOnly on purpose. Paper and Velocity each ship it, and a shaded
    // copy would break the serializers the platform uses on its own components.
    compileOnly(libs.bundles.adventure)
    // Reads what the premium sources answer. Paper and Velocity each ship it.
    compileOnly(libs.gson)

    testImplementation(libs.bundles.adventure)
    testImplementation(libs.gson)
    testImplementation(libs.archunit.junit)
    // Test only: the catalogue guards ask the command layer which keys it reads, so that a command.*
    // line in a language file is understood as an override of the library's default.
    testImplementation(libs.uxmlib.command)
    testImplementation(libs.uxmlib.integration)
}
