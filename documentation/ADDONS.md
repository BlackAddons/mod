# Writing a BlackAddons addon

An addon is a small Fabric mod that sits next to BlackAddons in `mods/`. It compiles against one artifact,
`org.blackaddons:blackaddons-api`, and gets everything else - settings, the ClickGUI tab, HUD editing,
translations, events, the game itself - from the host at runtime. This page is the path from an empty folder to
a released jar. How a single feature is written (settings, events, HUD elements, screens) is
[CREATING_FEATURES.md](CREATING_FEATURES.md); everything in it applies to addons unchanged.

## 1. Start from the template

Copy the `BlackAddons.AddonTemplate` repository. It builds for every Minecraft version the host supports, and its
example feature (`PartyList`) uses a setting, an event and a HUD element. Then rename in four places:

| What | Where | Rule |
|---|---|---|
| `addon_id` | `gradle.properties` | Your Fabric mod id: lowercase, `-` allowed. The same on every Minecraft version. |
| `Addon.id` | your `Addon` object | The same string as `addon_id`. The host warns when they differ. |
| Package | `core/src/main/kotlin/...` and `addon_entrypoint` | Your own, not `org.blackaddons`. |
| Lang folder | `core/src/main/resources/lang/<addon_id>/` | Named after `addon_id`. |

`addon_name`, `addon_description` and `mod_version` fill the manifest. There is one `fabric.mod.json` at the
root; the build writes it into each version's jar.

The layout, and why:

- **`core/`** holds the whole addon. It has no Minecraft on its classpath, only `blackaddons-api`, so an
  `import net.minecraft.*` does not compile. That is what keeps one source tree working on every version.
- **`26.3/`, `26.2/`** each ship `core` with that version's manifest. They are three lines; the shared logic
  is `gradle/platform.gradle.kts`. Adding a Minecraft version is a folder with that file and its properties.

## 2. Where the API comes from

`blackaddons-api` is published to the mod project's public package registry; the template already points at it
(`blackaddons_registry`). `blackaddons_api_version` tracks `-SNAPSHOT` while you write. Pin a plain version before
you ship a jar.

## 3. What the host gives you

Discovery is automatic: every `object` extending `Feature` in your entrypoint's package and below is found at
startup. A feature's package segment after `impl` names its tab, so `features.impl.myaddon` lands in the category
your `Addon.categories` declares with id `myaddon`.

| You want | Use |
|---|---|
| Settings, a HUD element, events while enabled | `Feature`: `boolean(...)`, `hudElement { }`, `register<Event> { }` |
| The game | `Platform.*`: `player`, `world`, `chat`, `skyblock`, `container`, `movement`, `rotation`, `interaction`... |
| Who wrote a chat line, in which channel | `ChatReceiveEvent.playerMessage`, `.channel` |
| Your party | `Party`, `PartyChangeEvent` |
| Live health, mana, defense | `SkyblockStats.current`, `SkillXpEvent` |
| The dungeon run, rooms, secrets | `Dungeon.run`, `.team`, `.score`, `.layout` (`secretSpots`, `toWorld`) |
| Area, profile, server changes | `AreaChangeEvent`, `ProfileChangeEvent`, `ServerJoinEvent`, `ServerLeaveEvent` |
| Titles, sounds, menu items | `TitleEvent`, `SoundPlayEvent`, `ContainerUpdateEvent` |
| HTTP | `Platform.http` (callback on the client thread) |
| Work that blocks | `OffThread`, `LatestJob` |
| Your own files | `AddonContext.dataDir` (handed to `onLoad`) |
| A screen | `Panel` / `ModalPanel`, the widgets, `Cards` for pages of data |
| Translations | `lang/<addon_id>/<locale>.json`, the same `feature.<id>...` keys as the host |
| Signing in to your own backend | `AddonContext.requestAssertion(...)` |

CREATING_FEATURES.md section 3 lists the ready-made events in more detail, and section 10 the helpers.

## 4. Rules the host enforces, and the ones it can't

- **No mixins.** They cannot load into a running game, and two addons patching the same method break each
  other. If a hook is missing, ask for an event in the host.
- **`compileOnly` the API.** The host has those classes loaded; a second copy inside your jar shadows them.
- **Host wiring is off limits.** Anything marked `@HostApi` (`bind`, a few setters) is a compile error unless
  you opt in. Don't: it changes state every addon reads.
- **The client thread.** Events, callbacks and `Platform` reads happen on it. Don't block it: a request or a
  file read goes through `Platform.http` or `OffThread`.
- **Your id is self-declared; your identity is not.** `Addon.id` only names your config and logs.
  `AddonContext.addonId` is the loader's id for your jar, and it is what the host trusts.
- **Copying from BlackAddons needs permission.** Building on the published API is allowed; taking code,
  assets or translations out of the mod repository is not, without the team's written permission
  (`PERMISSIONS.md`).

## 5. Build, test, release

```sh
gradle build-all        # every version; build-26.3 / build-26.2 for one
```

Drop the jar for your Minecraft version into `.minecraft/mods` next to BlackAddons.

To try your addon against an unpublished host change, publish the API locally from the mod repository with
`gradle :api:publishToMavenLocal -Psnapshot` and build the addon with an init script that resolves
`org.blackaddons` from `mavenLocal()` instead of the registry.

The template's CI builds every branch. On the default branch a manual **release** job uploads the jars to the
project's generic package registry and creates a GitLab release tagged `v<mod_version>`.

## 6. When the API changes

`blackaddons-api` keeps a dump of its public surface (`api/api/api.api` in the mod repository), and the host's
build fails when that surface changes without the dump being updated. So every change to what you compile
against is reviewed on purpose. A jar built against an older API keeps working as long as nothing it calls was
removed; pin the version you tested against, and move up when you are ready to test again.
