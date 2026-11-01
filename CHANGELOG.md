# Sodium Volt Changelog

## 1.0.4+mc26.3

### Minecraft 26.3 migration

- Target Minecraft 26.3, Sodium Fabric 0.9.2+mc26.3, and Fabric API 0.165.0+26.3.
- Require Fabric Loader 0.19.5 or newer, with no Loader upper version bound.
- Migrate key bindings and window-state handling to Minecraft's SDL input implementation.
- Adapt VRAM accounting and GPU inspection to RenderPearl and the updated buffer constructors.
- Update privacy screenshot capture, first-person hand rendering, and debug overlay hooks.
- Hide the separately rendered three-dimensional debug crosshair during privacy captures when debug or gameplay overlays are hidden.
- Connect block-entity budgeting and extraction caching to Sodium's actual block-entity rendering path.
- Defer automatic context profiles and profile capture while Volt Recovery owns a safe graphics profile, preserving recovery settings until restoration.
- Preserve Resource-Pack Shield protection with the new resource supplier streams, including cleanup after opening failures.
- Verify generated dependency metadata and supported/rejected version boundaries during `check`.
- Update the Gradle wrapper to 9.6.0 with its pinned distribution checksum.

## 1.0.3+mc26.2

### Performance Improvements

- Removed duplicate particle eligibility checks from Volt Guard's normal fallback planning.
- Reduced Volt Guard particle selection work when both priority options are disabled.
- Delayed Volt Guard particle ranking until the eligible particle count exceeds the budget.

### Fixes

- Fixed Volt Guard entity filtering causing severe slowdowns under heavy entity loads.
- Fixed crafted texture exemption lists disabling Animated Texture Throttling globally.
- Fixed Animated Texture Throttling not fully respecting changes to the exemption-list setting.
- Fixed Resource-Pack Shield settings changes silently disabling protection for loaded packs.
- Fixed malformed ZIP layouts bypassing Resource-Pack Shield archive limits.

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
