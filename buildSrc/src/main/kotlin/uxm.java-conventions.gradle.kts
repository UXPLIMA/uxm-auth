import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    id("java-library")
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

// Mockito 5 attaches its inline mock maker to the running JVM by itself. The JDK already
// answers that with "Dynamic loading of agents will be disallowed by default in a future
// release", and a later JDK will refuse it. The agent is passed on the command line here,
// so the warning goes away and the tests keep working when the refusal arrives.
val mockitoAgent: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencies {
    "compileOnly"(libs.jspecify)
    "testCompileOnly"(libs.jspecify)
    // @Retention(CLASS) markers that the compiler reads and nothing needs at runtime.
    "compileOnly"(libs.errorprone.annos)
    "testCompileOnly"(libs.errorprone.annos)
    "errorprone"(libs.errorprone.core)
    "errorprone"(libs.nullaway)

    "testImplementation"(platform(libs.junit.bom))
    "testImplementation"(libs.bundles.testing)
    // Gradle 9 no longer puts junit-platform-launcher on the test runtime classpath.
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = libs.versions.java.get().toInt()
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-processing",
            "-Xlint:-serial",
            // JDK 23+ flags a /** */ comment that is used as a section marker rather
            // than attached to a declaration. That is style, not correctness.
            "-Xlint:-dangling-doc-comments",
            "-Werror",
            "-parameters",
        ),
    )
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        // Would rewrite a deliberate descriptive unused-catch-variable name to `_`.
        disable("UnnamedVariable")
        // Style checks that Error Prone 2.50 turned on by default. They fire on correct
        // code. Correctness checks stay on.
        disable("RefactorSwitch")
        disable("ReferenceEquality")
        disable("EffectivelyPrivate")
    }
}

extensions.configure<net.ltgt.gradle.nullaway.NullAwayExtension> {
    onlyNullMarked.set(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.nullaway {
        severity.set(net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        palantirJavaFormat(libs.versions.palantir.fmt.get())
        removeUnusedImports()
        formatAnnotations()
        importOrder("java", "javax", "org.bukkit", "io.papermc", "net.kyori", "")
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
    kotlinGradle { ktlint("1.5.0") }
}

// A skipped test protects nothing, and it reports as green. MockBukkit turns an unimplemented
// mock into a TestAbortedException, which JUnit records as a skip, so a guard can stop running
// and nobody is told. There is no legitimate skip in this repository, so a skip fails the build.
val verifyNoSkippedTests =
    tasks.register("verifyNoSkippedTests") {
        description = "Fail the build when a test was skipped instead of run."
        group = "verification"
        dependsOn(tasks.named("test"))
        val results = layout.buildDirectory.dir("test-results/test")
        doLast {
            val folder = results.get().asFile
            if (!folder.isDirectory) {
                return@doLast
            }
            val skippedSuites = mutableListOf<String>()
            val counter = Regex("""skipped="(\d+)"""")
            for (file in folder.listFiles().orEmpty()) {
                if (!file.name.startsWith("TEST-") || !file.name.endsWith(".xml")) {
                    continue
                }
                // The first ">" in the file closes the XML declaration, not the testsuite tag.
                val head = file.readText().substringAfter("<testsuite").substringBefore(">")
                val skipped = counter.find(head)?.groupValues?.get(1)?.toInt() ?: 0
                if (skipped > 0) {
                    skippedSuites.add("  " + file.name.removePrefix("TEST-").removeSuffix(".xml") + ": " + skipped)
                }
            }
            if (skippedSuites.isNotEmpty()) {
                throw GradleException(
                    "A test was skipped rather than run, so it guards nothing:\n" +
                        skippedSuites.joinToString("\n"),
                )
            }
        }
    }

tasks.named("check") { dependsOn(verifyNoSkippedTests) }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:" + mockitoAgent.asPath) })
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    // A test enables the plugin with bStats unshaded, and bStats refuses an unshaded copy so that two
    // plugins never share one. The jar still relocates it and a real server still runs the check.
    systemProperty("bstats.relocatecheck", "false")
    // Pinned, because an unset max heap is a quarter of the host RAM. A CI runner then
    // gets far less than a workstation, and the parallel MockBukkit servers run out.
    maxHeapSize = "2g"
    // A guard reads files the compiler never turns into a class: the text of the sources, comments
    // included, the documents, and the version catalogue. Gradle reruns a test only when one of its
    // inputs changes, and none of those were inputs, so a change to a comment or a document left the
    // test up to date and the build green without running the guards that read it. Proved in uxmGlow
    // on 2026-09-22: a comment naming ActionContext.builder( broke three guards and the build passed.
    // The whole repository's text is declared, because a guard in one module may read another.
    inputs.files(
            rootProject.fileTree(rootProject.projectDir) {
                include("**/src/main/java/**", "**/src/test/java/**", "docs/**", "gradle/libs.versions.toml")
                exclude("**/build/**", ".gradle/**", "buildSrc/**")
            })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("guardedText")
}

// Taken from uxmLib, where it was written first, on 2026-09-22.
// MockBukkit answers a method it has not implemented with UnimplementedOperationException, which extends
// JUnit's TestAbortedException. A test that reaches one is therefore recorded as skipped rather than failed:
// the build stays green, and every assertion after that call goes unrun without anybody being told. A skip
// somebody wrote on purpose is a decision and stays allowed, an @Disabled with a reason or an integration
// test with nothing to connect to; this one is a test that quietly stopped testing, so it fails the build.
val verifyNoAbortedTests =
    tasks.register("verifyNoAbortedTests") {
        description = "Fails when a test was aborted by a MockBukkit method that is not implemented."
        val results = layout.buildDirectory.dir("test-results/test")
        doLast {
            val abortedCase =
                Regex(
                    "<testcase name=\"([^\"]*)\" classname=\"([^\"]*)\"[^>]*>\\s*" +
                        "<skipped[^>]*type=\"org\\.mockbukkit\\.mockbukkit\\.exception\\." +
                        "UnimplementedOperationException"
                )
            val files = results.get().asFile.listFiles { file -> file.name.endsWith(".xml") } ?: emptyArray()
            val aborted =
                files.flatMap { file ->
                    abortedCase.findAll(file.readText()).map { "${it.groupValues[2]}.${it.groupValues[1]}" }
                }
                    .sorted()
            if (aborted.isNotEmpty()) {
                throw GradleException(
                    aborted.joinToString(
                        prefix = "Aborted by an unimplemented MockBukkit method, so nothing the test " +
                            "claims to check was checked:\n",
                        separator = "\n",
                        postfix = "\nAssert against what the mock implements, or mark the test @Disabled " +
                            "with the reason, so the gap is one a reader can see.",
                    ) { "  $it" }
                )
            }
        }
    }

tasks.named<Test>("test") { finalizedBy(verifyNoAbortedTests) }
