// Convention plugins: shared build logic as precompiled script plugins, module build files keep only what's specific.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

// buildSrc is its own build, so we read the root gradle.properties directly. The Kotlin version lives only there.
val kotlinVersion: String = file("../gradle.properties").readLines()
    .firstOrNull { it.startsWith("kotlin_version=") }
    ?.substringAfter('=')
    ?.trim()
    ?: error("kotlin_version missing from the root gradle.properties")

val loomVersion: String = file("../gradle.properties").readLines()
    .firstOrNull { it.startsWith("loom_version=") }
    ?.substringAfter('=')
    ?.trim()
    ?: error("loom_version missing from the root gradle.properties")

dependencies {
    // Needed so precompiled script plugins can apply `kotlin("jvm")`.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    // The platform convention plugins apply Loom, so one version serves every Minecraft module.
    implementation("net.fabricmc:fabric-loom:$loomVersion")
}
