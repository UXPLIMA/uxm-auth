// Declared here so every module resolves the plugins the conventions bring, Shadow among them, without a version.
plugins {
    id("uxm.java") apply false
}

// The root project builds nothing: the modules carry the plugins. It holds the group and the one version they share.
allprojects {
    group = "com.uxplima"
    version = providers.gradleProperty("projectVersion").get()
}
