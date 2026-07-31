# Sodium Volt

<p align="left">
  <img src="src/main/resources/assets/sodium-volt/icon.png" alt="Sodium Volt icon" width="128">
</p>

Sodium Volt is a Fabric addon that expands Sodium with extra performance, security, privacy, stability, and monitoring features.

## Features

### Performance

- **Volt Guard** - Protects frame stability by limiting excessive particle, block-entity, and display-entity rendering. It gives priority to nearby and gameplay-important effects.
- **Adaptive Performance Controller** - Dynamically adjusts selected graphics settings to help reach a target FPS. It includes Balanced, Max Quality, and Max Performance profiles.
- **Visibility-Aware Particle Scheduler** - Prioritizes visible, nearby, and important particles while reducing work from distant, hidden, or repeated decorative particles.
- **Block Entity Render-budgeting** - Limits expensive block-entity rendering in dense areas while keeping nearby, targeted, and recently used block entities responsive.
- **Animated Texture Throttling** *(Experimental)* - Pauses unseen animations and updates distant animations less often. Important vanilla textures and interface textures can stay at full speed.
- **VRAM Pressure Protection** *(Experimental)* - Estimates graphics-memory pressure and can gradually lower render distance before heavy memory pressure causes stuttering or driver instability.
- **Smart FPS** - Reduces FPS while Minecraft is minimized, unfocused, or running on battery. Normal FPS limits return automatically when the condition ends.

### Security

- **Resource-Pack Shield** - Checks selected local and server-downloaded resource packs before they reload.
- **Archive and resource limits** - Limits file counts, archive size, expanded resource size, total read size, and ZIP compression ratios to reduce resource-exhaustion and decompression-bomb risks.
- **Image and JSON checks** - Limits PNG dimensions and pixel counts, and rejects excessively deep or malformed JSON structures.
- **Path protection** - Detects unsafe paths, excessive path depth, linked files, and other suspicious pack layouts.
- **Core shader protection** - Can block untrusted resource packs from overriding Minecraft core shaders.
- **Safe reports and notifications** - Can show fixed warnings and write a small sanitized local report without storing server, account, world, pack, or device names.

### Privacy

- **Privacy Screenshot Mode** - Takes an ordinary F2 screenshot using a temporary privacy-safe render frame without changing saved video or HUD settings.
- **Sensitive overlay hiding** - Can hide chat, the debug overlay, player list, scoreboard, boss bars, titles, subtitles, toasts, saving indicators, and entity name tags from the screenshot.
- **Clean screenshot options** - Can also hide the gameplay HUD, hands, and held item for cleaner images.
- **Screen blocking** - Can refuse privacy screenshots while menus, chat, inventories, loading screens, or other screens are open.
- **Randomized filenames** - Can replace timestamp-based screenshot names with random neutral names.
- **Fail-closed protection** - Can refuse a screenshot when a privacy-safe capture cannot be prepared instead of silently saving an unsafe image.

### Stability

- **Volt Recovery** - Detects unclean previous sessions and can start the next launch with a conservative graphics safe mode.
- **Recovery loop protection** - Limits repeated recovery attempts so the game does not keep applying safe mode forever.
- **Owned setting restoration** - Restores only settings that Volt Recovery changed, preserving later changes made by the player or another controller.
- **GPU Timeout Watchdog** - Watches render-frame heartbeats for possible GPU or renderer stalls while avoiding expected loading, paused, minimized, and resource-reload states.
- **Watchdog recovery handoff** - Can prepare Volt Recovery for the next launch after a confirmed possible render stall.
- **Sanitized stability reports** - Can write bounded local recovery and watchdog reports without private paths, server details, account data, or telemetry.

### Others

- **Volt Inspector** - Provides a configurable in-game HUD with frame-time statistics, chunk activity, scene complexity, particles, animated textures, garbage collection, GPU details, resource reload timing, and simple performance suggestions.
- **Volt Binds** - Adds a native Minecraft keybind for quickly showing or hiding the Volt Inspector HUD.
- **Automatic Context Profiles** - Stores separate graphics profiles for global defaults, single-player saves, and individual multiplayer servers.
- **Private profile identifiers** - Uses bounded salted hashes for saved worlds and servers instead of storing raw world paths or server addresses in the Profiles configuration.
- **Factory Reset** - Resets Volt options to the factory's default in case something goes wrong with your setup.
- **Sodium settings integration** - Adds Sodium Volt pages directly to Sodium's Video Settings screen so every feature can be configured in one place.

## Requirements

- A computer with minecraft and sodium

## License

Sodium Volt is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
