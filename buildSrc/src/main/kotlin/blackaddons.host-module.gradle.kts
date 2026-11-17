import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * BlackAddons' own modules: they can use `@HostApi` (binding facades, setting host state). Addons, ours included, never
 * apply this, so the compiler keeps them on the reading side.
 */

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.optIn.add("org.blackaddons.api.HostApi")
}
