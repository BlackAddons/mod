# Creating a feature

A short, practical guide to writing a feature for BlackAddons. Read it top to
bottom - each section builds on the last. By the end you'll know how to add
settings, listen to events, render HUDs, ship multi-file features, extend somebody
else's feature, write dev-only diagnostics, and dodge the traps the framework
can't catch for you.

> When in doubt, read a real feature. `PerformanceHud` (in
> `mod/core/.../features/impl/ui/`) is a compact single-file example with no
> Minecraft. `ModlistHider` (in `mod/core/.../features/impl/client/`) is a heavier
> one - many settings, a picker screen, dev-file logging - and is worth reading
> for how it stays out of the platform: the three points where it changes what
> the client discloses are events the platform fires, not mixins that know about
> the feature. `Dumpers` (in `mod/platform/shared/.../features/impl/dev/`) is the
> example of a feature that does touch `net.minecraft.*`, but only through
> version-stable names, so it lives in shared platform source rather than a
> version module.

## 1. The smallest feature

```kotlin
// mod/core/src/main/kotlin/org/blackaddons/core/features/impl/ui/MyToggle.kt
package org.blackaddons.core.features.impl.ui

import org.blackaddons.api.feature.Feature

object MyToggle : Feature("Does absolutely nothing - but it's enabled.")
```

