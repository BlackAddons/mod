# BlackAddons - Architecture

A map of the codebase, written for the maintainers and for future contributors -
including ones who reach for an AI assistant. Read it once, keep it open while
making non-trivial changes. It explains not just *where* things are but *why the
project is shaped this way*, because that shape is the thing that keeps a
multi-module, multi-edition mod maintainable.

- To add a feature, see [`CREATING_FEATURES.md`](CREATING_FEATURES.md).
- For AI-agent rules and the quick command list, see [`../AGENTS.md`](../AGENTS.md).

---

## Big picture

BlackAddons is a Fabric client mod for **Minecraft 26.3 and 26.2**, focused on
Hypixel Skyblock. From one source tree it ships **two editions**:

- **Legit** - quality-of-life, HUD, dungeon, and Skyblock features. No cheats.
- **Cheat** - everything in Legit **plus** cheat features. The cheats are loaded
  through the very same `blackaddons:addon` entrypoint a third-party addon would
  use. It also compiles against `:mod:core`, because most of it extends first-party features (`FeatureExtension`s of the
  Dungeon Map, Simon Says, Actions) - a third-party addon has `:api` only.

Both jars share `mod_id = "blackaddons"`; a user installs one, never both. The
Mods list tells them apart by name ("BlackAddons" vs "BlackAddons (Cheat
Edition)").

### The two ideas that drive everything

1. **A stable core, sealed off from Minecraft.** The bulk of the mod - UI,
   feature framework, settings, events - is plain Kotlin with *no Minecraft or
   Fabric on the classpath*. The compiler enforces that boundary. Anything that
   must touch `net.minecraft.*` is pushed into a thin platform layer behind an
   interface. This is what makes porting to a new Minecraft version a matter of
   days, not weeks.

2. **The version-specific layer is swappable, not scattered.** There are no
   `#if`-style version switches sprinkled through the code. Instead, every
   Minecraft-facing capability is an interface (`Platform.*` facade) whose
   implementation lives in a per-version module: `:mod:platform:26.3`
   and `:mod:platform:26.2`. Adding Minecraft 26.4 later means adding a
   `mod/platform/26.4/` sibling - the core doesn't move.

   The ratio is the point. Two live platforms share **one** `mod/platform/shared/`
   source dir; only a handful of files per platform are genuinely per-version, and
   most of those differ over *where* a thing lives rather than what it does - see
   [`McCompat`](#mccompat---naming-the-places-two-versions-disagree).

Keep those two ideas in mind and the module layout below explains itself.

---

## Module layout

```
api/                  <- :api - the shared public contract (addons compile against this)
mod/
  ├─ core/            <- :mod:core - host implementation (ClickGUI, FeatureManager, config, features)
  └─ platform/
       ├─ shared/     <- a bare source dir (NOT a module) - MC-touching code every platform shares
       ├─ 26.3/       <- :mod:platform:26.3 - mixins, facade impls, jar assembly, the Skija renderer
       └─ 26.2/       <- :mod:platform:26.2 - the same, for 26.2
addons/               <- one folder per addon: its MC-free half and its per-version half, side by side
  ├─ cheat/
  │    ├─ core/       <- :addons:cheat:core - CheatAddon + every cheat, MC-free, cheat jar only
  │    ├─ 26.3/       <- :addons:cheat:26.3 - no sources: the dev fabric.mod.json for runClient
  │    └─ 26.2/       <- :addons:cheat:26.2 - the same, for 26.2
  └─ extras/
       ├─ core/       <- :addons:extras:core - the Extras addon's version-neutral code
       ├─ 26.3/       <- :addons:extras:26.3 - ships Extras as a standalone jar for 26.3
       └─ 26.2/       <- :addons:extras:26.2 - the same, for 26.2
```

**The path is the documentation.** It says what a module is (`core` / a version number),
who it belongs to (`mod` / `addons/<name>`), and what it may see - a `core` leaf has no
Minecraft on its classpath; a version leaf applies Loom and does. Nothing else is needed to
keep the halves apart, which is why the whole apparatus that used to bridge them is gone.

Grouping the other way - by *build*, which is how this repo used to be laid out - meant that
reading one addon took you to two distant folders whose names didn't even match (`cheat-common` and
`platform-26.2/cheat`), and the platform folder was full of things that
weren't the platform.

### One build

There is a single `settings.gradle.kts`, and every module is in it. Minecraft-facing modules
apply Loom (`blackaddons.mc-module`); the rest don't (`blackaddons.jvm-module`). That is the
entire mechanism.

It used to be two Gradle builds - the pure-JVM one, and a composite-included `platform-26.2`
with its own Loom, its own Kotlin, and its own wrapper. Nothing about the *code* needed that;
it cost a maven round-trip (the root build published `:api` / `:mod:core` into a build-local
repository for the platform build to read back as jars), maven coordinates with placeholder
versions standing in for what were really project dependencies, `GradleBuild` tasks that
launched a second Gradle in-process, and a check task whose only job was to notice when the
two wrappers drifted apart. All of it is deleted. The platform now writes
`implementation(project(":mod:core"))` and means it.

One piece of that machinery survived, and it is worth knowing why. The bundled first-party
classes still arrive as *jars*, resolved through a configuration, instead of being read out of
the other projects' source sets. Reading a source set forces Gradle to configure that project -
and `:addons:cheat:26.3` compiles against the platform, so the platform asking to configure it
is a circular evaluation. Resolving an artifact asks nothing of the producer at configuration
time. (At *task* level there is no cycle: the cheat classes are needed by `cheatJar`, not by
`jar`.)

The one thing a single build costs: **every platform shares one Loom version**. If some
future Minecraft version ever needs a different one, that platform - and only that platform -
goes back into a composite build of its own.

#### Why the modules have different groups

A project's identity to Gradle is `group:name:version`, and its *name* is the last segment of
its path. Three of ours are called `core` and two are called `26.3`. Left under one shared
group they would literally be the same module, and a dependency on one would resolve to
whichever project was asking - which surfaces, bafflingly, as a circular task dependency (a
module compiling against its own jar).

So the convention plugins fold the path into the group: `:mod:core` is
`org.blackaddons.mod:core`, `:addons:cheat:core` is `org.blackaddons.addons.cheat:core`. This
is invisible day to day, and it is exactly what stops being invisible if you ever add another
module named after what it is rather than where it lives.

### Which module does this file go in?

Two questions decide it, in order.

**1. Does it need Minecraft?** If yes, it goes in a platform module (or `mod/platform/shared/`,
if it only uses MC names that hold across the versions we target). If no, it stays in the
pure-JVM half. This is the hard boundary: the version-neutral modules have no Minecraft on
the classpath at all, so a stray `import net.minecraft.*` there is a compile error, not a
code-review note.

**2. Is it a promise to addons?** `:api` is the contract third-party addons compile
against - facade interfaces, the feature/settings/event API, the UI toolkit. Everything
there is public, and changing it can break other people's code. The project is still pre-release,
so source and binary compatibility between builds is not promised yet; `:api` means the surface is
version-neutral across supported Minecraft versions, not that it is frozen. Breaking changes should
still be deliberate and documented so addon authors have a clear migration path. `:mod:core` is *our* implementation on
top of that contract: the ClickGUI, the backend client, our own features.
An addon never depends on `:mod:core`.

So: **`:api` is what addons use, `:mod:core` is what we do.** When in doubt, it belongs in
`:mod:core` - moving something out to `:api` later is easy, taking it back is not.

#### `internal` marks the difference

