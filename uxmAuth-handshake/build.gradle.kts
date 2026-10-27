plugins {
    id("uxm.java")
    alias(libs.plugins.paperweight)
}

dependencies {
    implementation(project(":uxmAuth-common"))
    paperweight.paperDevBundle(uxm.versions.paper.get())
}