Plus its name and description in every shipped lang file (see [§6](#6-localization)) - the build's
`LocalizationCatalogTest` fails without them:

```json
// mod/core/src/main/resources/lang/blackaddons/en_us.json - and the same keys in ru_ru, es_es
{
  "feature": {
    "mytoggle": {
      "name": "My Toggle",
      "desc": "Does absolutely nothing - but it's enabled."
    }
  }
}
```

That's the whole thing. After a build + restart:

- Open the ClickGUI -> **Interface** category -> **My Toggle** is in the list.
- The display name `My Toggle` is auto-generated from the class name (`MyToggle` -> `My Toggle`).
- The category comes from the package: the segment **right after `impl`**. It has to
  name one declared in `Categories` (`:api`), or the mod refuses the feature at startup
  and says so - see *Picking a category* below.
- The stable id (the config key) defaults to the simple class name `MyToggle`.
  Don't rename the class unless you mean to break saved configs.

### Picking a category

| Category    | Package    | What belongs there                                          |
|-------------|------------|-------------------------------------------------------------|
| `Dungeons`  | `dungeons` | Catacombs: the map, key timers, per-floor helpers           |
| `Visual`    | `visual`   | How the world and your own character are drawn              |
| `Interface` | `ui`       | Minecraft's own interface and controls                      |
| `Chat`      | `chat`     | Reading and writing chat                                    |
| `Client`    | `client`   | What the client tells the server about itself               |
| `Cheats`    | `cheats`   | Cheat-edition only; the tab is hidden without them          |
| `Settings`  | `settings` | Global preferences, usually `@AlwaysActive` and toggle-less |
| `Dev`       | `dev`      | Diagnostics; also where the `@Dev` annotation sends a class |

`Interface`'s package is `ui`, not `interface`, because a package segment cannot be a
Kotlin keyword without backticks. There is no general Skyblock category: the whole mod is
about Skyblock, so a tab saying so would name nothing. What divides these tabs is what a
feature *does*; what divides `Dungeons` from a future `Kuudra` is where you are.

An addon that wants a tab of its own declares the category and lists it in
`Addon.categories` - `ExtrasCategory` in the Extras addon is the worked example. Its tab
then appears under a header naming the addon, below everything the host ships.

Some categories divide further. Name a **section** to land in one, which also decides
the category, so the package no longer has to:

```kotlin
object MyFloor7Helper : Feature("...") {
    override val section = Categories.Dungeons.Floor7
}
```

A feature that names none falls into its category's first section. Captions only appear
once two or more sections in a tab hold features, so a small category looks exactly as
it did before it declared any.

### Warning labels

A feature, or a `FeatureExtension`, can warn the player before they use it:

```kotlin
override val labels = listOf(FeatureLabel.untested("The cancel fix has not been run in game yet."))
```

`untested`, `unsafe` (flaggable) and `broken` show as badges on the row, with the reasons on
hover; switching on a broken feature also shows a toast. Labels only warn - nothing is ever
disabled. The reason is localized under `feature.<id>.label.<kind>`.

Labels that come and go without a release - untested until someone runs it, unsafe until it is
fixed - belong in the backend's `feature_labels` game-data document rather than in code: it adds
labels to a feature for a range of mod versions, and dropping the entry clears them. The copy we
upload is `mod/core/src/test/resources/gamedata/feature_labels.json` (a test checks every id in it
is real); `FeatureLabels` documents the format. The document is public, like all game data.

### Annotations

- `@AlwaysActive` - on regardless of its toggle, listeners always registered. For global preferences.
- `@DevOnly` - not discovered outside a dev run, so it never reaches a release jar. For debug tools.

### Where the file goes

Walk two questions, in order: **is this a cheat?**, then **does it touch
`net.minecraft.*`?**

**Legit features** (ship in both editions):

- No `net.minecraft.*` -> `mod/core/.../features/impl/<category>/` - pure Kotlin.
- Touches `net.minecraft.*` through names stable across versions ->
  `mod/platform/shared/.../features/impl/<category>/` - shared source each platform
  compiles against its own mappings (like `Dumpers` and `TabListToggle`).
- Touches version-specific `net.minecraft.*` ->
  `mod/platform/<version>/.../features/impl/<category>/`, mirrored into each platform.

**Cheat features** (ship only in the cheat-edition jars):

- No MC types -> `addons/cheat/core/.../features/impl/<category>/` - shared skeletons
  and version-neutral cheat features (e.g. `Freecam`, `Perspective`, `AutoClicker`).
- Needs MC types -> through a `Platform` facade (add one if it is missing). The
  `addons/cheat/<version>` modules hold no sources, only the dev `fabric.mod.json`.

**Aim for `shared` and check before you settle for a version folder.** Two platforms are live,
so a file in a version folder is a file to write twice and keep in step forever. Most
MC-touching code turns out to be version-neutral - run `javap` on both dumps and compare the
signature rather than guessing; the whole seam depends on that habit. When only the *location*
of something moved between versions, name it in `McCompat` instead and stay shared.

All cheat modules share the `org.blackaddons.cheat.*` root package, so ClassGraph

- scanning recursively from `CheatAddon`'s package - finds them across the jar
  boundary. Cheats load through the `blackaddons:addon` entrypoint, the same path a third-party
  addon takes - but they also compile against `:mod:core` to extend first-party features, which a
  third-party addon cannot. The framework's own
  scan covers `org.blackaddons.core` and `org.blackaddons.platform` for first-party
  non-cheat features and adds each addon's package at runtime.

> **A feature must be a Kotlin `object`, not a `class`.** Auto-discovery reads the
> synthetic `INSTANCE` field Kotlin generates for `object`s. A `class` fails startup in a dev run
> and is skipped with a warning in production - an easy trap.

## 2. Adding settings

Settings register themselves through property delegation. Declare a property with
`by number(...)` (or any other factory below) and it's automatically added to
`configSettings`, shown in the ClickGUI, and persisted to disk.

The factories and builders (`withDescription`, `group`, `showIf`, `hidden`, ...) are
members of `SettingProvider`, which `Feature` and `FeatureExtension` implement - they
are in scope inside a feature without a single import.

```kotlin
import java.awt.Color

object AutoSomething : Feature("Auto-does something with a delay.") {

    private val delay by number(
        name = "Delay",
        default = 150.0,
        min = 0.0, max = 2000.0, step = 10.0,
        unit = "ms",
    ).withDescription("How long to wait between actions.")

    private val mode by mode(
        name = "Mode",
        default = "fast",
        modes = listOf("fast", "slow", "manual"),
    )

    // Only visible in the GUI when Mode == "manual"
    private val key by keybind("Trigger Key")
        .showIf { mode.value == "manual" }

    private val tint by color("Tint", Color.WHITE, allowAlpha = true)

    private val resetButton by button("Reset stats", action = { /* ... */ })
}
```

**Read a setting with `someSetting.value`**, never bare `someSetting` - the
delegate hands you the `Setting` object itself (so you can also call `.reset()`,
`.isVisible`, etc.).

> [!tip]
> **Checking Keybinds.** If you use `KeybindSetting`, query its physical press state inside a tick event or feature loop
using the helper property `someKeybind.isPressed` (returns `true` if held down, supporting both keys and mouse buttons).
This bypasses Minecraft classes and works in version-neutral modules.
>
> **Player Position & Camera.** Query the player's 3D coordinates in core/addon modules via `Platform.player.pos`
(returns a `Vec3d`), and compute distances using `pos.distanceSqr(otherVec)`. For eye coordinates and perspective, use
`Platform.camera` (`eyeX`, `eyeY`, `eyeZ`, `perspective`). For custom camera modes (freecam/third-person), implement
`CameraController` from `org.blackaddons.api.camera` and register with `CameraControllers`.

### Setting types

| Factory     | Type             | Use for                                                   |
|-------------|------------------|-----------------------------------------------------------|
| `boolean()` | `BooleanSetting` | Toggles                                                   |
| `number()`  | `NumberSetting`  | Numeric value with `min`/`max`/`step`/`unit`              |
| `mode()`    | `ModeSetting`    | One of a fixed list of strings                            |
| `color()`   | `ColorSetting`   | RGB or RGBA color (full HSV picker + chroma mode)         |
| `keybind()` | `KeybindSetting` | One key or mouse button, saved by name (`key.keyboard.f`) |
| `button()`  | `ButtonSetting`  | Action callback (not persisted)                           |
| `string()`  | `StringSetting`  | Single-line text input                                    |
| `list()`    | `ListSetting`    | Editable list of strings                                  |

Every type is `open`, so an addon can build on one - Auth's `SsidSetting` is a
`StringSetting` that encrypts what it saves. A custom type is declared through its
own constructor (`by SsidSetting()`); for the short form, give it an extension in
the feature's package: `fun SettingProvider.ssid(...) = SsidSetting(...)`.

### State that is saved but never shown

Feature state edited by a screen of its own - a picked theme id, a file path, a
list filled by a picker - is still a setting, so it saves and resets with the
feature. Mark it `.hidden()`: it gets no row and no search hit. Don't reach for
`showIf { false }` - `showIf` is for a condition that changes.

```kotlin
private val selectedId by string("Selected", BuiltInThemes.default.id).hidden()
```

State that is a whole document rather than a few values - the Minecraft, HUD and world fonts and
their drawing options - belongs in a file of its own instead (`GlobalFontStore`).
Then implement `OwnState`: **Reset to defaults** in a row's right-click menu (`ContextMenu`), or **Reset All** in the
Configs tab, calls `resetSettings()`, which puts every
setting back to its default and then calls `resetOwnState()`. It leaves the on/off state and HUD
placement alone. A feature with neither saved settings nor `OwnState` is not offered the reset.

### The row's right-click menu

A row gets what applies to it, in groups: its own actions, then **Edit HUD** (when it has a HUD
element) and **Copy settings** / **Paste settings**, then **Reset to defaults**. A feature with
nothing to offer opens no menu. Paste shows what would change before it changes anything, and a
masked field such as a session token is never copied or overwritten.

Copied settings are a `Portable` string like a theme, and every kind is imported the same way: a
`PortableKind` turns the document into a diff, and one modal shows it - from the row, from a
screen's Import button, or from `/b import`, which opens whatever kind is on the clipboard. A new
kind of config is one more `PortableKind` in `PortableKinds`.

A feature - or a `FeatureExtension` of it - can put its own actions first:

```kotlin
override fun init() {
    menuAction("Clear Cache", icon = LucideIcons.rotateCcw, danger = true, confirm = "Forget every cached room?") {
        cache.clear()
    }
}
```

`confirm` makes the menu ask first. The label and prompt localize like a setting,
under whoever declared the action: `blackaddons.feature.<declarer>.action.<id>.label`
and `.confirm`, so an extension translates its actions out of its own bundle.

> **Chroma colors.** `ColorSetting.value` is *live*: when the user turns on Chroma
> (toggle + speed slider in the picker) it returns a time-cycling hue, so a
> feature that draws with `color.value` rainbows automatically - no extra code.
> Read `color.baseColor` for the static pick, and `color.colorAt(offset)` for a
> rainbow shifted by a per-element phase.
>
> Declare a text color with `color(..., textChroma = true)` - the picker
> then offers **All** (whole string cycles as one color) vs **By Char** (rainbow
> stepped per character), via `ColorSetting.chromaMode`. Leave `textChroma` off
> (the default) for fills/boxes/world-render colors: they still get whole-color
> chroma, but the picker hides the per-character option. Draw text with
> `Render.chromaText(text, x, y, size, color, font)` (or `hudChromaText(text, color)`
> inside a HUD layout tree) and it honors the mode. The per-character sweep is
> angled (diagonal by default) and **anchored to screen space**, so it's one
> continuous rainbow field shared by every element - the shader-style "single
> mask" look; pass `angleDeg` / `scale` to tune it. All chroma math lives in one
> place (`Chroma` in `api/util/`), so `ColorSetting` and `chromaText` stay
> consistent, and colors sharing a speed stay in phase.

### How a setting row is drawn, and what that asks of you

You declare a setting; the ClickGUI decides how wide it is. Two things about that
are worth knowing when you pick a setting's shape:

- **Every stretching widget in a feature shares one column width** - the widest
  one's natural width. So one setting with a long `unit` or a long mode name
  widens the column for the whole feature. That is deliberate (a ragged left edge
  reads worse than a wide button), but it is a reason to keep a `unit` short:
  `"blocks"` costs seven characters of every slider's track in that feature.
- **A `NumberSetting` reserves room for the *widest* value it can ever show**, and
  the reserve is computed from `min`/`max`/`step`/`unit`, never from the current
  value - otherwise the track would resize as you dragged it. `step = 0.5` reserves
  one decimal place, `step = 0.05` two; nothing past two is ever shown.

A slider answers **Ctrl + wheel**, not a plain wheel - a plain one belongs to the
list it sits in. It also draws a dot at the setting's default and snaps to it
within a few pixels, so a default is always reachable by hand. If you write a
`SettingWidget` of your own whose input is not obvious from looking at it, say so
by overriding `inputHint`; the row appends it to its tooltip.

### Grouping settings

For a feature with many settings, chain `.group("Label")` to sort them into a **collapsible sub-section** (default
collapsed). Ungrouped settings sit in the
feature's top block; groups render after them in first-declared order. It chains
with the rest: `.withDescription(...).showIf { ... }.group("Health")`.

```kotlin
private val barWidth by number("Bar Width", 80.0, 40.0, 160.0, 2.0, "px") // top-level
private val showHpBar by boolean("Health Bar", default = true).group("Health")
private val hpColor by color("Health Color", Color.RED).group("Health")
private val showManaBar by boolean("Mana Bar", default = true).group("Mana")
```

The ClickGUI search box matches setting names and force-opens the group holding a
match.

### Persistence keys - rename safely

The JSON config keys each setting by its `id` (which defaults to its `name`).
Renaming the **display name** without setting an explicit `id` loses the saved
value:

```kotlin
// Old
private val delay by number("Delay", 150.0, ...)
// New display name, same persisted key
private val delay by number("Action Delay", 150.0, ..., id = "Delay")
```

### Setting hooks

`Setting` exposes two lifecycle hooks that custom setting types or implementations can override:

- `onRegister()`: Called when the setting is registered to the feature via delegation. Useful for capturing the initial
  state.
- `reset()`: Resets the setting to its default value. This is open for overrides (e.g. `ColorSetting` overrides it to
  restore its default chroma settings).
- `normalize(value)`: What a write stores - clamped, snapped, validated. Override this rather than `value`, so
  `onChange` keeps working for your type.

A value that works but can get the player flagged is marked, not forbidden. The row turns the danger color and shows
a badge with the reason on hover:

```kotlin
val reach by number("Reach", 4.5, 3.0, 6.0, 0.1).safeRange(3.0..4.5, "UNSAFE", "Past 4.5 the server sees the reach")
val mode by mode("Mode", "Legit", listOf("Legit", "Blatant")).unsafeWhen("BANNABLE", "No hand clicks this way") { it == "Blatant" }
```

The reason is localized under `feature.<feature>.setting.<setting>.unsafe`.

To react to a write, chain `.onChange { new -> ... }`. It runs only when the value actually changes, loading the config
and a reset included - so it also pushes the saved value on startup. Don't subclass a setting just to watch it.

## 3. Subscribing to events

Inside `init()`, use `register<EventType> { ... }`. The framework attaches the
listener when the feature is enabled and detaches it when disabled - you never
manage subscriptions by hand.

```kotlin
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.chat.ChatMessageEvent

object DemoListener : Feature("Reacts to ticks and chat.") {

    override fun init() {
        register<ClientTickEvent.Post> {
            // runs every client tick while the feature is enabled
        }

        register<ChatMessageEvent> {
            if (event.unformatted.startsWith("[BOSS]")) {
                event.cancelled = true   // hides the line from chat
            }
        }
    }
}
```

Inside the lambda the receiver is `EventBus.EventContext<T>`, so `event` is the
instance and `event.cancelled = true` cancels cancellable events.

> [!note]
> **Non-Feature Event Subscriptions.** For always-active background services, managers, and singleton action handlers
(outside a `Feature`), use `EventBus.subscribe<T> { ... }`. Inside a `Feature`, always use `register<T> { ... }` so
listeners unhook cleanly when toggled off.

### Watching chat vs. filtering it

There are two chat events, and picking the wrong one produces a feature that works on your
machine and does nothing on someone else's:

- **`ChatMessageEvent`** - cancellable, fired where chat reaches the GUI. Use it to hide or
  rewrite a line.
- **`ChatReceiveEvent`** - fired off the packet, not cancellable. Use it to **watch** for a
  line, because another mod that hides that line cannot starve it. If your feature triggers on
  something the server says, this is the one you want.

`ChatReceiveEvent` also carries `message: ChatMessage` - the spans, with hover and click. It is
built on first read, so match on `unformatted` first and only touch `message` when the cheap
test already says it is worth it. (Hypixel hides real information in hover text: a player under
a nick shows the nick, and only the hover names the account.)

Note that Hypixel writes legacy `§` codes into the *text*, not into the component's style. So
`unformatted` is stripped for you, but `raw` and every `ChatSpan.text` still contain them -
use `stripFormatting()`, `cleanName()`, or `containsIgnoreCase()` from `org.blackaddons.api.util.*` before matching,
or your `" is "` will meet a `" §eis "`.

### What the server says, ready to use

Before parsing a packet or the screen yourself, look for the event or facade that already does:

- **`ServerJoinEvent(address)` / `ServerLeaveEvent`** (`event.network`) - connecting and leaving. A
  `WorldChangeEvent` still follows every hop.
- **`AreaChangeEvent` / `ProfileChangeEvent`** (`event.skyblock`) - another SkyBlock area or profile. The
  blank moment of a server hop is skipped, so `current` is never null.
- **`TitleEvent.Title` / `TitleEvent.Subtitle`** (`event.chat`) - cancel to hide it.
- **`SoundPlayEvent`** (`event.world`) - the registry id and where it plays; cancel to mute it.
- **`ContainerUpdateEvent.Content` / `.Slot`** (`event.world`) - a menu's items after the client applied them,
  as `ItemInfo`, built only when read. `containerId == 0` is your inventory.
- **`SkyblockStats.current`** (`skyblock.player`) - health, mana, defense, overflow and vitality from the
  action bar, kept between bars. `SkillXpEvent` carries each skill xp gain. To take a stat off the bar
  because you draw it yourself, `ActionBar.rewriteSections(...)` with `StatGlyphs`.
- **`IrcMessageEvent`** (`event.chat`) - a new line in BlackAddons' global chat. Read-only, and never a
  `ChatReceiveEvent`: anyone can write there, so nothing that trusts the server's chat should hear it.
- **`Party`** and **`PartyChangeEvent`** - see section 10.

### A key that switches the feature itself

`onKeybind` only listens while the feature is on. For a hotkey that also turns it back on, declare the key and a hold
switch and call `toggleKey(key, holdMode)` once from `init()`: a press flips the feature, or with hold mode on it is
on only while the key is held (and lets go when a screen opens). For the same press/hold logic on some state of
your own, use `KeyToggle` from `org.blackaddons.api.input`.

### Modifier keys

`KeyInputEvent` and `MouseClickEvent` carry `ctrl` / `shift` / `alt` (and the raw `modifiers`).
Read them off the event rather than asking the keyboard - the event knows what was held at the
moment it happened, and it keeps version-fragile input classes out of your feature.

### Clicking in the world

Every synthetic click goes through `Platform.interaction.submit(action, owner = this) { result -> }`
(`rightClick()` / `leftClick()` are shorthands). The host decides *when* it is sent - in the tick's
own click slot, ahead of the movement packet, and for `UseOn`/`Interact` only once the server has
the aim - so it is safe to submit from any event, `Post` included. Never send interaction packets
or call `MultiPlayerGameMode` yourself: an anticheat rule added to `ClickQueue` then applies to
every feature at once, and a feature that goes around it is the one that gets flagged.

One request per owner is pending at a time; submitting again replaces it, so a feature that decides
every tick can just resubmit. Keep the returned `ClickRequest` if you must not click twice (`isPending`), and react to
the result: `SENT`, `BLOCKED` (something else was in the way - re-aim),
`EXPIRED`, `CANCELLED`. A switched-off feature's requests are cancelled for you.

### Aiming, holding buttons, the camera

Controls held over time follow the same rule as clicks: ask the host, keep what it hands back, and
only ever let go of your own. `Platform.rotation.request(owner = this, silent = ...)` returns an
`Aim` lease; turn through it (`turnToPoint`, `turnTo`, `turnAlong`, `set`) and `release()` it when
done - with `HandBack.Ease(speed)` to turn a silent aim back to the cursor, or `HandBack.KeepView`
to leave the camera where the silent aim was. Never call a global stop: there is none, and the host
decides whose aim is live. Other owners queue behind you in arrival order; `AimPriority.HIGH`
(actions, Align) takes over at once. If the host revoked your lease (`isEnded`), request a new one.
Every turn lands on whole mouse steps (`Platform.rotation.turnStep`), so allow at least that much
when checking "aimed".

`Platform.interaction.hold(MouseButton.ATTACK, owner = this)` is the same for a held button: it is
down while any owner holds it. `CameraControllers.register` is a stack - the last registered owns
the camera, and releasing hands it back to the one below.

### Adding a new event

The events the framework fires live in `:api`'s `event/` package, one subpackage per domain (`chat`, `input`, `render`,
`network`, `world`, `dungeon`; tick and world-change events sit in
`event/` itself). To add a new one:

1. Add a data class. Version-independent events go under the matching
   `api/.../event/<domain>/` (so addons can subscribe); events holding MC types (Packet, Screen, GuiGraphicsExtractor)go
   under
   `mod/platform/shared/.../platform/event/impl/`.
2. Emit it from a mixin in `mod/platform/shared/.../mixin/` when the signature is the same on
   every platform - check with `javap` before assuming - or from the per-platform module when
   it is not. `EventEmitters.kt` is the place if Fabric already exposes the callback.
3. Mixins are auto-registered - see the Pitfalls section; no JSON edit needed.

## 4. HUD elements

To hide part of the vanilla HUD (health, food, armor, scoreboard, ...), don't write a mixin: call
`Platform.vanillaHud.hideWhen(VanillaHudPart.HEALTH) { condition }` once from `init()`. Conditions from several
features add up - any one of them hides the part.

Inside the `object`, declare an `hudElement(...)` and assign it to a `val` (any
name - the property just keeps the element from being garbage-collected; mark it
`@Suppress("unused")` to quiet the linter).

The content lambda takes one argument, `example: Boolean`, and **returns a
`LayoutNode`** - the same measured layout tree the screens use. You never report a
size: the framework measures the tree, which is what keeps the editor's outline,
the anchor math and the drag bounds from disagreeing with what was drawn. For a
plain text panel, `hudPanel { }` is the whole thing:

```kotlin
import org.blackaddons.api.hud.hudPanel
import org.blackaddons.api.hud.hudText
import java.awt.Color

@Suppress("unused")
private val hud = hudElement(
    name = "My Counter",    // must be globally unique across all features
    enabled = { true },     // optional - tie visibility to a setting
    resizable = true,       // optional - false disables resize
) { example ->
    hudPanel(background = Color(0, 0, 0, 110)) {
        hudText(if (example) "Counter: 42" else "Counter: $realValue", Color.WHITE)
    }
}
```

`hudText` draws in the element's font (the global HUD font, or the per-element
override set from the editor's "T" handle) and measures itself, so lines stack
without clipping. `hudChromaText` is its `ColorSetting` twin, honoring chroma.

Beyond those, the full `column`/`row`/`cell` DSL is available - `cell(w, h) { x, y
-> ... }` is the bridge to raw `Render` calls, and `padded` / `background` /
`bordered` / `faded` decorate any node. An element that needs something the DSL
has no word for can return a `LayoutNode` of its own, or a `Widget` (which
additionally hit-tests itself); it then owns the correctness of its own
measurement.

> **Build the tree, draw inside the nodes.** The tree is measured before it is
> positioned, so a bare `Render` call made while *building* lands outside the
> element's transform. Anything that draws goes in a `cell`; anything that changes
> render state (alpha, scissor) goes in a decorator like `faded`.

> Draw only through `Render` and the layout primitives (see `api/.../ui/render/`).
> **Never call BlackSkija or the vanilla `GuiGraphics` directly** - the `Render`
> facade is what keeps the UI portable across Minecraft versions and rendering
> backends.

Beyond text, draw through `Render`: `Render.image(path, x, y, w, h)` draws a whole
texture - including Minecraft's own assets on the classpath, e.g.
`assets/minecraft/textures/item/<id>.png` - and the UV-subregion overload
`Render.image(image, x,y,w,h, srcX,srcY,srcW,srcH, texW,texH, tint)` draws part of
an atlas and tints it. For vanilla-style glyphs use `Fonts.minecraft` (numbers) +
`Fonts.unifont` (symbols). The Extras addon's `StatBars` / `SkillDisplay` /
`core.BarRenderer` are worked examples.

### Several HUDs in one feature

Just declare multiple. Each is a separate registry object with its own
anchor/offset, its own config entry (keyed by `name`), and its own `enabled`
lambda you can tie to a per-element `BooleanSetting`.

```kotlin
private val showFps by boolean("Show FPS", true)

@Suppress("unused")
private val fpsHud = hudElement("FPS Counter", enabled = { showFps.value }) { _ -> /* ... */ }
```

### Size and position are not yours to compute

Two multipliers reach every element before it draws, and a feature must not try to
apply either itself:

- **`scaleX` / `scaleY`** - the player's own size for this element, set in the
  editor.
- **`UiScaling.hudFactor`** - the global HUD scale (the `Scaling` setting), times
  an automatic factor from the screen's size when Auto Scale is on.

`renderElement` composes both, and applies them to the element's **offset** as
well as to its size. That is what makes a HUD layout survive a change of window
size: an offset is stored in Minecraft's units, which are absolute, so without it
everything anchored to the top slides down and everything anchored to the bottom
slides up as the window shrinks. Position ends up a fixed *share* of the screen.

Two consequences worth knowing:

- **`x` / `y` / `lastWidth` / `lastHeight` are the drawn values**, scale included,
  and `x`/`y` are additionally clamped to keep the element on screen. Read them;
  never recompute a position from `offsetX` yourself.
- **The clamp is applied on the way out and never written back.** An offset the
  player set is theirs. Nothing in a draw path may write to `offsetX`/`offsetY` -
  a clamp written back destroys a saved position the first time a window is too
  small for the element, and growing the window does not bring it back.
  `setPosition` is the one place that writes, and it divides the scale back out.

### Editor preview

`example` is `true` only while the HUD Editor is open. Use it to render
placeholder data when the real data is empty (a chat-driven counter that shows `0`
in a dungeon should show `99` in example mode so the element keeps a recognizable
size). While the editor is open, the in-game HUD layer skips your element and the
editor draws it instead, over the background blur - so keep `draw()` pure (no
input/state mutation), since it now runs from the screen render path too.

### Clicks and hover

A HUD element has no cursor in plain gameplay, but it does while the game chat is open (the chat
leaves the HUD uncovered), and an element that sets `drawsOverScreens` has one over any screen - the
notification stack is the one that does. Two hooks on `HudElement`, both in the element's **content
space** (the same coordinates the tree is drawn in, scale already undone):

- **`pointer`** - where the cursor is, or `null`. Set around `content(example)`, so a `cell` reads it
  for a hover state.
- **`mouseClicked(mouse)`** - a press inside the element (`mouse.x`, `mouse.y`, `mouse.isLeft`); return
  `true` to take it, and the screen underneath never sees the click.

The host offers every press to every element, front to back, and only to one drawn in the last few
frames: bounds from an element that stopped drawing do not catch clicks.

## 5. Your own screen

A HUD element draws on top of the game; a **`Panel`** takes it over. Panels are
version-independent - they draw through `Render` and receive input as plain
primitives - and `UiManager` stacks them: `push` puts yours over whatever is
already up (typically the ClickGUI), `pop` dismisses it.

If all you need is to *tell* the player something, you want neither: `Toasts.show("Copied",
detail = ..., kind = ToastKind.SUCCESS)` puts a notice on screen, with no screen and no chat line. Screens are for
things the player came to do.

**Popups go in `renderOverlay`, not at the end of `render`.** `Panel.renderOverlay(mouseX,
mouseY)` is a separate phase that runs after everything the host draws between the panel and
its popups - the Profile Viewer's player model, for one. An open `Dropdown`'s list drawn last
inside `render` is only last among *your* draws, and comes out behind that model.

Both live in `:api`, alongside `Theme` and the widget toolkit, so an
addon builds screens exactly the way the host does.

**Compose controls; don't draw rectangles.** Every control is a `Widget`: it
measures itself, draws itself, and **hit-tests itself** against where it last
drew, so you never compute a control's position twice (once to draw it, once to
route a click at it). Hover comes from the ambient `UiMouse`, so a control nested
deep inside a list still resolves it without the mouse being threaded down.