`:mod:core` is consumed by the platform as a separate Gradle module, so Kotlin's
`internal` means exactly what it should: *visible to `:mod:core` only*. Anything in `:mod:core`
that the platform does not call is `internal`, which leaves the public declarations there
saying something true - they are the platform-facing surface, and nothing else is.

Keep it that way. A new class in `:mod:core` starts out `internal`; it becomes public when a
platform module actually needs it, and that moment is worth a second's thought.

#### The build checks both

`gradle checkModuleBoundaries` (run by `build-all`) fails if a version-neutral module
imports Minecraft, and if a package lives in more than one module. Split packages are the
reason the tree can lie to you: a package that exists in two modules gives an import no way to
tell which one it means. The check carries a baseline of the packages that are already split (empty today); it fails if
that list grows, and it fails if an entry on it is no longer split
without being removed - so the list can only shrink.

### `mod/platform/shared/` - sharing *source*, not a compiled module

`mod/platform/shared/src/main/kotlin` is **not a Gradle module**. It's a bare source
directory that a platform adds to its own source set via
`kotlin.srcDir("../shared/src/main/kotlin")`. Why source and not a
compiled jar? Because two different Minecraft versions compile the same `.kt`
against *different* mappings - a precompiled module couldn't serve both. So the
unit of sharing is source: each platform compiles the same files against its own
mappings.

It holds platform code that touches Minecraft but uses only API names stable
across the versions we target - player/world/session reads, the cosmetics bridge,
the `Abstract*` facade bases, the entrypoints. Two platforms (26.3 and 26.2) compile it today. A
file earns a place here only if its logic is genuinely version-neutral; anything that diverges per
version stays in the platform module.

### `:api` - the public contract

Pure JVM. **No Minecraft, no Fabric, no ClassGraph on the classpath** - the
compiler enforces it. It compiles only against SLF4J, Gson and JOML (all `compileOnly`; Minecraft
provides them at runtime).

It holds everything an addon needs to import:

- **`Platform`** - the service locator (`object Platform`): a read-only getter per host facade, all
  served by the one `PlatformProvider` the host binds at startup.
- **`Addon`** / **`AddonContext`** - the interface an addon entrypoint implements, and what it is handed.
- **Host facades**, about 33, grouped by domain: `api.player` (`PlayerHost`, `RotationHost`,
  `MovementHost`, `InteractionHost`, `RaycastHost`), `api.world` (`WorldHost`, `WorldRenderer`,
  `ItemHost`), `api.gui` (`ScreenHost`, `WindowMetrics`, `OpenContainerHost`, ...), `api.chat`,
  `api.camera`, `api.input`, `api.skyblock`, `api.commands`, `api.config`, `api.cosmetics`; `api.host`
  keeps the system services (`GameStats`, `Scheduler`, `AddonLogger(s)`, `AddonStorage(s)`, `ModsHost`,
  `ClipboardHost`, `DesktopHost`, `HttpHost`, `LocaleHost`, `SessionStatus`).
- **The framework** - `EventBus`, `Event`, `Cancellable`, event types; `Feature`,
  `FeatureCategory`, the `Setting<T>` hierarchy, `HudElement`.
- **Shared helpers** in `api.util` - `OffThread`/`Workers`/`LatestJob`, `Disk`, `I18n`, `Uuids`, `Chroma`, ...
- **The draw facade** - `Renderer` and the `Render` convenience object in `ui/render/`.
- **The UI toolkit** - `ui/`: `Panel` and the `UiManager` screen stack,
  `Tooltip`, `Theme`, `Animation`/`Easing`/`DecayAnimation`, the widgets, and the setting editors (`ColorPopover` - the
  small color picker every swatch opens, which can expand into
  `ColorPickerScreen` - and `ListEditorScreen`). It sits here because it is the *view*
  of types that are already public: a widget renders a `Setting<T>`, so shipping
  the settings without their widgets would let an addon declare a `ColorSetting`
  but never open a color picker of its own.

Rule of thumb: **anything an addon imports lives here.**

### `:mod:core` - the host implementation

Pure JVM, depends on `:api`. Still **no Minecraft, no Fabric.** This
is where the contract gets an implementation:

- **`FeatureManager`** - runs ClassGraph at startup to discover `Feature` objects.
- **`CoreHosts`** - the facades core implements itself (scheduler, loggers, storage, config, http),
  typed as interfaces so the platform's provider can hand them out while the impls stay internal.
- **`BlackaddonsConfig`** - JSON persistence for feature state, settings, HUD layout.
- **BlackAddons' own screens** - `ClickGuiScreen`, the sidebar and its tabs, the
  HUD editor, the cosmetic editor, the profile viewer, `ModPickerScreen`. These are
  the app, not the toolkit: addons build their own `Panel`s out of the `:api`
  widgets instead of extending these.
- **Bundled host features** - e.g. `DevMode`, `PerformanceHud`.

### `:mod:platform:<version>` - the Minecraft layer

Fabric Loom, Java 25. One module per Minecraft version (`26.3`, `26.2`), each compiling its own
sources plus `mod/platform/shared`. Depends on `:mod:core` + `:api`. This is the only place that
imports `net.minecraft.*`. In `shared`:

- **`Blackaddons` / `BlackaddonsClient`** - the Fabric entrypoints. They call
  `Platform.bind(MinecraftPlatform)` and `MinecraftPlatform.install()`, discover addons, and hand
  them to `FeatureManager.init(...)`.
- **`EventEmitters`** - wires Fabric callbacks into our `EventBus` and installs
  the always-on HUD render hook.
- **Mixins** (`mixin/<group>/`, Java) - translate Minecraft events into `EventBus.post(...)`.
  Auto-registered by `AutoMixinDiscovery` (an `IMixinConfigPlugin`) scanning the
  `mixin` package recursively, so `blackaddons.mixins.json` names only the plugin.
- **Facade impls** (`impl/`) - `MinecraftLocale`, `MinecraftWindow`, `MinecraftScreen`, and the
  rest; `SharedPlatform`, the version-neutral half of every `MinecraftPlatform`.
- **`SkijaRenderer`** - the BlackSkija-backed `Renderer` implementation.
- **`SkijaHostScreen`** - the single Minecraft `Screen` the mod ever hosts.
- **MC-typed events** - `RenderOverlayEvent` and friends, which hold Minecraft
  types and therefore can't live in `:api`.

In each version module: `MinecraftPlatform` (the provider), `compat/` (where the two versions'
APIs differ) and the facade impls whose signatures differ per version.

### The cheat modules

Cheats mirror the platform split so a single cheat never has to choose between
portability and MC-binding ergonomics:

- **`:addons:cheat:core`** - pure JVM. Holds `CheatAddon` (the `blackaddons:addon` entrypoint object)
  and every cheat; MC access goes through `Platform` facades. Bundled into the cheat jar only - the
  legit jar never carries it.
- **`:addons:cheat:<version>`** (`26.3`, `26.2`) - Fabric Loom, no sources: a dev-only
  `fabric.mod.json`, so `runClient` loads the cheats through the addon entrypoint. Its output is
  merged into the cheat jar by `:mod:platform:<version>:cheatJar`.

No cheat module ships mixins - the same events-only constraint that binds
third-party addons. The cheat edition is just the legit jar plus these classes
plus a manifest that adds the `blackaddons:addon` entrypoint pointing at
`CheatAddon` (see `generateCheatManifest`, which preserves the Loom-injected
`"jars"` array of nested dependencies).

### The Extras addon

Extras is the worked example of a **standalone** addon (as opposed to the
bundled cheat). It's a port of select features from older addons:

