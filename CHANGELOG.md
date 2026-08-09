# Sodium Volt Changelog

## 1.0.2+mc26.2

### New Features

- Added Volt Tuner Profile with Volt Default, Competitive, and Aggressive presets.
- Added safe preset switching that restores default tuning and keeps only Volt Guard enabled when returning to Volt Default.
- Added a yellow `Sodium Volt Loaded (version)` line below Sodium Renderer in the F3 menu.

### Performance Improvements

- Removed duplicate animated-texture traversal and reused Sodium's visibility data.
- Reduced duplicate particle and block-entity scans between Volt Guard, VAPS, and BERP.
- Added normal-load fast paths for Volt Guard, VAPS, BERP, Recovery, Watchdog, Privacy, and VRAM hooks.
- Improved VAPS so normal particle loads use Minecraft's existing extraction pass instead of a separate planning pass.
- Reconnected the BERP block-entity backend and added cached block-entity type classification.
- Stopped detailed VAPS, BERP, Smart FPS, and Volt Recovery statistics work while the Inspector HUD is hidden.
- Reduced Volt Inspector render-thread work with faster percentile selection and cached HUD content.
- Cached profile and server identity data until the active world or server changes.
- Replaced per-tick synchronized profile reads with a lightweight runtime snapshot.
- Improved Smart FPS power checks, runtime snapshots, cancellation, and background-worker shutdown.
- Improved Resource-Pack Shield directory validation, ZIP scanning, and shared stream accounting.
- Reduced Resource-Pack Shield JSON validation and repeated filesystem work.
- Improved APC frame-time percentile calculations and reduced normal-load processing.

### Fixes

- Fixed VRAM Pressure Protection continuing to track allocations without a complete startup baseline.
- Fixed Animated Texture Throttling exemption reloads running more than once or publishing stale results.
- Fixed Volt Guard and VAPS keeping large temporary buffers after being disabled or leaving a world.
- Fixed BERP repeatedly classifying the same block-entity types.
- Fixed Inspector sampling and rebuilding unchanged HUD text unnecessarily.
- Fixed Resource-Pack Shield path-swap, symlink, ZIP mutation, read-limit, and idle-stream handling.
- Fixed Smart FPS and Volt Recovery publishing stale Inspector statistics.
- Fixed the Sodium Volt F3 line becoming duplicated, outdated, or placed above Sodium Renderer.

## 1.0.1+mc26.2

### Fixes and Improvements

- Fixed Volt Guard retaining render objects between frames and reduced its per-frame memory allocations.
- Removed duplicate particle and block-entity scans by sharing results between Sodium Volt's performance engines.
- Improved Resource-Pack Shield performance with bulk content validation and safer shared read limits.
- Improved Smart FPS battery detection by reusing hardware information instead of recreating it for every check.
- Removed the leftover `Hello Fabric world!` startup message.