**Input needs no routing.** Every panel has `widgets` (a `WidgetHost`) and `keys` (a `Keymap`), and
every event reaches them by default - add a control once and it gets clicks, drags, scroll and,
when focused, keys:

```kotlin
class MyScreen : ModalPanel("My Screen") {   // dim + centered card + title + Esc, for free
    private val search = widgets.add(TextInput(placeholder = "Search..."))
    private val done = widgets.add(Button("Done") { UiManager.pop() })
    private val rows = widgets.add(ScrollableList<Entry>().apply {
        drawRow = { entry, _, x, y, w -> drawEntry(entry, x, y, w) }
    })

    init {
        keys.on(Keys.F, ctrl = true) { widgets.focus(search) }   // org.blackaddons.api.input.Keys
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        search.render(contentX, contentY, contentW - 90f, 22f, mouseX, mouseY)
        done.render(contentX + contentW - 80f, contentY, 80f, 24f, mouseX, mouseY)
        rows.render(contentX, contentY + 30f, contentW, contentH - 30f)
    }

    // Not a widget, just drawn: one call makes it clickable and says whether it is hovered.
    private fun drawEntry(entry: Entry, x: Float, y: Float, w: Float) {
        val hovered = widgets.clickable(x, y, w, rows.rowHeight) { open(entry) }
        // ...
    }
}
```

