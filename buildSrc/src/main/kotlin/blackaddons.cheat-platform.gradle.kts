/**
 * An `:addons:cheat:<version>` module: cheat code that touches `net.minecraft.*`, the rest is in :addons:cheat:core.
 * Ships no jar of its own, its classes go into :mod:platform:<version>'s `cheatJar`. Its dev-only fabric.mod.json lets
 * `runClient` load CheatAddon like a third-party addon. The folder name is the Minecraft version.
 */

plugins {
    id("blackaddons.mc-module")
    id("net.fabricmc.fabric-loom")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

val mc = project.name
fun mcProp(name: String): String = prop("mc${mc.replace(".", "")}_$name")

val minecraftVersion = mcProp("minecraft_version")
val loaderVersion = mcProp("loader_version")
val kotlinLoaderVersion = mcProp("kotlin_loader_version")
val fabricVersion = mcProp("fabric_version")
val blackskijaVersion = mcProp("blackskija_version")
val iasVersion = mcProp("ias_version")
val modmenuVersion = mcProp("modmenu_version")
val skijaVersion = mcProp("skija_version")

base {
    archivesName = "blackaddons-cheat-$mc"
}

dependencies {
    implementation(project(":mod:platform:$mc"))
    implementation(project(":api"))
    implementation(project(":mod:core"))
    implementation(project(":addons:cheat:core"))

    // Unobfuscated: no `mappings(...)`, Fabric via plain `implementation(...)`.
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    // The platform's runtime deps don't come through the project dependency.
    runtimeOnly("com.github.DreamcastERA1.BlackSkija:blackskija-$mc:$blackskijaVersion")
    runtimeOnly("io.github.classgraph:classgraph:${prop("classgraph_version")}")

    runtimeOnly("maven.modrinth:in-game-account-switcher:$iasVersion")
    runtimeOnly("maven.modrinth:modmenu:$modmenuVersion")

    runtimeOnly("io.github.humbleui:skija-windows-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-linux-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-arm64:$skijaVersion")

    // Extras runs alongside in dev. Auth is a separate repo, just drop its jar into run/mods.
    runtimeOnly(project(":addons:extras:$mc"))
}

/*
 * Dev-only manifest. The shipped jar derives it from the legit one (`generateCheatManifest` in
 * :mod:platform:<version>).
 */
tasks.processResources {
    val projectVersion = project.version.toString()

    inputs.property("version", projectVersion)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to projectVersion,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
            "kotlin_loader_version" to kotlinLoaderVersion,
        )
    }

    from(rootProject.file("mod/platform/shared/src/main/resources/assets/blackaddons/icon.png")) {
        into("assets/blackaddons-cheat")
    }
}
