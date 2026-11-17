// :addons:cheat:core - the MC-free half of the cheat addon. Anything that needs Minecraft goes in :addons:cheat:26.3.
plugins {
    id("blackaddons.jvm-module")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

dependencies {
    implementation(project(":api"))
    testImplementation(testFixtures(project(":api")))
    implementation(project(":mod:core"))
    testImplementation("com.google.code.gson:gson:${prop("gson_version")}")

    // Provided by Minecraft at runtime.
    compileOnly("org.slf4j:slf4j-api:${prop("slf4j_version")}")
    compileOnly("org.joml:joml:${prop("joml_version")}")
}