A control of your own implements `InputTarget`. Each handler gets one object, in panel coordinates, as Float:
`mouseClicked(mouse: MouseButtonInput)` and `mouseReleased(mouse)` (`x`, `y`, `button`, `isLeft`/`isRight`),
`mouseDragged(drag: MouseDragInput)` (also `dx`, `dy`), `mouseScrolled(wheel: MouseScrollInput)` (`amount`),
`keyPressed(key: KeyInput)` (`code`, `ctrl`, `shift`, `alt`) and `charTyped(typed: CharInput)`. Hand the same
object to a child; `mouse.at(x, y)` moves it for a child laid out in its own coordinates.

For placing things by hand: `centered(start, size, inner)` is where something `inner` long starts in the middle
of a span, `columnWidth(width, count, gap)` the width of equal columns, and `Render.rect`, `hollowRect`,
`toggleSwitch` and `Tooltip.showOver` take a `Rect` as well as four numbers.

What the host does for you:

- **Pointer capture.** The widget that took the press gets the drags and the release, even off
  itself, and nothing else does - no `dragging` flags to keep in sync.
- **Clickable regions.** `widgets.clickable(x, y, w, h, button) { }` while drawing: last drawn wins
  an overlap, a region is cut to the scissor it was drawn under (a row half scrolled out of a list is
  clickable only where it shows), and one that was not drawn this frame cannot be clicked.
