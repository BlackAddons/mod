// Copyright (c) 2026 BlackAddons Team. All rights reserved.
// Licensed under the PolyForm Strict License 1.0.0 with additional permissions - see LICENSE.md and
// PERMISSIONS.md. Copying this code into other projects requires the BlackAddons Team's written permission.

/*
 * Root aggregator, no sources. Declares the Kotlin plugin once (`apply false`) so subprojects share one classloader.
 * The version is pinned in settings.gradle.kts from gradle.properties.
 */
plugins {
    kotlin("jvm") apply false
}

fun alias(name: String, task: String, description: String) {
    tasks.register(name) {
        group = "blackaddons"
        this.description = description
        dependsOn(task)
    }
}

alias("build-26.3-legit", ":mod:platform:26.3:jar", "Build the legit-edition jar for Minecraft 26.3.")
alias("build-26.3-cheat", ":mod:platform:26.3:cheatJar", "Build the cheat-edition jar for Minecraft 26.3.")
alias("build-26.3-extras", ":addons:extras:26.3:jar", "Build the standalone Extras-addon jar for Minecraft 26.3.")

// Cheat and Extras run from their own modules, whose dev manifests declare the addon entrypoint.
alias("run-26.3-legit", ":mod:platform:26.3:runClient", "Run the dev client on Minecraft 26.3, legit edition.")
alias("run-26.3-cheat", ":addons:cheat:26.3:runClient", "Run the dev client on Minecraft 26.3 with the cheat addon loaded.")
alias("run-26.3-extras", ":addons:extras:26.3:runClient", "Run the dev client with the Extras addon loaded (legit host + Extras).")

alias("build-26.2-legit", ":mod:platform:26.2:jar", "Build the legit-edition jar for Minecraft 26.2.")
alias("build-26.2-cheat", ":mod:platform:26.2:cheatJar", "Build the cheat-edition jar for Minecraft 26.2.")
alias("build-26.2-extras", ":addons:extras:26.2:jar", "Build the standalone Extras-addon jar for Minecraft 26.2.")

alias("run-26.2-legit", ":mod:platform:26.2:runClient", "Run the dev client on Minecraft 26.2, legit edition.")
alias("run-26.2-cheat", ":addons:cheat:26.2:runClient", "Run the dev client on Minecraft 26.2 with the cheat addon loaded.")
alias("run-26.2-extras", ":addons:extras:26.2:runClient", "Run the dev client with the Extras addon loaded (legit host + Extras).")

tasks.register<GenerateEmoji>("generateEmoji") {
    group = "blackaddons"
    description = "Rebuild the emoji sheets, font and catalog from the Twemoji and discord-emoji versions pinned in GenerateEmoji."
    root = layout.projectDirectory
}

val commitTypesFile = layout.projectDirectory.file("ci/commit-types").asFile
val commitTypeDocs = listOf(
    "AGENTS.md",
    "CONTRIBUTING.md",
    ".gitlab/merge_request_templates/Default.md",
).map { layout.projectDirectory.file(it).asFile }

val checkCommitTypes = tasks.register("checkCommitTypes") {
    group = "verification"
    description = "Fail if the docs list different commit types than ci/commit-types."
    inputs.file(commitTypesFile)
    inputs.files(commitTypeDocs)
    doLast {
        val canonical = commitTypesFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { it.substringBefore(':') }
            .toSet()

        val known = setOf(
            "feat", "fix", "refactor", "perf", "docs", "chore",
            "test", "build", "ci", "style", "revert",
        )
        // Docs quote both `feat` and `feat:`.
        val quoted = Regex("`([a-z]+):?`")

        val drift = commitTypeDocs.mapNotNull { doc ->
            val listed = quoted.findAll(doc.readText())
                .map { it.groupValues[1] }
                .filter { it in known }
                .toSet()
            if (listed == canonical) null else {
                val missing = canonical - listed
                val extra = listed - canonical
                buildString {
                    append("  ").append(doc.relativeTo(projectDir)).append(':')
                    if (missing.isNotEmpty()) append(" missing ").append(missing.sorted())
                    if (extra.isNotEmpty()) append(" unexpected ").append(extra.sorted())
                }
            }
        }

        if (drift.isNotEmpty()) {
            throw GradleException(
                "Commit types drifted from ci/commit-types (${canonical.sorted()}):\n" +
                    drift.joinToString("\n"),
            )
        }
    }
}

