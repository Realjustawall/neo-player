# Alpha 0.3 feature status

This file distinguishes shipped behavior from platform-dependent behavior. NEO PLAYER does not expose placeholder controls.

## Shipped

- MediaStore audio permission, scanning, change observation, manual rescan, minimum-duration filtering, and folder exclusion
- song, album, artist, genre, folder, playlist, and category browsing
- global local search, sorting, collection editing, custom artwork, favorites, listening history, and smart mixes
- Media3 foreground playback, MediaSession, notification/lock-screen/Bluetooth/headset controls, audio focus, noisy-route handling, queue persistence, seek, shuffle, repeat, and speed
- Now Playing gestures, persistent mini player, queue removal/reordering (buttons and long-press drag), and sleep timer
- Room migration-safe persistence for NEO-owned data and DataStore appearance/language settings
- plain, LRC-synchronized, translation, and romanization lyric layers; LRC import, timestamp authoring, and optional provider caching
- automatic adjacent .lrc/.txt sidecar discovery where Android exposes the media filesystem path
- dark/light/system/AMOLED modes and Orange/Green/Red/Blue/Custard/custom accents
- English and Persian resources with Android RTL layout direction
- equalizer presets, custom bands, bass boost, virtualizer, and loudness enhancement when the device audio stack supports them
- per-track audio-effect profiles persisted in Room and restored when a song changes
- extended accent palette with Purple, Cyan, Pink, Indigo, Teal, and Gold
- scoped-storage deletion confirmation, file sharing, song details, and safe non-destructive metadata overrides
- debounced cached unified search across songs, albums, artists, genres, playlists, categories, and folders
- embedded-cover extraction with size-aware Coil memory/disk caching to keep artwork correct and scrolling responsive
- Smart Local Radio mixes from artist/genre history and expanded time-of-day mixes
- Paging 3-backed song browsing (80-item pages with prefetch) for the default title view

## Platform-dependent or intentionally constrained

- Media3 applies gapless encoder metadata automatically for compatible tracks; there is no fake on/off switch.
- Virtualizer and some effects are deprecated or manufacturer-dependent and are hidden/degraded safely when unavailable.
- Direct tag rewriting is not performed in Alpha because scoped storage and codec-specific writers can corrupt the source; NEO stores reversible library overrides.
- Online lyrics require a legally configured JSON provider through build properties. No scraping endpoint or credential is committed.
- Crossfade uses a codec-safe software fade-out before the next item; true overlapping dual-decoder crossfade is not forced because Media3 has no universally correct implementation across all local codecs and output routes.
- ReplayGain remains out of Alpha 0.3 until per-file loudness metadata can be calculated reliably without blocking playback.
- SD-card visibility follows MediaStore/scoped-storage access granted by Android.

## Verification boundary

GitHub Actions proves compilation, JVM unit tests, Android Lint, and APK assembly. Notification appearance, lock-screen behavior, Bluetooth hardware, vendor audio effects, and 5,000+ song performance still require the physical-device checklist in `docs/QA.md`.