- **Only what shows is clickable.** A widget's `Rect` stamped while a panel draws belongs to that
  frame and to the scissor it was drawn under: a control for another mode, another tab, or scrolled
  out of a form takes no clicks, with no `if (mode == ...)` guard and no `clear()` needed.
- **Overlays first.** Whatever reports `isOverlaying` (an open `Dropdown`, a `TextInput` showing
  suggestions) gets the press and the wheel before anything under it, wherever it was registered.
- **Drag handles.** `widgets.draggable(x, y, w, h, onDrag = ..., onRelease = ...) { x, y -> }` is a
  region that keeps the pointer after the press - a row you drag to reorder.
- **Parts of a screen.** A tab or pane with controls of its own keeps its own `WidgetHost` and the
  screen registers that host like any widget: a host is an `InputTarget` too. Anything with custom
  input implements `InputTarget` (the input half of `Widget`, without the layout part).
- **Keys.** The focused widget first (a text field clears, then blurs, on Escape), then `keys`.
  `keys.on(key, ctrl, shift, alt, onlyIf) { }` - modifiers match exactly, and binding a key again
  replaces the earlier action, which is how a subclass overrides `ModalPanel`'s Escape.

Override an input method only for behavior the screen owns itself, and call `super` for the rest.

**What's in the box** (`org.blackaddons.api.ui.widget`, plus `ui.screen` /
`ui.layout`):

| Piece                                                       | What it is                                                                                                                                                |
|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Button`                                                    | Click target with hover fade, `enabled`, `accent`                                                                                                         |
| `TextInput`                                                 | The one text field - `placeholder`, fixed `prefix` (a hex field's `#`), `maxLength`/`charFilter`, `invalid`, `onEnter`/`onEscape`, optional `suggestions` |
| `AnimatedText`                                              | Character-animated label smoothly interpolating insertions/deletions                                                                                      |
| `MovementKeybindSelector`                                   | Chip grid selector for WASD/Jump/Sprint/Shift movement keys                                                                                               |
| `Slider`                                                    | Owns `value`/`min`/`max`/`step`/`onChange` + drag + scroll; optional `defaultValue` notch                                                                 |
| `ScrollableList<T>`                                         | Owns `items` + the row template `drawRow`; `rowAt`/`rowTop` route clicks                                                                                  |
| `Dropdown`, `MultiSelectDropdown`, `ProgressBar`, `Chevron` | Dropdown selection and multi-select list popups, progress bar, collapsible chevron                                                                        |
| `ModalPanel`                                                | Modal shell: dim, centered card, title, entrance animation, Esc to pop                                                                                    |
| `WidgetHost`                                                | Input dispatch, pointer capture, clickable regions, single keyboard focus; every panel's `widgets`                                                        |
| `Keymap`                                                    | Shortcuts table; every panel's `keys`                                                                                                                     |
| `column`/`row`/`cell` (`ui.layout`)                         | Measured layout tree - stack blocks without hand-computed y-offsets; `+widget` drops a Widget in                                                          |

Reach for `column`/`row` whenever you'd otherwise track a `var y` down the screen.
A `Widget` is a layout node, so it composes into the same tree.

> Writing a control others will use? Implement `Widget`, stamp your interactive
> box into `bounds` while drawing, and hit-test against it. Every input method
> defaults to "not consumed", so override only what you handle; `focused` has a
> no-op default, so only a control that reads keys needs to override it.

Open it from a `ButtonSetting`, so the screen hangs off the feature that owns it
rather than needing a home in the sidebar:

```kotlin
private val configure by button(
    name = "Configure",
    action = { UiManager.push(MyScreen()) },
    label = "Open",   // the word on the button; the row already says "Configure"
)
```

There is no frame to open or close and no `Screen` to register - the host owns the
single Minecraft screen and forwards to whichever panel is on top. `ColorPickerScreen`,
`ListEditorScreen` and (in `:mod:core`) `ModPickerScreen` are worked examples: modal,
centered, `Esc`/Done to pop. `ColorPopover` and `ConfirmPopover` are the small kind: a card
pinned to the control that opened it, no dim, a click off it closes.

> Panels render **only** while the host screen is up. Draw a persistent overlay
> with a HUD element instead.

### Text that doesn't fit

`size` in `Render.text` is an **em size**, not the height of the drawn glyphs - a line is
`ascent + descent` tall, roughly `1.3 * size`. So never center text with `(h - size) / 2`;
use `Render.centerY(top, height, size, font)`, and take a line's real height from
`Render.textHeight(size, font)`.

Horizontally, size a control from `Render.textWidth` rather than a magic constant, and give
same-typed controls in one column a shared width (the widest) so their edges line up. Where a
box genuinely can't grow, reserve the space its neighbor needs and draw with a `maxWidth`:
`Render.text(text, x, y, style, maxWidth = room)` (also `textRight` / `textCentered`) cuts the string
with an ellipsis and shows all of it in a `Tooltip` while hovered. Never let text run under an adjacent
element, and don't scale it down or scroll it to fit. A tooltip of your own goes through
`Tooltip.showOver(x, y, w, h, text)`, which, like `hovered(...)`, ignores the parts a scissor hides.

A page of data (stats, a summary) is built from the `Cards` kit in `ui/layout`: `Cards.card(...)` with
`sectionHeader`, `valueRow`, `stats`, `progressRow`, `note` and `grid` inside, and `Cards.scrolled(...)` for a
page taller than its box. The Profile Viewer is made of these, so such a page looks like ours.

Those are the rules for a **value on one line**. **Prose** (a description, a hint) should grow
downward instead: `Render.wrapText(text, maxWidth, size, font)` returns the lines to draw.

> If a wrapped block also feeds a measured height - a row that grows, a clip rectangle - then
> **one function must be the single source** for both the drawing and the measurement. Compute
> the lines once and derive the height from `lines.size`; two places computing "how tall is this"
> will drift, and the content ends up clipped by a box that disagrees with it.

`Render.textCentered(text, centerX, ...)` centers horizontally, the twin of `Render.centerY`.

### Animations and transitions

The UI toolkit provides two timing and animation utilities in `org.blackaddons.api.ui.anim`:

- `Animation(initial, durationMs, easing)`: Time-based animation using `System.currentTimeMillis()`. Transition toward a
  target value via `.animateTo(target)` and retrieve the current interpolated value with `.get()`.
