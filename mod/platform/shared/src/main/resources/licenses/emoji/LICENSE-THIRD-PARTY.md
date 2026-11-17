# Third-party emoji

## Twemoji - `assets/blackaddons/textures/font/emoji_*.png`, `assets/blackaddons/font/emoji.json`

Emoji graphics by Twitter, Inc and other contributors, from the maintained fork at
https://github.com/jdecked/twemoji (version 17.0.2).
Licensed under **CC-BY 4.0** (https://creativecommons.org/licenses/by/4.0/).

Changed from the originals: scaled to 32 px, packed into atlas sheets and reduced to a 256-colour palette
per sheet, by the `generateEmoji` Gradle task (`buildSrc/src/main/kotlin/GenerateEmoji.kt`).

## Discord emoji names - `assets/blackaddons/emoji/catalog.json`

The shortcodes and categories come from the `discord-emoji` package
(https://github.com/xCykrix/discord_emoji, version 2.5.3), MIT License,
Copyright (c) 2020 Samuel Voeller, Copyright (c) 2016-2020 Marek Kulik.
