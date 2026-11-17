import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.util.zip.ZipFile

/**
 * A `:mod:platform:<version>` module: the Minecraft integration (mixins, facades, Skija renderer) and both edition
 * jars. The folder name is the Minecraft version and picks the `mc<version>_*` properties, so a platform's own build
 * file only applies this.
 */

plugins {
    id("blackaddons.mc-module")
    id("blackaddons.host-module")
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
val blackvertexVersion = mcProp("blackvertex_version")
val iasVersion = mcProp("ias_version")
val modmenuVersion = mcProp("modmenu_version")
val irisVersion = mcProp("iris_version")
val sodiumVersion = mcProp("sodium_version")
val skijaVersion = mcProp("skija_version")

base {
    archivesName = mcProp("archives_base_name")
}

/*
 * mod/platform/shared: Kotlin, Java mixins and resources that compile on every platform against that platform's own
 * Minecraft. Only `compat`, render layers and four moved-target mixins stay here.
 */
kotlin {
    sourceSets.named("main") {
        kotlin.srcDir("../shared/src/main/kotlin")
    }
}

sourceSets.named("main") {
    java.srcDir("../shared/src/main/java")
    resources.srcDir("../shared/src/main/resources")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":mod:core"))

    // Deobfuscated: no `mappings(...)`, Fabric via plain `implementation(...)`.
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    /*
     * BlackSkija (https://jitpack.io/#DreamcastERA1/BlackSkija), nested (JiJ) so users don't have to install it. It
     * fetches the Skija native itself. Every supported version's blackskija has Vulkan and OpenGL.
     */
    implementation("com.github.DreamcastERA1.BlackSkija:blackskija-$mc:$blackskijaVersion")
    include("com.github.DreamcastERA1.BlackSkija:blackskija-$mc:$blackskijaVersion")

    // BlackVertex (3D cosmetics) registers its render layer at init, so it's a nested mod dependency like BlackSkija.
    implementation("com.github.DreamcastERA1.BlackVertex:blackvertex-$mc:$blackvertexVersion")
    include("com.github.DreamcastERA1.BlackVertex:blackvertex-$mc:$blackvertexVersion")

    runtimeOnly("io.github.humbleui:skija-windows-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-linux-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-arm64:$skijaVersion")

    // Bundled for FeatureManager discovery.
    implementation("io.github.classgraph:classgraph:${prop("classgraph_version")}")
    /* ClassGraph 6 ships as four jars, and include doesn't pull in dependencies. */
    for (module in listOf("classgraph", "classgraph-classpath", "classgraph-vfs", "classgraph-base"))
        include("io.github.classgraph:$module:${prop("classgraph_version")}")

    // Dev-only mods.
    runtimeOnly("maven.modrinth:in-game-account-switcher:$iasVersion")
    runtimeOnly("maven.modrinth:modmenu:$modmenuVersion")

    compileOnly("maven.modrinth:modmenu:$modmenuVersion")
    // Optional pipeline registration API. Iris and Sodium are still on the user to install.
    compileOnly("maven.modrinth:iris:$irisVersion") { isTransitive = false }

    runtimeOnly("io.github.humbleui:skija-windows-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-linux-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-arm64:$skijaVersion")
    compileOnly("maven.modrinth:sodium:$sodiumVersion") { isTransitive = false }
}

/*
 * Loom only ships this module's classes, so we bundle the version-neutral ones (otherwise ClassNotFoundException).
 * Resolved as jars, not source sets: :addons:cheat:<version> compiles against this module, so configuring it from here
 * would be circular. `isTransitive = false` keeps slf4j/gson/lwjgl out, the attributes pick the runtime variant.
 */