- `DecayAnimation(initial, halfLifeSeconds)`: Frame-rate-independent exponential decay animation using half-life
  interpolation toward a target value.

A widget that animates owns its `Animation` as a field and calls `animateTo` from its draw, the way
`Button` does for its hover - there is no keyed animation registry to clean up after.
`Render.slider(...)` and `Render.toggleSwitch(...)` draw a slider or a switch at a progress you pass in.

## 6. Localization

Features, settings and categories are localized **by convention** - you never pass a lang key. The
framework builds the key from ids, looks it up in the active language, and with no translation falls
back to the raw string you wrote in code.

**For a module in this repository it is not optional.** Each of core, cheat and extras runs a
`LocalizationCatalogTest` (on `build-all` and in CI) that fails unless:

- `en_us` has a `name` and a `desc` for every feature, and at least one key for every extension;
- every other shipped locale carries **exactly** the English keys - none missing, none extra - with
  the same placeholders (`%s`, `%d`, ...) in each string.

So a new key goes into every locale file, and a removed one comes out of every one. An addon in its own
repository can ship no bundle at all; everything then reads in English.

The lookup is the mod's own (`org.blackaddons.api.util.I18n`), not Minecraft's language system: a
bundle is read straight off the classpath, and the active language is re-read on every lookup, so a
language switch applies without a restart.

### Where the files go

