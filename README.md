# NEO PLAYER

![NEO PLAYER mark](app/src/main/res/drawable/ic_neo_mark.xml)

**Alpha 0.2 development** (`0.2.0-alpha`) — a modern local-first music player for Android. The immutable initial build remains available as the private `v0.1.0-alpha` Release.

NEO PLAYER gives an offline music library an immersive, artwork-led home. There is no account, catalog dependency, tracking, or analytics. Playback and listening data stay on the device.

> Screenshots will be added after the first device QA pass.

## Alpha features

- MediaStore indexing for MP3, FLAC, AAC/M4A, OGG, Opus, WAV, and every audio type supported by the device
- Songs, albums, artists, genres, and folder views with instant bilingual search
- Media3/ExoPlayer playback in a foreground MediaSession service
- Background, notification, lock-screen, Bluetooth, headset, and Android Auto-compatible media controls
- Persistent queue, play next, add to queue, removal, clear, shuffle, repeat, seeking, and playback speed
- Full-screen artwork player and persistent mini player
- Room-backed favorites, playlists, custom categories, listening-history model, and lyrics
- Plain and synchronized LRC lyrics with live line highlighting and a local editor
- Orange, green, red, blue, custard, and arbitrary custom-hex accent themes
- system, dark, light, and AMOLED display modes
- English and Persian resources with native Android RTL layout behavior
- Adaptive, round, monochrome launcher icon and branded splash screen
- Private-by-default operation; internet is contacted only when a user-configured lyrics provider is explicitly requested

## Architecture

The Alpha keeps module count deliberately small while enforcing clear package boundaries:

```text
UI (Compose + MVVM)
  ├── MusicRepository → Room cache and user collections
  ├── MediaStoreScanner → Android shared audio library
  ├── SettingsRepository → Preferences DataStore
  └── PlaybackConnection → MediaController → MediaSessionService → ExoPlayer
```

Room caches query-friendly MediaStore metadata but never duplicates audio. User-created playlists, categories, favorites, lyrics, and history remain stable across rescans. Coroutines keep scanning and database work off the main thread. Lazy Compose lists render large libraries incrementally.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Gradle 8.9 (CI provisions it; a local Gradle installation is sufficient)
- Android 8.0 / API 26 or newer device

## Build

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To reproduce the complete CI gate:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions renames the verified artifact to `NEO-PLAYER-Alpha-0.1.apk`. Alpha uses Android's standard debug signing key; no production signing secret is stored in the repository.

## Permissions

| Permission | Why it is used |
|---|---|
| `READ_MEDIA_AUDIO` | Index device audio on Android 13+ |
| `READ_EXTERNAL_STORAGE` | Index device audio through Android 12L |
| `POST_NOTIFICATIONS` | Show media controls on Android 13+ |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continue explicit playback in the background |
| `WAKE_LOCK` | Maintain reliable active audio playback |

The app does not request broad file management. Deletion uses Android's scoped-storage confirmation sheet. Metadata edits are safe local-library overrides and never rewrite or risk corrupting audio bytes.

## Lyrics

Lyrics pasted into the editor are saved locally. Standard LRC timestamps such as `[01:23.45]` are parsed, sorted, highlighted, and followed against playback position. The data model includes original, translation, and romanization layers for the provider architecture planned after Alpha. No unlicensed scraper or API key is committed.

## Current Alpha boundaries

Alpha 0.2 adds hardware-gated audio effects, sleep timer, drag-equivalent accessible reordering, collection artwork, favorite albums/artists/playlists, safe metadata overrides, optional configured online lyrics, layered lyrics, LRC import and manual timestamping. Crossfade, ReplayGain, embedded-tag extraction and direct embedded-tag writing remain device/codec-sensitive and are not falsely advertised where they cannot be implemented reliably.

## Automation and releases

`.github/workflows/android-alpha.yml` runs unit tests, Android lint, and a debug APK build on pushes and pull requests to `main` and `develop`, and on version tags. Build artifacts are retained privately with the workflow run for 30 days.

## Privacy

NEO PLAYER has no login, analytics SDK, tracking, cloud sync, or network permission. Media metadata, settings, history, collections, and lyrics remain in the app's private on-device storage.

## License and credits

Source is licensed under Apache License 2.0. Major libraries: AndroidX, Jetpack Compose, Media3, ExoPlayer, Room, DataStore, and Coil.

English UI: **Made By a Wall**  
Persian UI: **ساخته‌شده توسط یک دیوار**
