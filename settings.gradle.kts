// Copyright (c) 2026 BlackAddons Team. All rights reserved.
// Licensed under the PolyForm Strict License 1.0.0 with additional permissions - see LICENSE.md and
// PERMISSIONS.md. Copying this code into other projects requires the BlackAddons Team's written permission.

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
    plugins {
        // From gradle.properties, which buildSrc reads too, so the two Kotlin classpaths can't drift apart.
        id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlin_version").get()
    }
}

rootProject.name = "blackaddons"

// One project tree. The leaf says what a module can see: `core` is MC-free, a version number means Loom.
include("api")

include("mod:core")
include("mod:platform:26.3")
include("mod:platform:26.2")

include("addons:cheat:core")
include("addons:cheat:26.3")
include("addons:cheat:26.2")
include("addons:extras:core")
include("addons:extras:26.3")
include("addons:extras:26.2")
