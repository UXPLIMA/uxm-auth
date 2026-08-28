// Re-import the root version catalog, so that `libs.*` resolves inside the
// precompiled script plugin. Without this, `the<LibrariesForLibs>()` is unresolved.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