- **`addons/extras/core`** - pure-JVM, version-neutral feature code + domain types.
- **`:addons:extras:<version>`** (`26.3`, `26.2`) - the shipping vehicle: a real `fabric.mod.json`,
  `ExtrasClient` (the bits that need Minecraft), and `addons/extras/core`'s classes and resources.
  `:api`, `:mod:core` and the platform are provided by the host at runtime, never nested.
  Build/run via `build-<version>-extras` / `run-<version>-extras`.

---

## Build

Convenience tasks are registered under the **`blackaddons`** Gradle group so no
one has to remember which subproject carries which entrypoint.

Every task exists once per platform; substitute `26.2` for `26.3` throughout.

| Task                       | Result                          |
|----------------------------|---------------------------------|
| `gradle build-26.3-legit`  | Legit-edition jar               |
| `gradle build-26.3-cheat`  | Cheat-edition jar               |
| `gradle build-26.3-extras` | Standalone Extras-addon jar     |
| `gradle build-all`         | All six jars, both platforms    |
| `gradle run-26.3-legit`    | Dev client, legit               |
| `gradle run-26.3-cheat`    | Dev client, cheat addon loaded  |
| `gradle run-26.3-extras`   | Dev client, Extras addon loaded |

Jars land in `mod/platform/<version>/build/libs/` (legit + cheat) and
`addons/extras/<version>/build/libs/` (extras). `build-all` also runs the tests.

### One JDK, one version number, no wrapper

- **JDK 25.** Every module targets Java 25 - Minecraft 26.3 requires it, so a lower
  target for the pure-JVM modules would only add a second JDK to install. Gradle's
  toolchain detection picks it up.
- **One version number.** `mod_version` lives in the root `gradle.properties`, and every
  module reads it from there. Version-specific numbers (the Minecraft version, the loader,
  Fabric API, BlackSkija, BlackVertex) are prefixed by platform - `mc263_*`, `mc262_*` -
  because Gradle reads `gradle.properties` only from the root project, so each platform's
  numbers have to sit next to the others' without shadowing them. They are genuinely not the
  same: the two platforms pin different Fabric API and different native-library builds.
- **No committed Gradle wrapper**, by choice. Locally you build through
  IntelliJ's Gradle; GitLab CI runs in a Gradle image (`GRADLE_IMAGE` in `.gitlab-ci.yml`).

### The build tasks are plain aliases

`build-26.3-legit` and friends are one-line aliases for a task in a module (`:mod:platform:26.3:jar`,
`:mod:platform:26.3:cheatJar`, `:addons:extras:26.3:jar`). Nothing
clever happens; the names exist so that a build choice - which Minecraft version, which
edition - is a task name rather than a combination of flags.

They used to be `GradleBuild` tasks that launched a second Gradle build in-process, because
the platform lived in its own composite build and could not simply depend on the pure-JVM
modules - they had to be published into a build-local maven repository first and read back as
jars. See "One build" above for what that cost. It is all gone.

### Adding a new Minecraft version

This is what all the above buys you, and it has been paid out more than once: platforms were added
this way, and only a handful of files per platform turned out to be genuinely per-version
against the ~180 in `mod/platform/shared/`. To add, say, 26.4:

1. Copy `mod/platform/26.3/`, `addons/cheat/26.3/` and `addons/extras/26.3/` to `.../26.4/`. Their build
   files only apply a convention plugin (`blackaddons.platform`, `-cheat-platform`, `-extras-platform` in
   buildSrc), which reads the version from the folder name.
2. Add the `mc264_*` properties to the **root** `gradle.properties` (Minecraft, Fabric API, the
   BlackSkija/BlackVertex artifacts, archives base name) - never a module-level one.
3. `settings.gradle.kts`: `include` the three new modules.
4. Root `build.gradle.kts`: the `alias(...)` lines (`build-26.4-legit/-cheat/-extras`,
   `run-26.4-*`) and the new build tasks in `build-all`'s list.
5. `.gitlab-ci.yml`: the new modules' `build/libs/*.jar` artifact paths.
6. Fix up whatever `net.minecraft.*` calls actually changed: the version's `compat/` (`McCompat` and
   friends) and the mixins whose targets moved. Version-neutral code in `mod/platform/shared/` and the
   core doesn't move.

**The compiler drives step 6.** The shared `MinecraftPlatform` won't build until every facade
compiles, and a `compat/` that omits a name doesn't either, so "what does this version still owe
me?" is a build error list rather than something to remember. What the compiler *can't* catch,
so check it by hand:

- A facade that compiles but must hop to the client thread - see the thread-safety
  rule under the service locator. A copied impl that lost its `onClient { }` compiles fine and
  breaks only under load.
- Native library builds (BlackSkija/BlackVertex) and the Fabric API version have to
  exist for the new Minecraft version; take the *superset* when two libraries
  disagree on the minimum.
- Mixin targets. Method names and `@At` descriptors are strings, checked only when the
  game loads, and `defaultRequire` is 1 - a moved method compiles and then crashes on
  startup. Check every shared mixin against the new version's decompiled sources.
- Input codes. A key code is the running version's native number (GLFW on 26.2,
  SDL3 on 26.3), so it only lives in memory; `api/input/Keys` resolves its constants
  through Minecraft's key names. Anything saved holds an `api/input/InputBinding`,
  which writes the key name (`key.keyboard.f`) every version shares. Saves from
  before that held GLFW numbers, which `GlfwKeyNames` turns back into names.
- A capability the *older* version simply lacks. When you meet one of these, do not go looking for
  a nicer hook - write down that it does not exist, so the next reader doesn't go looking either.

The core, the UI, the feature framework, and every addon come along unchanged.

### `McCompat` - naming the places two versions disagree

`mod/platform/<version>/.../compat/McCompat.kt` exists for one narrow job: when two Minecraft
versions disagree about **where** a thing lives rather than what it does. Shared code says
`mc.releaseCursorAt(x, y)`; each platform answers in its own spelling. A new platform that
omits one of these does not compile - the same bargain `PlatformProvider` strikes for the
facades. Names every live version spells the same way (`mc.currentScreen`, `mc.chatComponent`)
sit in `mod/platform/shared/.../compat/McCommon.kt`, in the same package, so callers don't change;
a new version that disagrees moves the name back into each `McCompat`.

`compat/` as a whole holds every place the two versions' APIs differ in shape or hook stage, not
only in name: `McCompat.kt` also carries the version-specific half of the world render pass
(`drawText`), next to `Gpu.kt`, `NativeKeys.kt` and `NativeDesktop.kt`. Shared
code calls one name; each version's `compat/` decides what it means there.

---

## Key abstractions

### Platform service locator

`Platform` (`org.blackaddons.api`) is the lookup table. `:mod:core` and addons are
compiled without Minecraft on the classpath, so anything needing the host game
goes through a facade interface read from here:

```kotlin
object Platform {
    fun bind(provider: PlatformProvider)        // host only, once; a second call throws

    val configDir: Path get() = provider.configDir
    val chat: ChatHost get() = provider.chat
    val player: PlayerHost get() = provider.player
    val renderer: Renderer get() = provider.renderer   // UI draw facade
    ...
}
```

Every facade is read-only and comes from the one provider the host binds at startup, before any
feature or addon runs - so no addon can swap a facade out from under everyone else. Reading one
before the bind fails fast, which is what you want; a silent zero would be worse.

