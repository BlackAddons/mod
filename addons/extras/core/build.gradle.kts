/*
 * :addons:extras:core - the MC-free half of Extras (action-bar parsing, trackers, storage, lore parsing). Ships inside
 * each version's standalone Extras jar, api/core/platform come from the host.
 */
plugins {
    id("blackaddons.jvm-module")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

dependencies {
    implementation(project(":api"))
    testImplementation(testFixtures(project(":api")))

    /*
     * Not :mod:core: compiling against the published contract only means touching host internals is a compile error.
     * They're still on the runtime classpath through the host.
     */

    // Provided by Minecraft at runtime.
    compileOnly("org.slf4j:slf4j-api:${prop("slf4j_version")}")

    // For the storage cache's AddonStorage JSON. Minecraft gives us this at runtime.
    compileOnly("com.google.code.gson:gson:${prop("gson_version")}")
}
