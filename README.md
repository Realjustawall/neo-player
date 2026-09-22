# NEO PLAYER

![NEO PLAYER mark](app/src/main/res/drawable/ic_neo_mark.xml)

**0.8Alpha** (`0.8.0-alpha`, versionCode 8) — a modern local-first Android music player focused on offline playback, local intelligence, advanced transitions, per-track personalization, privacy, and a contextual Spotify-style UX.

NEO PLAYER keeps the complete original player capability set while reorganizing features into the context where they are expected: Home, Search, Your Library, Create, Now Playing, and Settings. Existing library, queue, playlists, favorites, categories, lyrics, themes, audio effects, search, widget, playback controls, collection tools, NEO+, Track+, and Offline Pro capabilities remain available.

> Current release: **v0.8.0-alpha — NEO PLAYER 0.8Alpha**
>
> [Download NEO-PLAYER-0.8Alpha-release.apk](https://github.com/Realjustawall/neo-player/releases/download/v0.8.0-alpha/NEO-PLAYER-0.8Alpha-release.apk)
> [Open the v0.8.0-alpha release](https://github.com/Realjustawall/neo-player/releases/tag/v0.8.0-alpha)
>
> The release workflow rejects any APK that is not larger than the complete 0.7Alpha package.

## 0.8Alpha highlights

- persisted theme and accent are applied before the first Compose frame, without the old launch-color flash
- compact Now Playing actions and queue item menus fit Persian, English, and narrow screens
- Local Radio has a Home shortcut beside Settings and continuously refreshed host timing for synchronized playback
- audio effects are opt-in and every dependent switch, preset, band, and slider is disabled while the equalizer is off
- reliable live English/Persian switching, one-level Back behavior, complete Liked Songs, QR joining, and listener management remain available
- all existing NEO+, Track+, Offline Pro, playback, library, lyrics, playlist and visual capabilities retained

## 0.6Alpha foundation

### Contextual Spotify-style UX

- Home, Search, Your Library, Create and Now Playing form the primary navigation and interaction model
- track-specific actions are grouped in Now Playing rather than scattered across unrelated launchers
- Track appearance, Lyrics AI, recommendations, audio analysis and advanced visuals are available from the current-track context
- playlist management, playlist folders and advanced playlist tools are available from Your Library
- Search history and suggestion controls are available from Search
- library configuration, playback/audio controls, storage/cache, backup and Strict Offline controls are available from Settings
- NEO+, Track+ and Offline Pro capabilities are preserved while their entry points are contextual instead of being separate feature dumps
- Create provides playlist/category creation from the main navigation flow

### Search and theme behavior

- Search opens with the local song library immediately visible
- typing filters a pre-normalized in-memory search index without issuing a database query for every keystroke
- light, dark, system and AMOLED themes propagate through the app surface, dialogs, sheets, contextual panels and system bars
- accent and per-track visual overrides remain available

### Playback and audio

- Media3/ExoPlayer foreground MediaSession playback
- background, notification, lock-screen, Bluetooth, headset, and Android Auto-compatible media controls
- persistent queue, shuffle, repeat, seeking, playback speed, play-next and add-to-queue
- **true overlapping dual-decoder crossfade** with safe fallback
- **gapless playback preference** for compatible files/codecs
- **advanced local AutoMix** using BPM, beat grid, beat phase, phrase boundaries, musical key and Camelot compatibility
- local playback analysis for BPM, key, mood, energy, valence, danceability, dynamic range and spectral features
- **ReplayGain/R128-aware normalization** when tags are available, with local-analysis fallback
- normalization and per-track audio effects remain active from the playback service even when the Activity is closed
- per-track EQ, custom bands, bass boost, virtualizer and loudness profiles

### Track+ visual experience

- custom artwork for individual songs
- independent per-song static background image
- background blur and opacity controls
- local looping Canvas video per track
- Canvas crop/fit, start/end trim and playback speed controls
- artwork-derived or manually selected per-track theme colors
- waveform, bars, pulse and playback-aligned local FFT/spectrum visualizers
- Reduce Motion-aware animation controls

### Lyrics and offline AI

- plain lyrics and synchronized LRC support
- sidecar `.lrc` / `.txt` discovery
- manual editing and timestamping
- translation and romanization layers
- **offline AI transcription using Vosk** for English and Persian
- word timestamps and local karaoke highlighting
- forced alignment for timing user-corrected/manual lyrics without automatically overwriting them
- compatible Vosk ZIP model import
- verified English/Persian model packs can be prepared on demand and then work fully offline
- optional **Full-Offline build** can still embed both Vosk models using `-PNEO_EMBED_VOSK_MODELS=true`
- Strict Offline mode prevents model/provider network access when enabled

### Offline-first smart features

- **Strict Offline Mode**
- **Offline Backup** generated from local listening history, favorites, skips and recommendation signals
- Mood and Genre filtering for offline backup/recommendations
- local recommendation feedback and deterministic offline scoring
- smart mixes/radio without an account or remote catalog

### Playlists and organization

- playlist search
- sorting by Custom, Title, Artist, Album, Date Added or Duration
- ascending/descending order
- per-playlist List/Grid preference
- custom playlist ordering
- playlist folders including nested folders
- playlist/folder reorder
- pinning playlists, albums and artists
- global Hide/Unhide
- playlist-scoped Hide/Unhide
- playlist filters for Genre, Mood, Key, BPM, Year and Duration
- playlist total duration
- M3U8 import/export
- source-folder allow-list alongside the original Exclude Folder feature

### Library, cache and search

- MediaStore indexing of device-supported audio formats
- Songs, Albums, Artists, Genres and Folders
- incremental Room-backed library updates
- protection against a transient null MediaStore query wiping a valid cached library
- recent local search history
- cached local search suggestions
- granular cache management for regeneratable analysis/visual data
- Android home-screen playback widget

## Performance work

0.6Alpha retains the performance hardening across playback, library scanning, Room writes, queue persistence and UI state handling:

- timeline/queue state is not rebuilt on every position tick
- seek callbacks are coalesced
- playback controller reconnect uses bounded retry/release handling
- queue persistence is debounced and serialized off hot UI paths
- MediaStore column indexes are cached per cursor
- concurrent rescans are serialized
- library refresh is incremental rather than unconditional delete/insert
- playlist/category batch writes are transactional
- listening history updates are batched and residual listening time is flushed on pause/track change
- analysis work is cancellable, cached and moved off the main thread
- search suggestion indexes are cached/debounced
- normalization target persistence is debounced

## Architecture

```text
NEO UI
  ├── Home / Search / Your Library / Create
  ├── Now Playing / Queue / Lyrics / Settings
  └── contextual feature entry points instead of scattered launchers

NEO+ / Track+ / Offline Pro
  ├── playlist folders, pins, hide/unhide, filters, M3U8
  ├── custom artwork/background/theme/Canvas
  ├── waveform / FFT / spectrum visualizers
  ├── offline AI lyrics + forced alignment
  ├── Offline Backup + local recommendations
  └── advanced local audio analysis

MusicRepository / Room v6
  ├── original library/user-data tables retained
  ├── additive playlist/folder/preference tables
  ├── visual profiles and lyrics transcript cache
  ├── advanced audio analysis / recommendation feedback
  └── MediaStore-backed local library

PlaybackConnection → MediaController → NeoPlaybackService
  ├── primary authoritative ExoPlayer / MediaSession
  ├── secondary transition player for true overlap crossfade
  └── service-side EQ / normalization / transition orchestration
```

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- **Android SDK 36**
- **compileSdk 36 / targetSdk 36**
- Gradle **8.13**
- Android 8.0 / API 26 or newer

## Build

### Debug

```bash
gradle assembleDebug
```

### Release APK + AAB

```bash
gradle testDebugUnitTest
gradle lintRelease
gradle assembleRelease bundleRelease
```

Outputs:

```text
app/build/outputs/apk/release/*.apk
app/build/outputs/bundle/release/*.aab
```

### Full-Offline Release

To embed both English and Persian Vosk models directly into the app:

```bash
gradle -PNEO_EMBED_VOSK_MODELS=true assembleRelease bundleRelease
```

The Full-Offline build is intentionally much larger. The normal Play-oriented Release keeps AI Lyrics capability but prepares verified model packs on demand or accepts a locally imported model ZIP.

## Release

Current release:

**NEO PLAYER 0.8Alpha — `v0.8.0-alpha`**

Published release assets:

- `NEO-PLAYER-0.8Alpha-release.apk`
- `NEO-PLAYER-0.8Alpha-release.aab`
- `RELEASE_ALPHA08_BUILD.txt` — build/signature metadata
- `INSTALL_ALPHA08_KVM_VERIFICATION.txt` — install/launch verification evidence

The release candidate passed JVM unit tests, Android Lint, Release APK/AAB assembly, APK signature verification, 16 KiB zip-alignment verification, Android 14 / API 34 x86_64 KVM installation, app launch, and runtime process-health checks before publishing.

Release builds use R8 code optimization with explicit keep rules for runtime-sensitive libraries such as Vosk/JNA/Room/Media3. Resource shrinking remains disabled.

Optional production signing is supported through release signing environment/secrets. No private keystore or signing password is committed to the repository. When production signing secrets are unavailable, the Alpha release pipeline uses the persistent Alpha test signing key for installable test releases.

## Database migration

The current Room database version is **v7**. Migrations are additive and preserve existing user data. Version 7 adds independent per-track effect switches while retaining all Track+, offline lyrics, recommendation, analysis, ReplayGain and Offline Backup data.

No destructive migration is used for the 0.8Alpha upgrade path.

## Permissions and privacy

NEO PLAYER remains local-first and has no account, advertising SDK or analytics requirement.

| Permission | Why it is used |
|---|---|
| `READ_MEDIA_AUDIO` | Index device audio on Android 13+ |
| `READ_EXTERNAL_STORAGE` | Index audio on older supported Android versions |
| `POST_NOTIFICATIONS` | Media playback notification on Android 13+ |
| `FOREGROUND_SERVICE` | Reliable foreground playback |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground-service type |
| `WAKE_LOCK` | Maintain reliable active playback |
| `INTERNET` | Optional lyrics provider and one-time verified offline-model-pack preparation when Strict Offline is disabled |

Local audio analysis, BPM/key/mood detection, FFT/waveform generation, ReplayGain handling, recommendations and installed AI models run on-device. Strict Offline Mode blocks optional online-provider/model-pack access.

## Release rollback

Before promoting 0.6Alpha to `main`, the previous Alpha 0.5 main was preserved as:

```text
backup-main-before-alpha0.6-2026-09-18
SHA: 4a2573d98af42745f298ce18b0f54be32389605e
```

This provides an exact restore point for the pre-0.6Alpha main branch.

## Verification boundary

CI verifies JVM unit tests, Android Lint, Room/KSP code generation, resource/manifest processing, Release APK assembly and Release AAB assembly. The 0.6Alpha release pipeline additionally verifies signing, 16 KiB zip alignment, real APK installation and application launch on an Android 14 / API 34 x86_64 KVM emulator. Physical-device QA is still useful for manufacturer-specific codecs/audio effects, launcher widgets, Bluetooth/output-route transitions, large libraries, SD-card variants and dual-decoder behavior.

## License and credits

Source is licensed under Apache License 2.0. Major libraries include AndroidX, Jetpack Compose, Media3/ExoPlayer, Room, DataStore, Coil, Vosk, JNA and Android Palette.

English UI: **Made By a Wall**  
Persian UI: **ساخته‌شده توسط یک دیوار**