**Host wiring is `@HostApi`.** `bind` here and on the other facades (`Dungeon`, `SkyblockLevels`, `Election`, `Party`,
`ActionQueue`), plus the setters of state every addon reads (`DevFlags`, `UiScaling`, `Theme.palette`,
`HudFontContext`, `Toasts.durationScale`, `HudEditorMode`, `UiMouse.set`), carry an opt-in marker at
error level. Only the host modules opt in (`blackaddons.host-module` in buildSrc: `:api`, `:mod:core`, the
platforms), so an addon - ours included - gets a compile error for writing them. It binds Kotlin only;
Java ignores opt-in markers.

**A version supplies its facades as one compile-checked object.**
`PlatformProvider` (also in `:api`) declares exactly what a Minecraft version must
provide. A version module implements it as a single `object` named `MinecraftPlatform` - so **forgetting a facade is a
compile error**, which is how a port to a new Minecraft version fails
fast instead of crashing at runtime on the one screen that happened to use it:

```kotlin
// mod/platform/26.3/.../MinecraftPlatform.kt
internal object MinecraftPlatform : SharedPlatform() {
    override val chat = MinecraftChat
    override val player = MinecraftPlayer
    ...
}
```

`Blackaddons.onInitialize()` binds it with `Platform.bind(MinecraftPlatform)` - the entrypoint is
shared source compiled into every version module, and every version names its provider the same,
so a missing one is a compile error, not a failed scan. No reflection is involved anywhere.

What does not vary by version lives in `SharedPlatform` (`mod/platform/shared`), which every
`MinecraftPlatform` extends: the core facades from `CoreHosts`, the config/game directories and
versions the loader reports, and `cosmetics`. It also owns `install()`, which runs the startup hook
of every `Installable` facade in one fixed order. The privileged Mojang session is deliberately kept
off this addon-facing locator.

Tests bind `TestPlatform` (`:api` test fixtures) instead: every facade is a plain field a test sets
to its fake (`TestPlatform.player = fake`), read back through `Platform` by the code under test.

So **adding a facade** is: the interface in `:api`, a `Platform` getter, a
`PlatformProvider` member, a `TestPlatform` field, and an impl per version (in `mod/platform/shared` if
the MC names are version-stable, else in the version module). The provider entry
is the part that makes every other version stop compiling until it's supplied -
that's the feature, not friction.

#### A host impl must be callable from any thread

Version-neutral code knows nothing about Minecraft's render thread, and plenty of
it legitimately runs on workers (backend auth, cosmetics fetch, presence). **The
thread hop belongs in the impl, not at every call site.**

This is not theoretical. `Platform.chat.local(...)` used to call Minecraft's chat
directly: adding a chat line makes the game wrap the text, which bakes glyphs
through `RenderSystem` and throws `called from wrong thread` off it. On a backend
timeout that exception killed the worker mid-task - the warning never reached
chat, and the screen waiting on that worker hung on "Loading" forever. The fix was
one line of routing in `MinecraftChat`:

```kotlin
override fun local(text: String) {
    val component = Component.literal(text)       // plain data on the caller's thread
    onClient { it.chatComponent.addClientSystemMessage(component) }   // hop only for the MC call
}
```

An audit then found a second caller that would have hit the identical crash -
which is exactly why the facade, not the caller, is the right place to fix it.
The tools, both in `mod/platform/shared/.../util/ClientThread.kt`:

- **`onClient { }`** for a call that changes something and returns nothing: inline on the client
  thread, queued from anywhere else.
- **`readOnClient { }`** for a call that has to *return* live state (entities, chunks, the
  clipboard, a raycast): it hops, waits and hands the value back, and costs nothing when already on
  the client thread.
- **Drawing is the exception.** `Render` and `Platform.worldRenderer` only mean something inside the
  frame being recorded, so they are render-thread only and drop (or reject) a call from anywhere else
  instead of hopping.

Rules that follow:

- **A new host, or a new platform, means auditing it for render-thread-only
  Minecraft calls.** We can read our own call sites; we cannot read a third-party
  addon's threads. A host that only works on the client thread is a landmine for
  addon authors, and the stack trace will look like our bug.
- **A host that hands out a handle synchronously** (an aim lease, a button hold) is either
  thread-safe itself (`Holds`, `ClickQueue`) or returns the handle at once and carries the
  work over (`AimControl` through `ViewPort.execute`).
- **Know which thread an event fires on.** Almost all of them fire on the client thread.
  `PacketEvent.S2C` does not: it is posted from `Connection.genericsFtw` on the netty thread, so a
  listener that touches game state has to hop (`ServerTickEvent` is built from it and hops before it
  posts).
- **A worker that owns UI state must always land a result**, or the UI hangs on
  its loading state. Don't re-implement that guarantee per screen: `OffThread.run`
  (`org.blackaddons.api.util`, available to addons too) calls back exactly once, turning a throw
  into a value the caller can render. See the "Work that blocks" section of `CREATING_FEATURES.md`.

### Renderer facade - and why it's the most important seam in the mod

`Renderer` (in `:api`) defines every draw call the UI needs. It's implemented by
`SkijaRenderer` (in the platform module), backed by **BlackSkija** - our own
Skija-based, immediate-mode rendering backend.

This one interface is why a two-week backend swap (NanoVG -> Skija) touched
essentially one implementation file and the host screen, while ~12k lines of UI
code didn't move. The core draws through `Render.*`; it has no idea what's behind
it. Treat this seam as sacred: **never call BlackSkija or vanilla `GuiGraphics`
directly from feature or UI code** - go through `Render`.

Two properties of the backend shape the API:

- **Immediate mode, no frame.** A draw call just appends to BlackSkija's
  per-frame batch, which its compositor flushes once per frame. So - unlike the
  old NanoVG backend - there is no `frame(context) { }` to open. The host's screen
  and HUD overlay simply issue `Render.*` calls during their draw pass;
  `SkijaRenderer.beginPass()` resets the CPU transform mirror at the top of each.
- **Build once, draw by reference.** For geometry that is drawn every frame but
  changes rarely - hundreds of wires, a minimap, the dungeon map's rounded rooms (`PathData.polygon`) - record it into a
  `PathData` and
  turn it into a `ShapePath` with `Render.createShape`; then `fillShape` /
  `strokeShape` draw the whole thing in one call, in the current transform (a
  stroke of 0 is a hairline). `SkijaRenderer` hands these to BlackSkija's own
  `SkijaShape` (3.0+), which owns the native path and its deferred free - the mod
  never touches Skija paths itself. Pair every `createShape` with a `deleteShape`.
- **Retained layers.** `Render.layer(layer, ...) { content }` records `content` once (BlackSkija's `SkijaLayer`, a Skia
  picture) and replays it every frame without
  running it, until `Render.invalidateLayer`. For a big, mostly still surface: draw
  the static part through a layer and what moves around it. Content must only draw -
  it does not run on frames that replay, so stamping a `Rect` inside it throws.
- **Screen-anchored chroma.** `object Render` is the entry point the code uses (`Render.rect`, `Render.text`). A few
  methods are composites over the
  primitives - notably `Render.chromaText`, which draws a `ColorSetting`'s chroma (one cycling color, or a per-character
  rainbow) using the shared `Chroma` math.
  Its hue is anchored to **screen space** via `Renderer.toScreen` (a CPU
  `TransformTracker` mirrors the transform stack), so the rainbow is one
  continuous field across every element - the shader-style "single mask" look,
  without a shader.

### Event system

`EventBus` is a lock-free pub/sub. Listeners are stored per exact event class in
an immutable list swapped atomically (CAS on register/unregister); `post` reads a
lock-free snapshot.

