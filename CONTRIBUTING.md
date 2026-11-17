# Contributing to BlackAddons

Thanks for your interest in improving BlackAddons. This guide covers how to set
up the project, the conventions we follow, and how to get a change merged.

## Contributor License Agreement

By submitting a contribution (a merge request, patch, or any other submission) you
agree to the [Contributor License Agreement](CLA.md). In short: you assign the
copyright in your contribution to the BlackAddons Team, the project may
commercialize it, and the public source stays under the project's public terms
([LICENSE.md](LICENSE.md) and [PERMISSIONS.md](PERMISSIONS.md)).
Confirm agreement on your first merge request (the MR template has a checkbox).

## Prerequisites

- **JDK 25.** Every module targets Java 25, on every platform (26.3 and 26.2). Let
  Gradle's toolchain detection pick it up.
- **IntelliJ IDEA** (recommended). The repo is not shipped with a Gradle wrapper -
  the IDE's bundled Gradle (9.8.x) is used.
- No `gradle` on PATH is required: the IDE's Gradle runs every task below.
- If you'd rather have a wrapper, run `gradle wrapper` in your clone. `gradlew`,
  `gradlew.bat` and `gradle-wrapper.jar` are gitignored, so it stays yours and
  can't be committed by accident.

## IntelliJ setup

Open the repo root. There is **one** Gradle build, so there is nothing to link by
hand - the committed `.idea/gradle.xml` registers it on open.

If the editor shows unresolved `net.minecraft.*` in files that Gradle compiles fine:

1. Gradle tool window -> **Reload All Gradle Projects**. (A newly added file may just
   not be in a source set yet.)
2. Still red? **File -> Invalidate Caches... -> Invalidate and Restart**.

A symptom worth naming: `mod/platform/shared/` showing as a plain folder rather than
a source root. It is not a module - the platform stitches it in with `kotlin.srcDir`,
so only a Gradle sync puts it on the classpath.

## Building

Jars land in `mod/platform/<version>/build/libs/` (legit + cheat) and
`addons/extras/<version>/build/libs/` (extras), for `26.3` and `26.2`.

## Where does my change go?

Read [`ARCHITECTURE.md`](documentation/ARCHITECTURE.md) once for the module map, and
[`CREATING_FEATURES.md`](documentation/CREATING_FEATURES.md) for the feature/cheat decision
tree. The short version is the table in [`AGENTS.md`](AGENTS.md#where-does-my-change-go).

## Conventions

- **Conventional Commits are required.** Prefix every commit with `feat:`, `fix:`,
  `refactor:`, `perf:`, `docs:`, `chore:`, `test:`, `build:`, `ci:`, `style:`, or `revert:`.
  The release changelog is
  generated from these prefixes, so an unprefixed commit lands in "Other Changes".
  The full shape is `<type>[(scope)][!]: <description>`, subject at most 72 characters.
  Run this once after cloning and a `commit-msg` hook will check the format for you:

  ```sh
  git config core.hooksPath .githooks
  ```
- **Comments explain *why*, not *what*.** Keep them self-contained and in American
  English. Match the density and style of the surrounding code.
- **No `#if`-style version scattering.** Version-neutral code goes in the shared
  modules; only genuinely version-bound code lives under `mod/platform/`.
- **Addons ship events, not mixins.** If you need a hook events don't cover, add
  the event (and its mixin) to the platform rather than exposing a mixin.

## Merge request process

1. Branch off `main`.
2. Make your change; keep commits focused and conventionally prefixed.
3. Ensure `gradle build-all` passes and you ran the affected edition. Touched shared platform code (`mod/platform/shared`) or a mixin -> run **every** platform (`run-26.3-*` and `run-26.2-*`): a shared mixin is resolved per version and fails only at launch.
4. Open an MR against `main`. The `verify` job must be green.
5. Fill in the MR template, including the CLA checkbox.

Questions? Open an issue.
