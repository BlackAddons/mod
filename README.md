LEGACY - EOL SOON. Join discord to get updates on new version (BlackAddons-Reimagined). BETA is out! Ping any of the developers in discord to get access!
discord.gg/YBDRvNRrrp


# BlackAddons

BlackAddons is a utility and quality-of-life mod for Minecraft, designed to enhance the player experience with advanced personalization, privacy tools, and dungeon utilities. I totally didn't make readme with ai. Totally.

## Features

### Utility

- **Profile Viewer**: Inspect player stats directly in-game without using external sites. View dungeon stats, skills, slayers, inventory, ender chest pages, pets, wardrobe, backpacks, and vault. Use `/b pv` to view your own profile.
- **Dungeon Party Finder**: A dedicated menu for finding and creating dungeon groups. It integrates with external services to provide a more reliable experience than the standard party finder.
- **RNG Tracker**: Automatically tracks rare drops and displays them on screen. Includes options to simulate drops for testing purposes.
- **Bot Integration**: Seamlessly syncs daily stats and integrates with external bots for leaderboard tracking.
- https://github.com/BLACKUM/rtca-bot-hypixel (bot)

### Dungeons

- **Dungeon Map**: Custom minimap with colored room types (entrance, blood, fairy, puzzle, trap, champion, mimic), optional room names, and fully configurable colors/border/background/rounded corners.
- **Water Board Solver**: Timer HUD for the water board puzzle (real fast).
- **TP Maze Solver**: Visually highlights correct teleport pads in the Teleport Pad Maze puzzle, complete with a tracer line to the correct pad.
- **Solo Clears Tracker**: Stores your solo clears locally with time, secrets, map and puzzle details. Features global leaderboards (`/b leaderboard`) and an interactive in-game minimap viewer to inspect your runs directly from the menu.

### Privacy & Mod Hider

- **Spoof Mode**: Diverse control over how your client identifies itself to servers.
    - **Vanilla**: Pretend to be a completely vanilla client.
    - **Modded**: Identify as modded but hide specific mods from the list.
    - **Custom**: Set a custom client brand name, hide mods, and disable payloads.
- **Hide Mods**: Prevent servers from querying your detailed mod list.
- **Payload Control**: Block or whitelist custom payload channels to prevent server-side mod detection hacks and improve security. Yes, even modanouncer from Firmament.

### IRC Chat

A global chat system that lets you communicate with other mod users across different servers. It features an emoji selector and handles media previews directly in the interface.

- **Usage**: Type `#message` in the regular Minecraft chat to send a global message.
- **Conflict Handling**: If another mod is also using the `#` prefix, BlackAddons will take priority. To send a message to the other mod's IRC, use `##message`.
- **Interface**: Use `/b irc` to open the dedicated IRC chat GUI.
- **Configuration**: Can be disabled if u don't like yapping >:(

### Cheats (I don't take any accountability if something goes wrong)

- **AutoSS**: Automatically solves the Simon Says device in F7/M7 with humanized rotations and snaps.
- **FastLeap**: Advanced dungeon leaping with multiple modes:
    - **Door Opener**: Automatically leap to the player who opens wither doors.
    - **Positional**: Leap to specific classes based on your S-room position.
    - **Class Presets**: Configure specific classes for each S-room (S1-S4).
- **Chat Actions**: Custom automation triggered by chat messages. Actions are configurable step by step (swapping slots, using items, rotations, etc.).
- **Waypoint Actions**: Position-based triggers (entry/exit/GUI exit) with step-by-step automation similar to Chat Actions.
- **Note**: Both Chat and Waypoint actions can be used to fully automate phases in F7/M7. (I didn't tell you that)
- **AutoTNT**: Automatically interacts with specific blocks (Cracked Stone Bricks, Smooth Stone Slab, Barrier blocks) using TNT with smart delays and auto-unequip.
- **AutoBM**: Automates buying stuff in kuudra shop (auto ballista mechanic)
- **AutoClicker**: Advanced, highly configurable autoclicker with adjustable min/max CPS, button selection (left/right/both), toggle/hold keybinds, continuous hold click mode, ground-only checks, look-at filters (target specific entities/blocks), and held-item filters. (you can use it for mining, if you are lazy... i didn't tell you that)
- **TP Maze cheat**: Semi-auto navigation for the Teleport Pad Maze puzzle. Automatically rotates/snaps to the correct pad.
- **Freecam**: Detaches the camera from your player for free movement, with configurable speed, scroll sensitivity, toggle/hold keybinding, and an option to still render your hands.
- **Perspective**: Third-person perspective with configurable distance, sensitivity, scroll zoom, and toggle/hold keybind.

### Visuals

- **Visual Utilities**: Remove fire overlay, visually disable nearby particles (within 2 blocks), and hide status effect icons from the HUD/inventory.
- **Fullbright**: Toggle permanent Night Vision for better visibility in dark areas.
- **Custom Text / Vector Text**: Custom Google-font rendering for in-game text with configurable anti-aliasing, shadow, outline, bold, and italic.
- **Custom GUI**: A clean, modern card-based interface for settings with movable and resizable elements.
- **Chat Visual Filters**: Hide chat messages matching patterns (contains / starts-with / exact / regex, optional case-sensitive).
- **Image Preview**: Inline `[Preview]` button on chat links to images/GIFs/Tenor.
- **Notifications**: In-game toast notifications for important events and updates.

## Commands

- `/b`, `/black`, or `/blackaddons`: Open the main settings menu.
- `/b pf`: Open the Dungeon Party Finder.
- `/b hud`: Open the HUD overlay editor.
- `/b leaderboard`: Open the leaderboard screen.
- `/b irc`: Open the IRC chat interface.
- `/b irc on | off | toggle | status`: Control the IRC chat. (basically like /chat p selection but for irc)
- `/b irc msg [message]`: Send a message directly to the IRC general channel.
- `/b pv`: Open the Profile Viewer for yourself.
- `/b pv [player] [force]`: Open the Profile Viewer for a specific player. Use the force argument to refresh cached data.
- `/b preview [url]`: Open a full-screen preview for a direct image or Discord media link.
- `/b ping`: Check your exact ping/latency to the server.
- `/b em` / `/b editmode` `on | off | toggle | status`: Toggle chat / waypoint action triggers.
- `/b commandaliases add [alias] [original command]`: Create a custom command alias.
- `/b commandaliases del [alias]`: Remove a command alias.
- `/b commandaliases list`: List all currently configured aliases.

## Credits

- **Blackum**: Erm that's me hello
- **AtkLxve**: Solo clears (Bad boy)
- **Autismo**: Helped me with rewriting, understanding java and a lot more stuff
- **noamm**: Helping out and other stuff
- **NotYukimii**: Helped out atk with coding his features
- **FoundOstrich**: Helped me with testing stuff
- **adjectiven0un**: Original XP calculation logic and API inspiration from [adjectils](https://adjectils.com/dungeon.html).

## Powered By

- **Player Data**: UUID lookup provided by [PlayerDB](https://playerdb.co/).
- **Hypixel Data**: Official [Hypixel API](https://api.hypixel.net/) for Bazaar prices.
- **Auction House**: Price data provided by [Moulberry's Codes](https://moulberry.codes/).
- **Pricing for stuff like Shiny necron's handle**: Item market data from [Coflnet](https://sky.coflnet.com/).