- **Superclass dispatch.** `post` delivers to listeners on the event's concrete
  class *and* every superclass up to `Event`, merged and priority-sorted - so
  subscribing on a sealed parent (`ClientTickEvent`) catches both `Pre` and `Post`.
- **A memoized dispatch view.** The merged, sorted listener list for a concrete
  event class is cached, because it's a pure function of the registration set.
  Hot-path events (tick 20/s, render every frame) skip the superclass walk and
  sort entirely. Register/unregister (cold, on feature toggle) clear the cache.
- **Broken listeners quarantine themselves.** Exceptions are caught and logged;
  in a dev environment (or with `DevFlags.rethrowListenerExceptions`) they're
  rethrown to surface the bug. After 5 consecutive throws a listener
  auto-unregisters, so one broken addon can't spam the log forever.

Features subscribe inside `init()` with `register<T> { }`; the framework wires the
listener on enable and removes it on disable. Addon authors never touch
`EventBus.register` directly.

Version-independent events live in `:api`'s `event/`, grouped by domain (`event/chat`,
`event/input`, `event/render`, `event/network`, `event/world`, `event/dungeon`); MC-typed events (holding `Packet` /
`Screen` / `GuiGraphicsExtractor`) live in the platform
module and bind any subscriber to that Minecraft version.

#### Two chat events, on purpose

Chat is fired twice, at different depths, and the difference is not cosmetic:

- **`ChatMessageEvent`** - at `ChatComponent.addMessage`, where chat is added to the GUI.
  It is `Cancellable`: this is where you suppress or rewrite a line.
- **`ChatReceiveEvent`** - off the packet, in `ClientPacketListener.handleSystemChat` and
  friends. Deliberately **not** cancellable. It is where you *watch*.

The reason is starvation. Another mod hiding a message at the GUI end starves anything
listening there - a trigger that waits for a specific server line then never fires, silently.
The observe-only event sits upstream of every such filter, including our own, so a watcher
cannot be blinded. **Buy that ordering structurally; never by racing mixin priorities at the
same injection point.**

`ChatReceiveEvent` also carries `message: ChatMessage` - the styled spans, with each one's
hover and click. Built **lazily**, because most listeners only ever match `unformatted` and
chat is busy; check the flat text first and force the spans only when it is worth it. Hypixel
puts real information in hover text (a player under a nick shows the nick; only the hover
names the account).

#### Input events carry their modifiers

`KeyInputEvent` and `MouseClickEvent` both implement `WithModifiers` - `modifiers` plus named
`ctrl` / `shift` / `alt`. Read those; do **not** ask the keyboard at handling time. The event's
answer is the state at the moment of the input, it costs no Minecraft call, and it keeps
`InputConstants` - which moves between versions - out of feature code.

### Feature lifecycle

```kotlin
object MyFeature : Feature("description") {
    override fun init() {
        register<ClientTickEvent.Post> { /* ... */ }
    }
}
```

`FeatureManager.init(addons)`:

1. ClassGraph-scans `org.blackaddons.core` and `org.blackaddons.platform` plus each addon's package.
2. For each `Feature` subclass **object**, reads its `INSTANCE` field, checks its category, and
   calls `initialize()` - which runs `init()` only. Nothing is enabled yet.
3. For each `FeatureExtension` **object**, the same - in a second pass, after every
   feature, because an extension contributes into a host that has to exist first (see *Feature extensions* below).

Then, in `BlackaddonsClient`:

4. `BlackaddonsConfig.read()` puts every saved setting and enabled flag into the features, without
   switching anything on. A `save()` before this point is refused, so nothing can write defaults
   over the player's file.
5. `FeatureManager.loadAddons()` calls each addon's `onLoad(context)` - it sees the saved settings,
   and no feature has run `onEnable` yet.
6. `FeatureManager.activate()` runs `onEnable` for everything that is on, in one pass, so no
   feature's `onEnable` sees another's default state instead of its saved state.

Category is read from the package: the segment right after `impl`
(`...features.impl.visual.MyToggle` -> `Visual`). It must name something declared -
the host's in `Categories` (`:api`), an addon's in its `Addon.categories`, both held
by `CategoryRegistry`. An undeclared segment is an error at startup; it used to mint
a category, which meant a typo in an addon's package name quietly grew a tab of its
own holding one feature.

A category may declare **sections** - `Dungeons` has General and Floor 7 - and a
feature joins one with `override val section = Categories.Dungeons.Floor7`. A section
carries its category, so naming one decides the tab as well; a feature that names none
falls into the category's first section. The ClickGUI only draws section captions once
two or more of them hold features, so a category can declare its sections long before
it is big enough to show them. `Cheats` is `hideWhenEmpty`: its tab appears only once the
cheat addon fills it.

A `class` (not `object`) fails startup in a dev run and is skipped with a warning in production -
`object`s have the synthetic `INSTANCE` field discovery reads. This is the single most common trap;
see the Pitfalls section of `CREATING_FEATURES.md`.

### Feature extensions

One module adding settings and behavior to a `Feature` that belongs to another -
the cheat edition's cheater map inside the Dungeon Map, its autosolver inside
Simon Says.

```kotlin
object DungeonMapCheats : FeatureExtension(DungeonMap, group = "Cheats") {
    val cheaterMap by boolean("Cheater Map", false)
    override fun init() {
        register<RenderWorldEvent> { /* ... */ }
    }
}
```

**There is no dependency mechanism between features and there is not going to be
one.** A second switch that silently does nothing while a first one is off is a
rule the player cannot see; the answer is that the complement is not a feature.

- Discovered like a feature, initialized after all of them, contributed through
  `Feature.registerSetting` - which nothing calls by hand any more.
- `register<T>` joins the **host's** listener set, so the host's enable/disable
  drives it and no `if (Host.enabled)` guard is needed inside the handler.
- `group` is the default subcategory for settings that name none.
- No switch of its own: an extension that wants one declares a `BooleanSetting`.
- `FeatureRegistrationValidator.rejectionFor(extension)` re-checks the host's
  setting ids afterwards - a collision would make the config unaddressable.

### Settings

```kotlin
private val color by color("Color", Color.WHITE, allowAlpha = true)
    .withDescription("Banner color.")
    .showIf { someBoolean.value }
```

Factories, builders and the delegate operators are members of `SettingProvider`,
implemented by `Feature` and `FeatureExtension`, so a feature needs no imports for
them. The `by` invokes `provideDelegate`, which hands the setting to
`registerSetting`: a feature adds it to its own `configSettings`, an extension
passes it to its host while keeping its own group and key namespace. `getValue`
returns the setting itself, so `color` *is* the setting and `color.value` is the
`T`. Types (in `:api`'s `feature/settings/impl/`, all `open` for addons to build
on): `BooleanSetting`, `NumberSetting`, `ModeSetting`, `ColorSetting`,
`KeybindSetting`, `ButtonSetting`, `StringSetting`, `ListSetting`. Each is
`Savable` (except `ButtonSetting`). A `.group("Label")` sorts settings into
collapsible sub-sections in the ClickGUI; `.hidden()` saves a setting without ever
showing it.

### HUD system

```kotlin
@Suppress("unused")
private val hud = hudElement(name = "My Counter", enabled = { showHud.value }) { example ->
    hudPanel(background = ...) { hudText("text", Color.WHITE) }
}
```

