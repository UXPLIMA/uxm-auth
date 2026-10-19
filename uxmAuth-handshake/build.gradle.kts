// The login handshake of a lone Paper or Folia server, written against the server's own login classes.
// The Paper API has no hook into the login phase, so this module is compiled against the Mojang-mapped
// server through paperweight-userdev, and nothing else of the plugin is. A Minecraft update that moves a
// login class breaks this module's build, not a player's login. See uxm-briefs/repos/uxm-auth/docs/adr/0006.

plugins {
    id("uxm.java-conventions")
    alias(libs.plugins.paperweight)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal() // uxmLib, while a release is still on its way to the repository
    maven("https://repo.uxplima.com/repository/maven-public/") // uxmLib
}

dependencies {
    // The decision the hello asks for, and nothing of the platform.
    implementation(project(":uxmAuth-common"))
    // The server itself, in Mojang names, for compiling only. The server provides it at runtime.
    paperweight.paperDevBundle(libs.versions.paper.get())
}
