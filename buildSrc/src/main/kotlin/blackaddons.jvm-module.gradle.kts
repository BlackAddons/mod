import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * A version-neutral, pure-JVM module: no Loom, no Minecraft, so `import net.minecraft.*` is a compile error. Holds what
 * every such module shares (toolchain, encoding, tests).
 */

plugins {
    kotlin("jvm")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

version = prop("mod_version")

/*
 * Gradle identifies a project as `group:name:version`, and `:mod:core`, `:addons:cheat:core` and
 * `:addons:extras:core` are all named `core`. With one group they'd collide (a module compiling against its
 * own jar), so the path goes into the group:
 *
 * :api                 -> org.blackaddons:api
 * :mod:core            -> org.blackaddons.mod:core
 * :addons:cheat:core   -> org.blackaddons.addons.cheat:core
 */
group = (listOf(prop("maven_group")) + project.path.removePrefix(":").split(":").dropLast(1))
    .joinToString(".")

// Matches the platform's Java version (Minecraft 26.3 needs 25). An older platform would need this lowered.
val targetJavaVersion = 25

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    /*
     * Types inferred from the JDK and Minecraft APIs carry these annotations. Without them the IDE reports "type
     * annotation class ... is inaccessible".
     */
    "compileOnly"("org.jetbrains:annotations:${prop("annotations_version")}")

    "testImplementation"(platform("org.junit:junit-bom:${prop("junit_version")}"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// Minecraft-provided libraries are compileOnly, which leaves them off the test classpath. Tests run outside Minecraft.
configurations.named("testImplementation") {
    extendsFrom(configurations.named("compileOnly").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
    compilerOptions.allWarningsAsErrors.set(providers.environmentVariable("CI").isPresent)
}

/*
 * The team's private tests live in a sibling checkout, BlackAddons.ModTests, mirroring this tree. When it's there they
 * join this module's test source set, so they see `internal` code and the public test helpers. A public clone just has
 * none.
 */
val privateTests = rootDir.resolveSibling("BlackAddons.ModTests")
    .resolve(projectDir.relativeTo(rootDir).invariantSeparatorsPath)
    .resolve("src/test/kotlin")
if (privateTests.isDirectory) {
    kotlin.sourceSets.named("test") { kotlin.srcDir(privateTests) }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// No publishing here: modules are project dependencies, and only `:api` publishes (for addon authors).