A `HudElement` is stored as **anchor + offset** (a 3x3 anchor grid plus an offset)
rather than raw x/y, so it stays in roughly the same screen region across
resolution and GUI-scale changes. `EventEmitters` walks
`FeatureManager.hudElements` each frame during the HUD draw pass and calls
`renderElement`. It also offers every mouse press to them (`HudElement.offerClick`): an element can be
clicked through the game chat, or over any screen when it sets `drawsOverScreens`, and reads the
cursor in its own content space from `pointer`.

**`renderElement` applies `UiScaling.hudFactor` to the element's offset as well as
to its size**, and the offset half is what makes a layout survive a resize. An
offset is in Minecraft's units, which are absolute: at a pinned GUI Scale a
1080-tall window is 540 units and a 510-tall one is 255, so an element 60 units
below the top goes from an eleventh of the way down to a quarter, and a HUD
arranged on one window collapses toward the middle on another. Scaling the offset
by the same factor makes the position `offset / reference` - a fixed share of the
screen, independent of the window, by construction.

**Nothing on a draw path writes to `offsetX`/`offsetY`.** `x`/`y` apply the scale
and an on-screen clamp *on the way out*; `setPosition` is the only writer and
divides the scale back out. A clamp written back destroys a placement the first
time an element is too big for the window, and growing the window does not undo
it - which is what it did until 2026-09-06.

**An element returns a measured tree, not a size.** `content(example)` hands back a
`LayoutNode`, and `renderElement` measures it, *then* resolves the anchor, *then*
pushes the transform and draws it. That order is the point: it is why a
CENTER/RIGHT-anchored element no longer trails a frame behind when its content
resizes, and why the editor's outline cannot disagree with what was drawn. The
element-owns-its-own-arithmetic contract is the same one the widget toolkit uses -
a feature that needs more than the DSL offers returns its own `LayoutNode` (or a
`Widget`, which additionally hit-tests itself) and takes on that correctness.

The trap the split creates, and the one thing to remember: the tree is *built*
before it is positioned, so drawing during construction escapes the transform. The
`cell` leaf and the `faded` decorator exist so it doesn't have to.

The HUD editor (`HudEditorScreen`) sets `HudEditorMode.active` so every element
shows for placement: drag to move (re-anchors on drop), wheel to scale, corner
handle to resize per axis. Names and handles draw in a second pass, over every
element's content, and only for the selected and hovered ones.

### Scaling: `UiScaling`

Two multipliers, one per space, written by the `Scaling` setting and read by the
toolkit while drawing (the `HudFontContext.globalFont` shape - suppliers, not
values, so a change is visible on the frame it is made).

- **`interfaceFactor`** multiplies `UiManager.scaleOf(panel)`, on top of the
  reference-GUI-Scale normalization, for normalized panels only. Its automatic part
  is `realPixelHeight / 1080`.
- **`hudFactor`** composes with each element's own `scaleX`/`scaleY`. Its automatic
  part is `scaledHeight / 540` - **Minecraft's units, not pixels**, and that
  division is exactly what makes the answer a constant share of the screen at any
  GUI Scale.

The price of that division is real and is why this is a setting rather than the
law: with Auto Scale on, the HUD stops following Minecraft's GUI Scale, so raising
that grows the hotbar and leaves ours alone. Turning the setting off collapses
`hudFactor` to the manual multiplier (default 1.0), which is Minecraft's own
behavior exactly.

**`UiManager` latches `interfaceFactor` from a press to its release.** The
Interface Scale slider lives inside a panel whose transform it sets, so reading it
live is a feedback loop - the thumb moves the track under the cursor. Minecraft's
own GUI Scale slider applies on release for the same reason.

### UI: the ClickGUI

The mod hosts exactly **one** Minecraft `Screen`: `SkijaHostScreen`. It owns no UI
of its own - it renders whatever `UiManager.current` (a `Panel`) sits on top of
its stack and forwards decoded input to it. `UiManager` is a stack: `open` (clear

+ push + show), `push` (e.g. a ColorPicker over the ClickGUI), `pop` (dismiss;
  hide the host when empty).

#### Three draw phases, not two

`SkijaHostScreen` draws, in order:

1. `UiManager.render` - the panel's content. A player model (the Profile Viewer's avatar) queues
   itself here, where the panel draws it.
2. `MinecraftPlayerModelPreview.endFrame()` - closes the model frame: forgets previews nobody asked
   for this frame.
3. `UiManager.renderOverlay` - the panel's popups, then the hover `Tooltip`.

The popups get a phase of their own because they must top everything the panel drew, the avatar
included - an open dropdown under the avatar's legs is the bug this prevents. So
`Panel.renderOverlay` is the phase for anything that must top the screen. **A new popup goes there - not at the end of
`render`.** Drawing it last within the panel only makes it last among what the panel draws.

The ClickGUI itself (`ClickGuiScreen : Panel`) is a sidebar + a content area.
`TabRegistry.buildTabs()` **is** the sidebar layout, written out in the order it is
drawn: our own category tabs, a rule, the screens that are not features (Configs,
Actions, HUD Editor, Cosmetic Editor, About), a rule, one group per addon that
declared categories of its own (a header naming the addon, then its tabs), a rule,
and Dev last. Order used to come from the order `FeatureCategory`'s constants happened
to be declared in, one module away.

