# NEO PLAYER

![NEO PLAYER mark](app/src/main/res/drawable/ic_neo_mark.xml)

**Alpha 0.5** (`0.5.0-alpha`, versionCode 5) — a modern local-first Android music player focused on offline playback, local intelligence, advanced transitions, per-track personalization, and privacy.

NEO PLAYER keeps the complete original player surface and adds new capabilities on top of it. Existing library, queue, playlists, favorites, categories, lyrics, themes, audio effects, search, widget, playback controls, and collection tools remain available.

> Current public release: **v0.5.0-alpha**
>
> Release APK: about **43.7 MiB**  
> Release AAB: about **21.6 MiB**

## Alpha 0.5 highlights

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

### Offline-first Spotify-style features

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

Alpha 0.5 includes additive performance hardening across playback, library scanning, Room writes, queue persistence and UI state handling:

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
Classic NEO UI
  ├── library / search / playlists / categories / favorites
  ├── player / queue / lyrics / settings
  └── remains available under additive layers

NEO+ / Track+
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

**NEO PLAYER Alpha 0.5 — `v0.5.0-alpha`**

The validated normal Release build is approximately:

- APK: **45,810,113 bytes / 43.688 MiB**
- AAB: **22,659,587 bytes / 21.610 MiB**

Release builds use R8 code optimization with explicit keep rules for runtime-sensitive libraries such as Vosk/JNA/Room/Media3. Resource shrinking remains disabled.

Optional production signing is supported through release signing environment/secrets. No private keystore or signing password is committed to the repository.

## Database migration

The current Room database version is **v6**. Migrations are additive and preserve existing user data. New tables/columns cover Track+ visuals, offline lyrics transcripts/models, recommendation feedback, advanced audio analysis, ReplayGain/normalization metadata and Offline Backup-related state.

No destructive migration is used for the Alpha 0.5 upgrade path.

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

Before promoting Alpha 0.5 to `main`, a rollback branch was created:

```text
backup-main-before-alpha0.5-2026-09-17
SHA: 3bd4b16ed7a81d9c786bfd009fb0382145b294b4
```

This provides an exact restore point for the pre-Alpha-0.5 main branch.

## Verification boundary

CI verifies JVM unit tests, Android Lint, Room/KSP code generation, resource/manifest processing, Release APK assembly and Release AAB assembly. Device QA is still useful for manufacturer-specific codecs/audio effects, launcher widgets, Bluetooth/output-route transitions, large libraries, SD-card variants and dual-decoder behavior.

## License and credits

Source is licensed under Apache License 2.0. Major libraries include AndroidX, Jetpack Compose, Media3/ExoPlayer, Room, DataStore, Coil, Vosk, JNA and Android Palette.

English UI: **Made By a Wall**  
Persian UI: **ساخته‌شده توسط یک دیوار**
