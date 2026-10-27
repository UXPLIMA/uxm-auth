plugins {
    id("uxm.java")
}

dependencies {
    api(uxm.uxmlib.common)
    api(project(":uxmAuth-api"))
    api(uxm.configurate.hocon)
    api(uxm.uxmlib.storage)

    // The drivers a database setting can name. SQLite comes with uxmLib's storage module.
    runtimeOnly(libs.mariadb.jdbc)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)

    implementation(libs.bouncycastle)
    implementation(uxm.uxmlib.redis)
    implementation(libs.lettuce.core)
    implementation(libs.maxmind.db)
    implementation(libs.zxing.core)

    // The platforms provide these at runtime.
    compileOnly(libs.bundles.adventure)
    compileOnly(libs.gson)
}
