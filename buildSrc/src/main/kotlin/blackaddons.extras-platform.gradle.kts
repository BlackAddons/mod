/**
 * An `:addons:extras:<version>` module: the standalone Extras jar, :addons:extras:core plus MC-bound vanilla HUD
 * hiding. api, core, platform, BlackSkija and ClassGraph come from the host. Unobfuscated, so the plain `jar` is the
 * output. The folder name is the Minecraft version.
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
val skijaVersion = mcProp("skija_version")

base {
    archivesName = "blackaddons-extras-$mc"
}

dependencies {
    // Compiled against the host, never bundled.
    implementation(project(":mod:platform:$mc"))
    implementation(project(":api"))

    /*
     * Not :mod:core: compiling against the published contract only means touching host internals is a compile error.
     * They're still on the runtime classpath through the host.
     */

    // Not in the host, so we bundle it below.
    implementation(project(":addons:extras:core"))

    // Unobfuscated: no `mappings(...)`, Fabric via plain `implementation(...)`.
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    // The host nests these, we only need them for the dev run.
    runtimeOnly("com.github.DreamcastERA1.BlackSkija:blackskija-$mc:$blackskijaVersion")
    runtimeOnly("io.github.classgraph:classgraph:${prop("classgraph_version")}")

    runtimeOnly("io.github.humbleui:skija-windows-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-linux-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-x64:$skijaVersion")
    runtimeOnly("io.github.humbleui:skija-macos-arm64:$skijaVersion")
}

/*
 * Bundles only the version-neutral half (classes and bar atlas): a second api/core/platform would shadow the host's.
 * Resolved as a non-transitive jar, which asks nothing of the producer at configuration time.
 */
val bundledCore: Configuration = configurations.create("bundledCore") {
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

dependencies {
    bundledCore(project(":addons:extras:core"))
}

val bundledExcludes = listOf(
    "META-INF/MANIFEST.MF",
    "META-INF/*.SF",
    "META-INF/*.RSA",
    "META-INF/*.DSA",
    "META-INF/*.kotlin_module",
    "META-INF/maven/**",
)

tasks.jar {
    // `elements` carries the producing task, so no `dependsOn` needed.
    from(bundledCore.elements.map { jars -> jars.map { zipTree(it.asFile) } }) {
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

    filesMatching("fabric.mod.json") {
        expand(
            "version" to projectVersion,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
            "kotlin_loader_version" to kotlinLoaderVersion,
        )
    }

    from(rootProject.file("mod/platform/shared/src/main/resources/assets/blackaddons/icon.png")) {
        into("assets/blackaddons-extras")
    }
}
