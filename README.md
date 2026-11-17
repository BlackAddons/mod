# BlackAddons

A Fabric client modification for **Minecraft 26.3 and 26.2**, focused on Hypixel Skyblock
- dungeons, quality-of-life, HUD, cosmetics, and more - with a clean in-game
ClickGUI and an extensible addon system.

> Source is available to read, not to copy into other projects (see [Licensing](#licensing)).

### I totally didn't make docs with AI. Totally. Trust me bro.
### Docs mostly for [AtkLxve](https://github.com/AtkLxve) and his neuroslop

## Editions

BlackAddons ships as two jars from one source tree. Install **one**, never both.

| Edition   | Contents                                                                                                             |
|-----------|----------------------------------------------------------------------------------------------------------------------|
| **Legit** | All quality-of-life, HUD, and Skyblock features. No cheat features.                                                  |
| **Cheat** | Everything in Legit **plus** cheat features, loaded through the same addon entrypoint a third-party addon would use. |

Both share the same mod id; the Mods list distinguishes them by name
("BlackAddons" vs "BlackAddons (Cheat Edition)").

## Features

Features are organized into categories, shown as tabs in the ClickGUI:

- **Dungeons** - Skyblock dungeon helpers.
- **Visual** - rendering and world visuals.
- **Interface** - HUD and screen quality-of-life.
- **Chat** - chat filtering and tweaks.
- **Client** - client-side behavior.
- **Settings** - global client preferences.
- **Cheats** - cheat-edition only.
- **Dev** - developer tools.
- **Extras** - an optional standalone addon with Skyblock (SkyblockAddons-style)
  features.

Open the ClickGUI with the configured keybind, toggle features, and drag HUD
elements into place with the HUD editor.

## Requirements & installation

1. **Minecraft 26.3 or 26.2** with **[Fabric Loader](https://fabricmc.net/)** - take the jar built for your version.
2. **[Fabric API](https://modrinth.com/mod/fabric-api)** and
   **[Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)**.
3. Download a BlackAddons jar from [Releases](https://gitlab.blackaddons.com/blackaddons/blackaddons-reimagined/-/releases)
   and drop it into your `mods/` folder.

## Building from source

Requires JDK 25. See [CONTRIBUTING.md](CONTRIBUTING.md) for the
full setup. Quick start:

```sh
gradle build-all          # every edition
gradle run-26.3-cheat     # launch the dev client, cheat addon loaded
```

Jars are written to `mod/platform/<version>/build/libs/` (legit + cheat) and
`addons/extras/<version>/build/libs/` (extras); every task exists for `26.3` and `26.2`.

## Addons

BlackAddons is extensible. An addon is a Fabric mod that declares a
`blackaddons:addon` entrypoint; the host discovers it at startup and loads its
features - the same mechanism the cheat edition uses. See
[CREATING_FEATURES.md](documentation/CREATING_FEATURES.md) and [ARCHITECTURE.md](documentation/ARCHITECTURE.md).

An addon can live in its own repository: `blackaddons-api` is published to our GitLab
Maven registry, so it compiles against the contract without this source tree. Start with
[ADDONS.md](documentation/ADDONS.md): it walks from the addon template to a released jar.

## Licensing

- **Source**: [PolyForm Strict License 1.0.0](LICENSE.md) with the
  [additional permissions](PERMISSIONS.md) the team grants. In short, for
  **noncommercial** purposes only: you may use the mod, fork it and share your
  fork as long as it keeps the BlackAddons name and credit and is not passed off
  as yours or as official; you may build addons on the published API; you may
  train machine-learning models on the code. Copying any part of it into another
  mod or project needs the team's written permission.
- **Compiled mod**: additionally governed by the [EULA](EULA.md).
- **Contributions**: covered by the [Contributor License Agreement](CLA.md).

Use of the mod is at your own risk; you are responsible for complying with the
rules of any server you connect to. See the EULA for details.

## Contributing

Merge requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first -
note that contributions require agreeing to the [CLA](CLA.md) and following
Conventional Commits.
