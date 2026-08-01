# Sodium Volt Changelog

## 1.0.1+mc26.2

### Fixes and Improvements

- Fixed Volt Guard retaining render objects between frames and reduced its per-frame memory allocations.
- Removed duplicate particle and block-entity scans by sharing results between Sodium Volt's performance engines.
- Improved Resource-Pack Shield performance with bulk content validation and safer shared read limits.
- Improved Smart FPS battery detection by reusing hardware information instead of recreating it for every check.
- Removed the leftover `Hello Fabric world!` startup message.
