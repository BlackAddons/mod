import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * A Minecraft-facing module: `blackaddons.jvm-module` minus tests, plus a Java 25 toolchain and Minecraft's
 * repositories. Which of the two a module applies says whether it may see the game.
 *
 * Loom is applied by the platform convention plugins (`blackaddons.platform`, `blackaddons.cheat-platform`,
 * `blackaddons.extras-platform`), at the version `loom_version` puts on buildSrc's classpath.
 */

plugins {
    kotlin("jvm")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

version = prop("mod_version")

/*
 * Same rule as `blackaddons.jvm-module`. Both platforms are named `26.3`, so they need distinct groups too:
 *
 * :mod:platform:26.3   -> org.blackaddons.mod.platform:26.3
 * :addons:cheat:26.3   -> org.blackaddons.addons.cheat:26.3
 */
group = (listOf(prop("maven_group")) + project.path.removePrefix(":").split(":").dropLast(1))
    .joinToString(".")

// Minecraft 26.3 needs Java 25, the toolchain provisions one if it's missing.
val targetJavaVersion = 25

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

repositories {
    /*
     * First, so a locally published sibling library (BlackSkija, BlackVertex) can be tested without a JitPack
     * release:
     * BlackSkija$ ./gradlew publishToMavenLocal -Pmaven_group=com.github.DreamcastERA1.BlackSkija \
     *                                             -Pmod_version=2.3.1-diag
     * mod$        gradle run-26.2-cheat -Pmc262_blackskija_version=2.3.1-diag
     * Nothing pins a local version, so normal builds are unaffected.
     */
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io") { name = "JitPack" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    // REI's basic-math (me.shedaniel.math.Rectangle) is published only here. >:(
    maven("https://maven.shedaniel.me") {
        name = "Shedaniel"
        content { includeGroup("me.shedaniel.cloth") }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}