// Module boundaries: "can this file see Minecraft?" decides where it lives.

/** Has to compile without Minecraft. The platform's are left out on purpose. */
val mcFreeSourceRoots = mapOf(
    "api" to file("api/src"),
    "mod:core" to file("mod/core/src"),
    "addons:cheat:core" to file("addons/cheat/core/src"),
    "addons:extras:core" to file("addons/extras/core/src"),
)

/**
 * All roots with our packages, including `mod/platform/shared`, which isn't a Gradle module (added via
 * `kotlin.srcDir`).
 */
val packageOwningSourceRoots = mcFreeSourceRoots + mapOf(
    "mod:platform:shared" to file("mod/platform/shared/src"),
)

/**
 * Packages allowed in more than one module: **empty, and it stays that way**. Each module owns its own root package, so
 * an import tells u its module.
 */
val knownSplitPackages = emptySet<String>()

fun sourceFiles(root: File): List<File> =
    if (!root.exists()) emptyList()
    else root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()

fun packageOf(file: File): String? = file.readLines()
    .firstOrNull { it.startsWith("package ") }
    ?.removePrefix("package ")
    ?.trim()
    ?.removeSuffix(";")

val checkModuleBoundaries = tasks.register("checkModuleBoundaries") {
    group = "verification"
    description = "Fail if an MC-free module imports Minecraft, or if a package is split across modules."
    packageOwningSourceRoots.values.forEach { inputs.dir(it).optional(true) }
    doLast {
        // 1. No Minecraft in version-neutral modules. The compiler already enforces it, this makes it a readable rule.
        val mcImports = mcFreeSourceRoots.flatMap { (module, root) ->
            sourceFiles(root)
                .filter { f -> f.readLines().any { it.trimStart().startsWith("import net.minecraft.") } }
                .map { "  $module: ${it.relativeTo(projectDir)}" }
        }
        if (mcImports.isNotEmpty()) {
            throw GradleException(
                "Minecraft imported from a version-neutral module (it must go behind a Platform facade):\n" +
                    mcImports.joinToString("\n"),
            )
        }

        // 2. One package, one module.
        val owners = mutableMapOf<String, MutableSet<String>>()
        packageOwningSourceRoots.forEach { (module, root) ->
            sourceFiles(root).forEach { f ->
                packageOf(f)?.let { owners.getOrPut(it) { mutableSetOf() } += module }
            }
        }
        val split = owners.filterValues { it.size > 1 }

        val added = split.keys - knownSplitPackages
        if (added.isNotEmpty()) {
            throw GradleException(
                "New split package - one package must belong to exactly one module:\n" +
                    added.sorted().joinToString("\n") { "  $it -> ${split.getValue(it).sorted()}" },
            )
        }

        val healed = knownSplitPackages - split.keys
        if (healed.isNotEmpty()) {
            throw GradleException(
                "These packages are no longer split. Remove them from knownSplitPackages so the\n" +
                    "baseline keeps shrinking:\n" + healed.sorted().joinToString("\n") { "  $it" },
            )
        }
    }
}

tasks.register("build-all") {
    group = "blackaddons"
    description = "Build all legit, cheat and Extras-addon jars, run the checks and the tests."
    dependsOn(
        checkCommitTypes,
        checkModuleBoundaries,
        ":api:checkKotlinAbi",
        "build-26.3-legit", "build-26.3-cheat", "build-26.3-extras",
        "build-26.2-legit", "build-26.2-cheat", "build-26.2-extras",
    )
    // Tests live in the version-neutral modules, they run without a game.
    dependsOn(mcFreeSourceRoots.keys.map { ":$it:test" })
}
