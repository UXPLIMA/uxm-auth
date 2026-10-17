// The root project builds nothing. It carries the group and the version so that the three
// modules read one number rather than three.
//
// There is no root `plugins {}` block. Spotless, Error Prone and NullAway reach every
// module through the buildSrc convention plugin, and declaring one of them here again,
// even with `apply false`, makes Gradle 9 fail with "plugin already on classpath". Shadow
// and run-paper are applied in the module that needs them, which is not every module.

allprojects {
    group = "com.uxplima"
    version = project.findProperty("projectVersion")?.toString() ?: "0.1.0-SNAPSHOT"
}
