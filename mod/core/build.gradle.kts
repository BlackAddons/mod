// :mod:core - the mod's version-independent logic: features, screens, backend, ClickGUI. No Minecraft.
plugins {
    id("blackaddons.jvm-module")
    id("blackaddons.host-module")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

dependencies {
    implementation(project(":api"))
    testImplementation(testFixtures(project(":api")))

    // Minecraft gives us the binding at runtime.
    compileOnly("org.slf4j:slf4j-api:${prop("slf4j_version")}")

    // Minecraft gives us gson at runtime.
    compileOnly("com.google.code.gson:gson:${prop("gson_version")}")

    // Feature discovery. implementation, not compileOnly: Minecraft doesn't provide it, so the platform bundles it.
    implementation("io.github.classgraph:classgraph:${prop("classgraph_version")}")
}