fun bundleConfiguration(name: String): Configuration = configurations.create(name) {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

/** Legit jar: code both editions share. */
val bundledCore: Configuration = bundleConfiguration("bundledCore")

/** Cheat jar only. */
val bundledCheat: Configuration = bundleConfiguration("bundledCheat")

dependencies {
    bundledCore(project(":api"))
    bundledCore(project(":mod:core"))
    bundledCheat(project(":addons:cheat:core"))
    bundledCheat(project(":addons:cheat:$mc"))
}

// Strip manifests, signatures and Kotlin module descriptors that would collide.
val bundledExcludes = listOf(
    "META-INF/MANIFEST.MF",
    "META-INF/*.SF",
    "META-INF/*.RSA",
    "META-INF/*.DSA",
    "META-INF/*.kotlin_module",
    "META-INF/maven/**",
)

/** `elements` carries the producing tasks, so no `dependsOn` needed. */
fun unzipped(configuration: Configuration) =
    configuration.elements.map { jars -> jars.map { zipTree(it.asFile) } }

tasks.jar {
    from(unzipped(bundledCore)) {
        exclude(bundledExcludes)
    }
    // License, permissions and EULA ship in every jar, suffixed so they don't collide.
    from(rootProject.files("LICENSE.md", "PERMISSIONS.md", "EULA.md")) {
        rename { "${it.removeSuffix(".md")}_${base.archivesName.get()}.md" }
    }
}

tasks.processResources {
    val projectVersion = project.version.toString()

    inputs.property("version", projectVersion)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filteringCharset = "UTF-8"

    // The cheat edition reuses this processed manifest (`generateCheatManifest`).
    filesMatching("fabric.mod*.json") {
        expand(
            "version" to projectVersion,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
            "kotlin_loader_version" to kotlinLoaderVersion,
        )
    }
}

/*
 * Cheat-edition jar. Unobfuscated, so plain `jar` (`RemapJarTask` fails with "Cannot configure known indyBsms in a
 * non-obfuscated environment").
 *
 * `cheatJar` is the finished legit `jar` plus cheat classes and a manifest with the `blackaddons:addon` entrypoint
 * added. Deriving from it keeps the nested jars and Loom's `"jars"` array in one place: an earlier hand-built manifest
 * didn't have `"jars"`, Fabric (which only reads that array) never loaded ClassGraph, and FeatureManager crashed.
 */

val generateCheatManifest = tasks.register("generateCheatManifest") {
    dependsOn(tasks.jar)
    val legitJar = tasks.jar.flatMap { it.archiveFile }
    val mcVersion = minecraftVersion
    val outFile = layout.buildDirectory.file("generated/cheat/fabric.mod.json")
    inputs.file(legitJar)
    outputs.file(outFile)
    doLast {
        val json = ZipFile(legitJar.get().asFile).use { zip ->
            val entry = zip.getEntry("fabric.mod.json")
                ?: error("legit jar is missing fabric.mod.json")
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        @Suppress("UNCHECKED_CAST")
        val manifest = JsonSlurper().parseText(json) as MutableMap<String, Any?>
        manifest["name"] = "BlackAddons (Cheat Edition)"
        manifest["description"] = "BlackAddons cheat edition for Minecraft $mcVersion."
        @Suppress("UNCHECKED_CAST")
        val entrypoints = manifest.getOrPut("entrypoints") { linkedMapOf<String, Any?>() }
            as MutableMap<String, Any?>
        entrypoints["blackaddons:addon"] = listOf(
            mapOf("value" to "org.blackaddons.cheat.CheatAddon", "adapter" to "kotlin"),
        )
        val out = outFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(JsonBuilder(manifest).toPrettyString())
    }
}

val cheatJar = tasks.register<Jar>("cheatJar") {
    archiveClassifier = "cheat"

    // The legit payload is a subset, copy it minus its manifest.
    from(zipTree(tasks.jar.flatMap { it.archiveFile })) {
        exclude("fabric.mod.json")
    }
    // Cheat classes from both halves, only here. :addons:cheat:<version>'s dev fabric.mod.json must not ship.
    from(unzipped(bundledCheat)) {
        exclude(bundledExcludes)
        exclude("fabric.mod.json")
    }
    // Legit manifest + addon entrypoint (keeps `"jars"`).
    from(generateCheatManifest)

    dependsOn(tasks.jar, generateCheatManifest)
}

tasks.named("build") { dependsOn(cheatJar) }
