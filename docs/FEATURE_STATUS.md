# Alpha 0.2 feature status

This file distinguishes shipped behavior from platform-dependent behavior. NEO PLAYER does not expose placeholder controls.

## Shipped

- MediaStore audio permission, scanning, change observation, manual rescan, minimum-duration filtering, and folder exclusion
- song, album, artist, genre, folder, playlist, and category browsing
- global local search, sorting, collection editing, custom artwork, favorites, listening history, and smart mixes
- Media3 foreground playback, MediaSession, notification/lock-screen/Bluetooth/headset controls, audio focus, noisy-route handling, queue persistence, seek, shuffle, repeat, and speed
- Now Playing gestures, persistent mini player, queue removal/reordering, and sleep timer
- Room migration-safe persistence for NEO-owned data and DataStore appearance/language settings
- plain, LRC-synchronized, translation, and romanization lyric layers; LRC import, timestamp authoring, and optional provider caching
- dark/light/system/AMOLED modes and Orange/Green/Red/Blue/Custard/custom accents
- English and Persian resources with Android RTL layout direction
- equalizer presets, custom bands, bass boost, virtualizer, and loudness enhancement when the device audio stack supports them
- scoped-storage deletion confirmation, file sharing, song details, and safe non-destructive metadata overrides

## Platform-dependent or intentionally constrained

- Media3 applies gapless encoder metadata automatically for compatible tracks; there is no fake on/off switch.
- Virtualizer and some effects are deprecated or manufacturer-dependent and are hidden/degraded safely when unavailable.
- Direct tag rewriting is not performed in Alpha because scoped storage and codec-specific writers can corrupt the source; NEO stores reversible library overrides.
- Online lyrics require a legally configured JSON provider through build properties. No scraping endpoint or credential is committed.
- Crossfade and ReplayGain remain out of Alpha 0.2 because Media3 has no universally correct built-in implementation across local codecs and output routes.
- SD-card visibility follows MediaStore/scoped-storage access granted by Android.

## Verification boundary

GitHub Actions proves compilation, JVM unit tests, Android Lint, and APK assembly. Notification appearance, lock-screen behavior, Bluetooth hardware, vendor audio effects, and 5,000+ song performance still require the physical-device checklist in `docs/QA.md`.