Tabs come in four flavors - `ContentTab`, `TriggerTab` (fires a callback), `HeaderTab`
(an addon's name), `DividerTab` (a bare rule). Every rule that ends up leading,
trailing or doubled is dropped, so a block that turns out empty takes its separator
with it - which is what happens to the addon block when no addon declares a category of its own (the cheat addon uses
host categories; Extras declares `Extras`). `Sidebar` therefore positions rows by a prefix sum
rather than `index * TAB_HEIGHT`: a rule is a fraction of a row tall.

### The widget toolkit

Screens compose controls; they don't draw rectangles at computed offsets. The
contract is `Widget : LayoutNode` (`:api`, `ui/widget/`):

- **A widget measures and draws itself** (`width`/`height`/`draw(x, y)`) and **hit-tests itself** against the box it
  stamped while drawing. This is the load
  bearing idea: nothing else re-derives where a control sat in order to route
  input at it. Layout written once, in the draw path.
- **Input mirrors `Panel` 1:1** (`mouseClicked/Released/Dragged/Scrolled`,
  `keyPressed`, `charTyped`), every method defaulting to *not consumed*, so a
  control overrides only what it handles. `focused` has a no-op default - only a
  control that reads keys backs it with a real field.
- **Hover comes from the ambient `UiMouse`**, set once per frame in
  `UiManager.render`. That keeps `draw(x, y)` mouse-free, so a control nested deep
  inside a list resolves hover without the mouse being threaded down to it.
- **`WidgetHost`** dispatches input to registered widgets front-to-back and owns
  the single keyboard focus (click-to-focus, click-nothing-blurs).
- **`ui/layout/Layout.kt`** is the measured `column`/`row`/`cell` tree; `+widget`
  drops a widget straight into it.

Controls: `Button`, `TextInput` (the one text field, over the `TextEditor`
engine, with optional autocomplete), `AnimatedText`, `MovementKeybindSelector`,
`Slider`, `ScrollableList<T>`, `Dropdown`, `ProgressBar`, `ModalPanel`,
plus `SettingWidget` and its subclasses (the ClickGUI's setting rows, which
implement the same contract).

**`Toasts`** (`ui/toast/`) is the one control that lives outside a `Panel`: short notices whose
stack is a `HudElement` (`Toasts.element`, owned by the `Notifications` settings feature), so the
player places it in the HUD editor. The HUD pass draws it with no screen open, and
`Toasts.render()` from the screen pass draws it over one. A `ToastKind` picks the icon and color,
and a draining bottom line shows the time left. Use them for what the player should notice but need
not act on; anything they must re-read or click belongs in chat, which is a log.

**The palette is `Theme`, and the brand lives there too.** `Theme.BRAND_FROM`/`BRAND_TO` are the
name gradient's endpoints and `Theme.ACCENT` is drawn from the same family, so the ClickGUI and
the mod's voice in chat (`Brand`, which reads them) cannot drift apart. Never hardcode a color
that is meant to be "ours".

Two places stay hand-positioned **on purpose**: the feature rows' animated
expand/collapse in `FeatureCategoryTabController` (variable-height stateful
animation, not a static tree) and `HudEditorScreen` (a free-form drag canvas where
the user picks the coordinates).

#### The node canvas

`NodeCanvas` (`:api`, `ui/node/`) is a Blender-style node editor as one `Widget`:
a `NodeGraph` of nodes and wires, a `NodeCamera` (wheel zoom about the cursor,
eased; middle/right-drag pan with coast), box select, socket-to-socket wiring and
a `GraphHistory` that makes every edit undoable. It is built to stay cheap at
thousands of nodes, and the reasons are worth keeping when changing it:

- **Nothing off screen is touched.** Nodes and wires live in `SpatialGrid`s (uniform 256-unit cells); drawing and
  hit-testing query the view or the cursor,
  never the whole graph.
- **Detail drops with zoom** (`NodeLod`, with hysteresis): full nodes above 45%,
  no labels or subtitles above 25%, flat boxes above 15%, colored blocks and
  straight wires below. Text is skipped once it would be under 5 physical pixels.
- **Batches are cached per tile.** Wires at every zoom, and node boxes when zoomed
  out, are filed into 1024-unit tiles (`TiledShapes`), one `ShapePath` per tile and
  color. A frame is a few dozen draws however big the graph is; an edit rebuilds
  only the tiles it touched; an idle frame builds nothing.
- **A drag is a layer.** Dragged nodes and the wires between them are drawn from one
  cached shape at an offset; only wires crossing the edge of the selection bend per
  frame. The move is committed to the graph once, on release.
- **The scene is recorded.** Wires and nodes are drawn through a `RenderLayer`
  covering the view plus a third of it on each side, re-recorded only when the graph,
  the detail tier or the zoom bucket changes, or a pan leaves the region. Outlines,
  highlights, the hovered socket and a drag are drawn live on top - so hovering,
  selecting, idling and short pans replay one recording.

A host supplies the meaning: node payloads, a `connectionFilter` for its own wiring
rules, and callbacks for double-click (`onActivate`), right-click (`onContextMenu`)
and edits (`onEdited`).

`NodeCanvas` itself is the public face - hit-testing and the undoable edits. Behind it,
`NodeCanvasInput` runs the gestures, `NodeDrag` a drag and its drop into groups,
`NodePusher` the push-out between groups and nodes, `NodePropertyEditor` the in-place
rows, and `NodeCanvasPainter` the drawing, with the node body, grid, minimap and theme
colors in classes of their own.

### The other drawing path: `ScreenDraw` and container overlays

A `ContainerOverlay` (e.g. the Extras storage browser) does **not** use the
toolkit, and can't. It draws through `Platform.screenDraw` - vanilla GUI drawing
on the screen's own `GuiGraphics`, passed as an opaque `ctx` and valid only during
that screen's render pass. It needs vanilla-only behavior: the real item tooltip
with its lore, redrawing the container's inventory at the slots' actual positions,
and physically moving native chest slots so clicks fall through to vanilla item
moves. `Render`/Skija cannot substitute any of that, and `LayoutNode.draw` calls
`Render`, so the toolkit simply doesn't reach here.

Don't "fix" this by giving `Renderer` a vanilla-`GuiGraphics` backend: it has no
rounded corners, gradients or custom fonts, so the same API would silently produce
a different look. Two honest APIs beat one lying one.

### Addon system

```kotlin
// In an addon mod's source tree:
object MyAddon : Addon {
    override val id = "myaddon"
    override fun onLoad(context: AddonContext) { /* optional setup */
    }
}
```

```json
"entrypoints": {
  "blackaddons:addon": [
    {
      "value": "com.example.myaddon.MyAddon",
      "adapter": "kotlin"
    }
  ]
}
```

At startup `BlackaddonsClient` fetches the `blackaddons:addon` entrypoints, pairs each
with the mod id of the jar that declared it (`AddonRegistration`) and passes the list
to `FeatureManager.init`, which adds each addon's package to the ClassGraph scan and
discovers its `Feature` objects. The cheat edition is exactly this pattern; a
standalone addon (like Extras) is the same, shipped as its own remapped jar dropped
into `mods/`.

#### Two ids, and only one of them means anything

`Addon.id` is **self-declared**. It namespaces logs and config keys, and nothing else

- an addon can put any string there, so it can never decide what an addon is allowed
  to do. The host warns when it doesn't match the mod id, because the usual cause is a
  typo and the symptom is config saved somewhere the author isn't looking.

`AddonContext.addonId` is the **loader's** id for the jar that declared the entrypoint.
That one can't be forged from inside the addon, and Fabric refuses to run two mods
sharing an id, so no addon can impersonate another in the same client.

#### Sign in with BlackAddons

`context.requestAssertion(nonce) { assertion -> }` hands back a short-lived signed
`Assertion` that the player owns their Minecraft account, which an addon sends to **its
own** backend; that backend verifies it against our JWKS instead of calling us per
sign-in. It returns at once and calls back on the client thread - there is no blocking
form, since proving ownership can take a Mojang round trip and a login first.

The network facades follow the same rule: `Platform.http.get/send/bytes` take a callback
that runs on the client thread. Their `*Blocking` twins are for a chain of requests that
already runs inside `OffThread`, and throw if called on the client thread.

There is deliberately no `audience` parameter: the audience *is* `addonId`. An addon
that could name the audience could mint a valid assertion for somebody else's backend,
which is the entire hole this closes. For the same reason the context is handed to one
addon and is not retrievable by id from anywhere - a capability you can ask for by name
is one you can ask for on another addon's behalf.

> **If you ship per-Minecraft-version jars, use one mod id across all of them.** The
> audience is the mod id, so `myaddon-263` and `myaddon-262` would be two different
> relying parties and your backend would have to accept both. Both first-party addons
> use a single id (`blackaddons-cheat`, `blackaddons-extras`) on every platform; only
> the jar's `archivesName` carries the version. Nothing loads two platform jars at
> once, so the ids can't collide. One exception: in the shipped cheat jar the cheat addon rides the
> core manifest, so its loader id - `AddonContext`'s identity and the assertion audience - is
> `blackaddons`; `blackaddons-cheat` is its id only in a dev run.

#### Terrain and movement without mixins

Mixins can't be loaded once the game runs, so an addon ships none: every hook it could need
lives in the platform behind `:api`. For anything that plans over the world (a pathfinder, a
puzzle solver) that means:

- **Events:** `ChunkEvent.Load/Unload` (Fabric's chunk callbacks) and `BlockChangeEvent`, posted
  from `ClientLevel.setBlocksDirty` - the one call every real client-side change goes through,
  server update or prediction. Its `before`/`after` terrain is built on first read.
- **Bulk reads:** `WorldHost.snapshot(corner, opposite)` and `chunkSnapshot(x, z)` copy a box in one
  client-thread pass into a `TerrainSnapshot` - palette indices per cell, one `BlockTerrain` per
  distinct state (id, properties, collision boxes, fluid, climbable, damaging, slowing), computed
  from the real shapes and the game's own path types, never from a table of ids. Cells in chunks
  the client lacks read `UNLOADED`, not air. One block per `blockIdAt` hop is the wrong tool for
  thousands of cells.
- **Fit tests:** `WorldHost.collides(box)` plus `PlayerHost.width`/`stepHeight`.
- **Moving the player:** `MovementHost.drive(owner, priority) { keys }`. All drives stay live and
  each tick the strongest one that steers wins, so a weaker drive resumes by itself once a stronger
  one stops steering (null). `hold(key, owner)` adds a key on top, shared like a mouse hold.
  `MovementInputEvent` carries the player's own keys; drives and holds apply after its listeners.
- **Replanning:** `LatestJob` - one worker, a new job cancels the running one.
- **Storage:** `AddonContext.dataDir`, a private folder keyed by the loader mod id. Its layout is
  the addon's own (a SkyBlock cache is better keyed by location than by server).

#### An addon in its own repository

An addon does not have to live in this tree. `:api` - and only `:api` - is published to
the GitLab Maven registry as `org.blackaddons:blackaddons-api`, so a separate repo can
compile against the contract as an artifact:

```kotlin
repositories {
    // The mod project's own endpoint, by numeric id - what GitLab's Packages page hands out. The
    // group endpoint reads several publishing projects through one URL; only this one publishes,
    // so it buys nothing.
    maven("https://gitlab.blackaddons.com/api/v4/projects/17/packages/maven") {
        name = "BlackAddons"
        // Only our artifacts come from here; everything else keeps resolving where it always did.
        content { includeGroup("org.blackaddons") }
    }
}

dependencies {
    compileOnly("org.blackaddons:blackaddons-api:<version>")   // a release, or <mod_version>-SNAPSHOT for main
}
```

The registry is public: no account, token or credentials block is needed to read it, from a
developer's machine or from any CI.

`compileOnly`, never `implementation`: the host mod has these classes loaded already, and a
second copy riding inside the addon jar would shadow them. It is the same rule the in-tree
Extras module and the Auth addon (in its own repository) follow, for the same reason.

`main` publishes `<version>-SNAPSHOT` on every commit and a release publishes the plain
version, so an addon can track the contract while it is being written and pin to a release
when it ships.

**Only `:api` is published, and that is the design, not a gap.** An addon that needs
something from `:mod:core` is asking for a host internal, and the answer is a facade on
`Platform` rather than a second published artifact - `Platform.http` and `Platform.config`
exist because the Auth addon needed exactly that. The bundled cheat edition is the one
module that genuinely reaches into `:mod:core` (dungeon tracking, the map, `FeatureManager`),
which is why it ships from this tree and not from a repo of its own.

### Actions subsystem

The actions engine (`org.blackaddons.core.actions`) enables automated triggers and sequences:

- **Chat Actions**: Match incoming chat messages (plain text or regex) to execute steps or play alerts.
- **Waypoint Actions**: Trigger sequences upon entering/exiting spherical or cylindrical 3D world waypoints, with
  optional grouping and area/floor/boss filters.
- **Shortcuts & Dynamic Aliases**: Keybinds or client command aliases (`/b alias add <name> <cmd>`) that execute action
  step chains. Registered aliases dynamically sync into the client command dispatcher
  (`Platform.commands.syncDynamicAliases`) for client-side syntax highlighting and tab completion.
- **Location Actions**: Area transition triggers that evaluate location filters (island, dungeon floor, boss room, F7
  phase) with configurable delay and cooldowns. A location action can instead fire on a dungeon event (run start, blood
  door, boss room, an F7 phase, run end) while its filters match.
- **WAIT steps** wait for time, a position, a chat line, a dungeon event or a boss bar, with an optional timeout that
  either lets the chain go on or stops it. Steps run "after previous" (once every earlier step has finished) or
  alongside it.
- **Flow control**: `Repeat` (a count, or while a condition holds) and `If` open a block that its `End` closes;
  `Stop Steps` ends the run it belongs to. Conditions check the location filters, being near a point, an item in hand or
  the hotbar, or the dungeon class. Blocks are a flat list read by `ActionBlocks`; a loop goes round at most once per
  tick, so a loop with nothing to wait on cannot hang the game.
- **Graph view** (`ui/screen/actions/graph/`): the active profile as a node graph on the `NodeCanvas` - each trigger a
  node with a `Run` output, each step wired after the one before it, an `If`/`Repeat` a node whose `Next` output is what
  follows its `End`, like any step's, and whose `Do` (`Loop`) output under it is its body, laid out on the row below.
  The profile stays the source of truth: `ActionGraphBuilder` reads the flat lists into the graph, and
  `ActionGraphCompiler` writes every edit straight back - same step objects, `End` markers regenerated from structure -
  so the executor and the list screens always see the graph's state. An `End` that closes nothing is a no-op at run time
  and gets no node; a block left open stays open only where that reads the same (last at the top level). Node positions,
  the view and chains not yet wired to a trigger are saved per profile in `actions/graph/<profile id>.json`, apart from
  the profile itself.

### Dungeon tracking & lifecycle events

`DungeonTracker` (`org.blackaddons.core.dungeon`) parses tablist data, boss messages, and game state to manage run state
and dispatch typed events:

- `DungeonRunStartedEvent`, `DungeonRunEndedEvent`
- `DungeonBossEnterEvent`
- `DungeonDoorOpenedEvent` (Blood and Wither doors)
- `DungeonPlayerDeathEvent`
- `F7PhaseStartEvent` (from each boss's opening line; the Wither King from his boss bar)

Addons read the run through the `:api` facade `Dungeon` (`org.blackaddons.api.skyblock.dungeon`), bound once
to core's `CoreDungeon`: `Dungeon.run` (clock, keys, puzzles, `bossPhase`), `Dungeon.team`, `Dungeon.score`
and `Dungeon.layout` (rooms, doors, `roomAt`, `currentRoom`, `mimicRoom`). A room also gives its `secretSpots` and
converts room-local positions (`toWorld` / `toLocal`), the coordinates room data and routes are written in. All
read-only and read on the client thread; the map is widened only through `MapOverride`.

The party (`Party` in `api/hypixel`) comes from Hypixel's Mod API `hypixel:party_info`, asked again on party notices
and server hops (`CoreParty`). `PartyInfoChannel` in the platform owns the channel, unless the Hypixel Mod API mod
is installed: that mod registers the same channel, so the request then goes through it. `BossBarEvent` (`api/event/world`) carries the server's
boss bars.

### Component sanitization

`ComponentSanitizer` intercepts server-supplied translatable and keybind text components before rendering in signs and
anvils to prevent remote servers from probing installed client mods via formatting or translation callbacks.

---

## Known limitations

- **Addons ship events, not mixins.** By design - the addon contract is
  events-only. A missing hook is a signal to add the event (and its mixin) to the
  platform, not to expose mixins. You can make a mixin in the addon, but you will
  lose version-neutrality and will have to maintain multiple minecraft versions.
- **No hot reload.** ClassGraph runs once. After adding or renaming a feature
  class, restart the game.
- **No isolation between addons.** A Java mod can do anything; the addon API is
  trust + curation, not a sandbox. The auto-disable-on-throw behavior (see the
  event system) is the one guardrail.
- **`Setting.value` writes can be lossy.** `NumberSetting` clamps and snaps to
  step; `ModeSetting` ignores values not in its list. Don't assume `s.value = x`
  reads back exactly `x`.