One bundle per module and locale, **outside** `assets/` (that directory is Minecraft's resource pack):

```
mod/core/src/main/resources/lang/blackaddons/<locale>.json
addons/cheat/core/src/main/resources/lang/blackaddons-cheat/<locale>.json
addons/extras/core/src/main/resources/lang/blackaddons-extras/<locale>.json
```

Locales shipped today: `en_us`, `ru_ru`, `es_es`. The directory is the module's id and keeps
the bundles apart inside a fat jar; it is **not** part of the key. Registration is automatic - the core
bundle at startup, an addon's from its declared id - and a module that ships no bundle is fine.

### Keys and the JSON that answers them

Files are **nested**; the loader flattens them under `blackaddons.`. For `object MyToggle` with
`boolean("Show FPS", true)`:

```json
{
  "feature": {
    "mytoggle": {
      "name": "Моя фича",
      "desc": "Описание.",
      "setting": {
        "show_fps": {
          "name": "Показывать FPS",
          "desc": "Подсказка."
        }
      }
    }
  }
}
```

answers `blackaddons.feature.mytoggle.name`, `blackaddons.feature.mytoggle.setting.show_fps.name`, and
so on. The whole convention:

```
feature.<feature>.name | .desc
feature.<feature>.setting.<setting>.name | .desc | .placeholder
feature.<feature>.setting.<setting>.label             # ButtonSetting's button face
feature.<feature>.setting.<setting>.unsafe            # reason shown on an unsafe value
feature.<feature>.setting.<setting>.option.<value>    # ModeSetting option; the saved value stays raw
feature.<feature>.action.<action>.label | .confirm    # row menu actions
setting_group.<group>
category.<category>
section.<category>.<section>
addon.<addon_id>                                      # the addon's sidebar header
```

Every segment is a slug: lowercase, anything outside `[a-z0-9_]` becomes `_` ("Show FPS" ->
`show_fps`). A JSON array of strings joins with newlines, so a long description can be written as
lines. Keys from all bundles share one space, and a key defined twice is logged when the bundles load rather than
silently resolved.

Mirror every key in `en_us.json` even where the text equals the raw string: `en_us` is also the
fallback for a language that lacks the key.

### Strings of your own

Text that is not a feature or setting name - a chat line, a label in your screen - takes one
`I18n.scope` per file rather than a hand-written helper:

```kotlin
private val tr = I18n.scope("blackaddons.my_feature")

Platform.chat.local(tr("walls_done", "Blew %d walls", count))   // blackaddons.my_feature.walls_done
```

Declare it at the top of the file: unlike a function, a `val` has an initialization order, and a
field above it that already calls `tr(...)` would find it unset.

### Addons

An addon's bundle uses the **same `blackaddons.` keys** as the core - `feature.<id>...` for its
features whoever declared them, `category.<id>` for its own categories, `addon.<addon_id>` for its
sidebar header. A setting it contributes to another module's feature keys under the extension that
declared it (see [Localization of a contributed setting](#localization-of-a-contributed-setting)), so an
addon never has to write into the core's keys. Ship no bundle and everything reads in English.

## 7. Multi-file features

When a feature needs helpers, drop them into a subpackage next to it:

```
features/impl/dungeons/
├── AutoGFS.kt              # single-file feature, flat in the category dir
└── bloodblink/             # multi-file feature, its own subpackage
    ├── BloodBlink.kt       # the `object : Feature(...)`
    ├── RoomFinder.kt       # helper, plain class
    └── Executor.kt
```

The category is `dungeons` (the segment right after `impl`) no matter how deep the
file sits - which is also why a subpackage is a feature's own folder and never a
section. Sections are named in code. Helpers don't extend `Feature`, so ClassGraph ignores them;
mark them `internal` so they don't leak.

**A feature subpackage vs a package of its own:**

- Only this feature uses the helper -> keep it in the feature's subpackage.
- Other features could realistically reuse it -> give it a package of its own outside
  `features/` (e.g. `mod/core/.../tps/TpsTracker.kt`, because TPS tracking isn't specific to
  `PerformanceHud`); an addon needs it too -> `:api`.

Don't create the subpackage prematurely: a single-file feature lives flat as
`features/impl/<cat>/Feature.kt`. Wrap it in a folder only when a second file
appears.

## 8. Extending another feature

Sometimes what you are writing is not a feature of its own - it is *more of a
feature the player already has*. The cheat edition's cheater map is part of the
Dungeon Map; its Simon Says autosolver is part of Simon Says. Shipped as separate
features those gave the player two switches where the mechanism has one, and made
the second silently useless whenever the first was off - a dependency between
features, which is a rule nobody can see in the ClickGUI.

**There is deliberately no `requires =` on `Feature`, and there should not be
one.** Declare a `FeatureExtension` instead:

```kotlin
object DungeonMapCheats : FeatureExtension(DungeonMap, group = "Cheats") {

    val cheaterMap by boolean("Cheater Map", false)
        .withDescription("Show all rooms on the map, including undiscovered ones.")

    override fun init() {
        register<RenderWorldEvent> { /* ... */ }
        onKeybind(modeKey) { cheaterMap.value = !cheaterMap.value }
    }
}
```

The settings appear inside the host feature's own row, under the `group` you
named, and everything here lives exactly as long as the host is enabled.

**What you get, and what it costs:**

- **Discovery is the same as a feature's.** A Kotlin `object` under the mod's or
  your addon's root package, found by ClassGraph, initialized before the config is
  read. Nothing is registered by hand - `Addon.onLoad` should not mention it.
  Extensions are initialized in a **second pass, after every feature**, because an
  extension contributes into a host that has to exist first.
- **`register<T>` puts the listener in the host's own set**, so the host's
  enable/disable drives it. Do **not** write `if (Host.enabled)` guards inside
  those handlers - the framework has already answered that. `onKeybind` is a
  member here too and gates on the host's `enabled` by default.
- **`group` is a default**, applied to any setting that does not name its own. Give
  one: it is what keeps a contributed block reading as a block instead of settings
  scattered through somebody else's feature.
- **There is no switch of your own, on purpose.** An extension that wants one
  declares a `BooleanSetting` like anything else, so the player reads it as part of
  the feature they already turned on.
- **Setting ids must not collide with the host's.** They land in the same feature,
  so the config cannot tell two apart. `FeatureRegistrationValidator` refuses the
  extension at startup if they do.

### Localization of a contributed setting

A contributed setting keys its text under the **extension**, not the host:

```
blackaddons.feature.dungeonmapcheats.setting.cheater_map.name
```

That is what lets an addon translate its own contributions out of its own lang
file without writing into the core's key space, where two addons could silently
collide. The mechanism is `Setting.namespace`, set by whoever declared the
setting; you never touch it.

Note the shape of the localization accessors while you are here - they hang off
the **setting**, not off a feature:

```kotlin
setting.translatedName          // not feature.translatedSettingName(setting)
setting.translatedDescription
setting.translatedUnsafeReason
buttonSetting.translatedLabel
```

One authority for the key, because the feature that *shows* a setting is not
always the one that *declared* it.

## 9. Action step types

The steps of chat, waypoint, shortcut and location actions come from step types. The core
ships Wait, HUD Timer, Text Overlay, Send Message, Temp Waypoints and the Repeat / If / End /
Stop Steps blocks (an If runs its True or its False steps, then whatever both join at); the cheat addon adds Rotate, Align, Move Keybinds and the rest. Yours are
declared the same way:

```kotlin
object PingStep : StepType<PingStep.Params>("myaddon:ping") {

    data class Params(var times: Int = 1, var sound: String = "")

    override val displayName: String get() = tr("step_type.ping", "Ping")

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) {
        repeat(params.times) { Platform.feedback.playSound(context.interpolate(params.sound), 1f, 1f) }
    }

    override fun summary(params: Params): String = "${params.sound} x${params.times}"

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        number(tr("times", "Times"), params::times, min = 1.0)
        text(tr("sound", "Sound"), params::sound, "minecraft:block.note_block.pling")
        note(tr("ping_help", "Plays the sound, {player} and {1} work."))
    }
}
```

- **Discovery is a feature's.** An `object` under your addon's root package; nothing to
  register. Unlike a feature it has no switch: saved steps need the type as long as the game runs.
- **`id` is saved in every profile - never rename it.** Prefix it with your mod id. The core's
  own ids are bare (`WAIT`, `ALIGN`) because they predate the registry.
- **`Params` is saved flat beside the common step fields** (`enabled`, `waitForPrevious`,
  `triggeredBy`, `type`, `delaySeconds`) and the step's flags; a param named like one of those
  is refused at startup. Every field needs a default, so the class has a no-arg
  constructor - a field missing from a save reads as that default, which is how a new field
  stays compatible with old saves. Only non-default fields are written.
- **`newParams()` is for new steps only.** Seed it from your settings if you like (Align
  does); old saves never see it.
- **`properties` is the editor.** The graph shows the rows on the node and the step editor
  shows them as a form, so write them once. Rows read and write `params` through the
  accessors - `nodeProperties { }` takes property references. `note` rows show only in the
  form. The rows are read again after every change, so a toggle can show or hide others.
- **Timing.** `busyTicks` keeps later "wait for previous" steps waiting a fixed time;
  `isBusy()` holds them while your own work still runs (an engine, a held key). Both are
  optional.
- **Cancel and step changes arrive as events:** `ActionStepEvent.Started` (before each step),
  `Idle` and `Cancelled`. Stop your work on `Cancelled`. `ActionQueue.cancel()` cancels from
  your side.
- **An on/off option for other types' steps is a `StepFlag`** - an `object` found the same way,
  saved on each step as `"<id>": true`. The cheat edition's "No rotate until next" is one: it
  shows on every step its `appliesTo` accepts and is read back with `ActionStepEvent.Started.has(flag)`.
- **A step whose addon is missing is kept as saved**, shown as missing and skipped, so
  uninstalling an addon never loses a profile's steps.

## 10. Common API Utilities (`org.blackaddons.api.util.*`)

Never hand-roll string manipulation regexes, tick math, or unsafe JSON traversal in your features.
The `:api` module provides shared, tested extensions in `org.blackaddons.api.util.*`:

### Text & Numbers (`StringExt.kt`)

- **`formatDecimals(decimals = 2)`** - Formats `Float` / `Double` using `Locale.ROOT` without boilerplate.
- **`stripFormatting()`** - Strips legacy Minecraft `§` format and color codes.
- **`cleanName()`** - Strips formatting, trims whitespace, and converts to lowercase for clean item/entity matching.
- **`cleanCommand()`** - Trims whitespace and strips leading slashes (`/`) for command and alias handling.
- **`containsIgnoreCase(other)` / `startsWithIgnoreCase(prefix)`** - Case-insensitive checks without intermediate
  lowercase string allocations.
- **`equalsIgnoreCase(other)`** - Case-insensitive equality on nullable strings (`String?`).
- **`splitWhitespace()`** - Splits tokens by whitespace runs using the global `WHITESPACE_REGEX`.
- **`colorize(altColorChar = '&')`** - Replaces `&` color code prefixes with `§`.
- **`truncate(maxLength, ellipsis = "...")`** - Non-UI character-length truncation for toasts and logs.
- **`toRoman()` / `parseRomanOrNull()`** - Formats/parses Roman numerals for dungeon floors and enchantment tiers.

### JSON Navigation (`JsonExt.kt`)

- **`child(key)` / `array(key)`** - Null-tolerant navigation to child objects/arrays without crashing on missing keys.
- **`str(key)` / `int(key)` / `long(key)` / `float(key)` / `double(key)` / `bool(key)`** - Reads primitive values with
  default fallbacks and zero crash risk on bad server responses.
- **`intOrNull(key)` / `longOrNull(key)` / `floatOrNull(key)` / `doubleOrNull(key)`** - Nullable variants for optional
  numerical fields.

### Time & Ticks (`TimeExt.kt`)

- **`Float.secondsToTicks` / `Double.secondsToTicks`** - Converts fractional seconds to whole ticks (20 TPS).
- **`Int.ticksToSeconds`** - Converts game ticks to fractional seconds.
- **`Int.ticksToDuration` / `Duration.inWholeTicks`** - Seamless interop with Kotlin stdlib `Duration`.
- **`TICKS_PER_SECOND` (20)** and **`MS_PER_TICK` (50L)** constants.

### Skyblock data (`org.blackaddons.api.skyblock`, `Platform.mojang`, `Platform.profiles`)

- **`SkyblockLevels.skill(name, xp, cap)` / `catacombs(xp)` / `slayer(xp, vampire)`** - A `SkyblockLevel(level,
  progress, cap)` from experience, on the tables the backend serves. Don't ship your own XP tables.
- **`Election.mayor` / `Election.hasPerk("EZPZ")`** - The SkyBlock mayor in office with their perks and the
  minister, from Hypixel's election resource; null (and `hasPerk` false) until the first answer.
- **`Party.members` / `Party.leader` / `Party.isLeader`** (`org.blackaddons.api.hypixel`) - Your Hypixel party from
  the server's own list, roles included, refreshed on party notices; changes arrive as `PartyChangeEvent`.
- **`event.playerMessage` / `event.channel`** on `ChatReceiveEvent` (and on a `ChatLine`) - Who wrote a chat line,
  in which channel (`ChatChannel.PARTY`, `GUILD`, `PRIVATE`...), and the text after the name. Don't parse
  Hypixel's chat format yourself.
- **`Platform.mojang.byName(name) { ... }` / `byUuid(uuid) { ... }`** - Account lookups through our backend's cache;
  the answer (`Found` / `NotFound` / `Unavailable`) arrives on the client thread.
- **`Platform.profiles.byName(name) { ... }` / `byUuid(uuid) { ... }`** - A player's Skyblock profiles, parsed:
  skills, dungeons (floors, classes, best runs), slayers, networth, pets, and the coop members by name.
  `cached(uuid)` answers without a request; the Profile Viewer shares the same cache. Items are a separate
  `inventory(member) { ... }`, because decoding them takes a second or more; ask only when you will show them. What isn't modeled yet is
  in `ProfileMember.raw` (Hypixel's member JSON). A skill Hypixel adds later still shows up, with `skill == null`,
  so give a `when` over `Skill` an `else`.

```kotlin
Platform.profiles.byName("DreamcastERA20") { lookup ->
    val profile = (lookup as? ProfileLookup.Found)?.profiles?.selected ?: return@byName
    val cata = profile.member.dungeons.catacombsLevel
    val coop = profile.coop.mapNotNull { it.name }  // e.g. [Murz474]
}
```
- **`Platform.gameStats.tps`** - Server ticks per second, null until measured.

### Files (`Disk.kt`)

- **`Disk.write(path, text)` / `Disk.write(path, bytes)`** - Atomic: written to a temporary sibling, then moved over the
  real file, so a crash mid-write keeps the old file instead of a truncated one. Never write a file you care about
  with `Files.writeString` directly.
- **`Disk.write(path) { tmp -> ... }`** - For writers that stream to a path (NBT, images); throw from the block - a
  failed check of what you wrote included - and the old file stays.
- **`Disk.writeAll { file(a) { ... }; file(b) { ... } }`** - Files that belong together: nothing is replaced unless
  every writer returns.
- **`Disk.read(path)`** - The content, or `null` when the file does not exist.

## 11. Dev diagnostics

Ordinary logging: host code uses `Log.main` / `Log.area("x")` (`mod/core/.../log/Log.kt`), an addon its
`MyAddon.log`, named by the loader's mod id (`BLACKADDONS-<id>`; `.area("x")` for a part) so it cannot log as the
host or as another addon. It works from feature objects too, before `onLoad`. When that isn't enough - you want a stream of events in a dedicated
file you can `tail -f` - use `DevFileLogger`:

```kotlin
import org.blackaddons.api.util.dev.DevFileLogger

object MyFeature : Feature(...) {
    private val devLog = DevFileLogger.named("my-feature")

    override fun init() {
        register<SomeEvent> {
            devLog.write("got event ${event.someField}")
        }
    }
}
```

What it handles for you:

- Writes to `<configDir>/dev/logs/my-feature.log` (`configDir` already ends in `blackaddons`), creating the file
  and directory lazily on first write.
- `write()` short-circuits unless `DevFlags.verboseLogging` is on (driven by
  `DevMode -> Verbose Logging`) - no per-call-site `if` guard.
- The first write of each JVM session emits a `--- session start <timestamp> ---`
  header, so runs are visually separated.
- `named()` caches instances: two features calling `named("dev")` share one file
  and one write lock, so lines can't interleave. Use the tagged overload
  `devLog.write(tag = "my-feature", line = "...")` -> `"[my-feature] ..."` when
  correlating features in one file.

For live state, a dev feature with a HUD panel beats a log: `LocationDebugger` and `PartyDebugger` in
`features/impl/dev` show what the host believes right now, next to what the game shows.

**Don't roll your own per-feature logger** - that duplicates ~70 lines of handle
lifecycle, locking, lazy open, and the `DevFlags` gate. **Don't expose "Debug
Logging" / "Clear Log" as feature settings** - one toggle in `DevMode -> Verbose
Logging` controls every dev log; users clear logs by deleting the file.

## 12. Work that blocks - going off the client thread

Anything that talks to the network or the disk must not run on the client thread;
doing so freezes the game for the duration. But a background thread that dies
takes your loading state with it, and the screen waits on it forever.

`OffThread.run` (`org.blackaddons.api.util`, so addons have it too) exists so you cannot get that
wrong. It runs the work on a named daemon thread and calls `onResult` **exactly
once** - with the value, or with whatever `onFailure` makes of a throw:

```kotlin
OffThread.run(
    name = "pv-status",                              // thread is "blackaddons-pv-status"
    work = { PlayerStatusSource.fetch(uuid) },       // blocking; runs off the client thread
    onFailure = { PlayerStatus.Unavailable },        // a throw becomes a value you can draw
    onResult = { status = it },                      // publish it; render picks it up
)
```

Three rules:

- **The result field is `@Volatile`.** `onResult` runs on the worker, `render`
  reads on the client thread; without it the reader may never see the write.
- **`onResult` only publishes.** It runs on the worker: assign the field and let the next frame do
  the work. `Platform` facades are safe to call from any thread (they hop to the client thread
  themselves), but drawing (`Render`, `Platform.worldRenderer`) only means something inside a frame.
- **Model failure as a value.** `onFailure` returns your own result type, so the
  UI renders an error state instead of an absence. Keep it a plain constructor;
  it is the last line of defense.

To run something *on* the client thread instead - a delayed action, a repeating
check - use `Platform.scheduler` (`schedule` / `every`), which is safe to call
from a worker.

## 13. Pitfalls - read this section

- **Class must be `object`, not `class`** - it fails startup in dev and is skipped in production.
- **Don't hand-roll a `Thread` for a network call.** A throw kills the worker
  with your result never set, and the screen waits on it forever. Use
  `OffThread.run` - see the previous section.
- **HUD `name` must be unique across the whole mod** - it's the config and
  registry key. Prefix with the feature name when there are several (`"Boss
  Ticks"`, `"OoB Ticks"`).
- **Don't change a setting's `name` without keeping `id`** - the JSON key follows
  `id`, which defaults to `name`.
- **Setting writes can be lossy** - `NumberSetting` snaps to `step` and clamps to
  `[min, max]`; `ModeSetting` ignores off-list values. Don't rely on `s.value = x`
  reading back as exactly `x`.
- **`example: Boolean` in `hudElement` is the editor-preview flag, not a toggle.**
- **Don't draw while building a HUD tree** - `content` is measured before it is
  positioned, so a `Render` call made there lands outside the element's transform.
  Put drawing in a `cell`, and render-state changes in a decorator (`faded`).
- **HUD `lastWidth`/`lastHeight` are populated *after* the first render**, so the
  editor can't hit-test an element that has never rendered. Rarely a problem -
  render runs every frame.
- **Draw through `Render`, never BlackSkija or `GuiGraphics` directly.** The
  `Render` facade is the single point the rendering backend is swapped.
- **`EventBus.post()` swallows listener exceptions** (logged, not rethrown). A bug
  in a listener becomes a silently-broken feature with one ERROR line. Grep the
  log for your feature's id; in `DevMode`, flip `Rethrow Listener Exceptions` on.
- **No hot reload.** ClassGraph runs once at client init - restart after adding,
  renaming, or moving a feature class.
- **Mixins are auto-registered but manual to write.** Drop a `*.java` mixin under
  `mod/platform/shared/.../mixin/` - or the per-platform module when the target really
  differs - and `AutoMixinDiscovery` picks it up; no `blackaddons.mixins.json` edit. (Cheat/addon modules ship no
  mixins - the addon contract is events-only on purpose.)
- **A green build proves nothing about a mixin.** Injection points are resolved when the game
  applies them, so a wrong `@At` target compiles happily. `defaultRequire: 1` means it then
  fails loudly at launch rather than silently doing nothing - but you only learn that by
  launching. Prefer an existing event to a new mixin whenever one will do.
- **Anchors, not coordinates.** HUD positions persist as
  `anchorX/anchorY/offsetX/offsetY`. Don't assume raw `x/y` is stable across
  resolution changes.
- **Gate anything network-layer on single-player.** Filtering or rewriting C2S
  payloads (or KnownPacks) will fight the integrated server in single-player and
  deadlock world load at "Preparing spawn area: 16%". Gate the effect on
  `!Platform.world.isSingleplayer` - see `ModlistHider.shouldSpoof`.

## 14. Removing a feature

1. Delete the `.kt` file.
2. Delete its lang entries from **every** locale (`en_us`, `ru_ru`, `es_es`) - a key left in
   one of them fails `LocalizationCatalogTest` as "extra".
3. Optional: remove its entry from the dev runtime config
   (`mod/platform/<version>/run/config/blackaddons/blackaddons.json`) or the live
   `<game>/config/blackaddons/blackaddons.json` - the next `save()` prunes it
   anyway, since the registry no longer knows the id.

No other changes needed: ClassGraph won't find the missing class, the config
loader skips unknown ids, and the HUD registry never registers the deleted
element.

---

*`PerformanceHud.kt` is the live HUD reference; `ModlistHider.kt` covers a wide
settings surface and dev-file logging. If something here disagrees with the code,
trust the code and fix the doc.*
