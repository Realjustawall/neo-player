# NEO PLAYER

![NEO PLAYER mark](app/src/main/res/drawable/ic_neo_mark.xml)

**Alpha 0.3+ development** (`0.3.0-alpha`) — a modern local-first music player for Android. The immutable initial build remains available as the private `v0.1.0-alpha` Release.

NEO PLAYER gives an offline music library an immersive, artwork-led home. There is no account, catalog dependency, tracking, analytics, cloud library, or server requirement. Playback, library organization, analysis, and listening data stay on the device.

> Screenshots will be added after the first device QA pass.

## Original Alpha features retained

The advanced offline work is additive. The original Compose application is still rendered and all pre-existing controls remain available.

- MediaStore indexing for MP3, FLAC, AAC/M4A, OGG, Opus, WAV, and every audio type supported by the device
- Songs, albums, artists, genres, and folder views with instant bilingual search
- Media3/ExoPlayer playback in a foreground MediaSession service
- Background, notification, lock-screen, Bluetooth, headset, and Android Auto-compatible media controls
- Persistent queue, play next, add to queue, removal, clear, shuffle, repeat, seeking, and playback speed
- Full-screen artwork player and persistent mini player
- Room-backed favorites, playlists, custom categories, listening history, metadata overrides, and lyrics
- Plain and synchronized LRC lyrics with live line highlighting, local editor, import, manual timestamping, translation, and romanization layers
- Equalizer presets and custom bands, bass boost, virtualizer, per-track loudness enhancement, and per-track effect profiles where Android/vendor audio effects permit them
- Sleep timer, smart local radio, time-of-day mixes, recently played/most played/never played/forgotten-favorite mixes
- Playlist and category artwork, song sharing, scoped-storage delete confirmation, local library metadata editing, and folder exclusion
- Paging-backed title browsing, cached/debounced multi-facet search, and size-aware Coil artwork caching
- Orange, green, red, blue, custard, purple, cyan, pink, indigo, teal, gold, and arbitrary custom-hex accent themes
- system, dark, light, and AMOLED display modes
- English and Persian resources with native Android RTL layout behavior
- Adaptive, round, monochrome launcher icon and branded splash screen
- Private-by-default operation; internet is contacted only when a user-configured lyrics provider is explicitly requested

## NEO+ advanced offline experience

NEO+ adds the local/offline parts of a modern streaming-player experience without introducing accounts or a remote catalog.

### Audio and transitions

- **True overlapping crossfade:** a secondary ExoPlayer decoder starts the next local track while the current track fades out, then the authoritative MediaSession player takes over the already-progressed next track
- the original codec-safe fade path is retained in code as a fallback rather than removed
- **On-device loudness normalization:** local PCM analysis estimates integrated loudness and peak level, caches the result in Room, and applies a configurable target without uploading audio
- automatic normalization stays separate from existing per-track EQ/loudness profiles so old user settings are preserved
- **Local BPM analysis and AutoMix:** cached tempo estimates drive bounded tempo matching and beat-length-aligned transition windows, followed by a gradual return to the user's playback speed
- **Gapless preference:** works with Media3's native encoder-metadata-aware gapless behavior for compatible source files
- library-wide audio analysis is cancellable, off-main-thread, cooperatively yielding, and cached so it does not repeatedly decode already-analyzed songs

### Playlist and collection organization

- search inside an individual playlist
- playlist sorting by Custom, Title, Artist, Album, Date Added, or Duration
- ascending/descending sorting
- independent List/Grid preference for each playlist
- persistent custom ordering of playlists in the library
- playlist folders, including nested folders and safe parent reassignment with cycle prevention
- moving playlists between folders without altering their tracks
- independent Pin system for playlists, albums, and artists; pinning does not replace or mutate the existing Favorite model
- global Hide/Unhide for songs; hidden tracks leave normal songs/search/album/artist/genre/folder/playlist/category results while remaining recoverable from the management screen
- separate raw-library access is retained specifically for Unhide and cached analysis management

### Library, search, cache, and widget

- **positive source-folder allow-list** in addition to the existing Exclude Folder feature; exclusion still takes precedence
- source folders are discovered directly from MediaStore rather than from an already-filtered Room result, so additional folders remain selectable after an allow-list is active
- Android 8/9 folder discovery derives real parent paths from legacy MediaStore paths
- global List/Grid browsing preference in the additive collection organizer
- recent local search history in DataStore
- autocomplete suggestions from local title, artist, album, and genre metadata
- Storage & Cache panel that measures temporary cache and can clear regeneratable cache without deleting music, playlists, favorites, categories, lyrics, history, or settings
- Android home-screen playback widget with track title/artist, previous, play/pause, next, and app-open actions
- widget command receiver is not exported for arbitrary third-party control

## Performance and lag hardening

Large-library and playback hot paths have been rewritten to reduce unnecessary work while retaining the original feature surface:

- progress refresh no longer rebuilds the complete Media3 queue every polling tick
- seek-slider callbacks are coalesced instead of flooding MediaSession IPC
- controller lifecycle now has failure recovery, bounded reconnect backoff, and explicit release
- queue persistence is debounced and serialization is moved away from the hot UI/application path
- MediaStore column indexes are cached once per scan cursor instead of repeatedly looked up per field/per row
- concurrent rescans are serialized
- Room library refresh is incremental; unchanged songs are not rewritten and deleted IDs are removed in bounded chunks
- playlist/category bulk mutations and reorders are transactional
- history counters use atomic updates and listening time excludes paused intervals
- favorite membership checks use set-backed state for constant-time UI lookup
- EQ persistence and duration-triggered rescans are debounced
- sidecar-lyrics file work and audio analysis run off the main thread
- local analysis yields between decoder buffers and tracks, supports cancellation, and stores results for reuse
- rapid track changes cannot apply a delayed effect profile from the previous track
- automatic MediaStore rescans preserve the user's selected minimum-duration threshold
- folder filtering uses normalized path boundaries rather than unsafe raw-prefix matching

## Architecture

The project deliberately keeps the original application surface while layering the new local-first tools beside it:

```text
Classic UI (NeoPlayerApp / Compose + MVVM)
  ├── existing library, player, settings, lyrics, favorites and collection screens
  └── remains rendered unchanged under additive overlays

NEO+ / Collections+ additive surfaces
  ├── playlist search/sort/grid/folders/order
  ├── pins, hide/unhide, source-folder allow-list
  ├── recent search + local suggestions
  ├── cache/storage management
  └── local audio analysis + normalization controls

MusicRepository
  ├── Room v4 library cache + existing user data
  ├── additive folder/pin/hide/preference/analysis tables
  └── MediaStoreScanner for local files and source discovery

PlaybackConnection → MediaController → NeoPlaybackService
  ├── primary ExoPlayer / authoritative MediaSession
  └── secondary transition ExoPlayer for real overlap crossfade

SettingsRepository → Preferences DataStore
PlaybackWidgetProvider → existing MediaSession
```

Room caches query-friendly MediaStore metadata but never duplicates the user's audio files. Existing playlists, categories, favorites, lyrics, history, metadata overrides, and effect profiles migrate forward. New local tables only add playlist folders/preferences, pins, hidden-song state, source-folder selections, and cached audio analysis.

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

GitHub Actions renames the current development artifact to `NEO-PLAYER-Alpha-0.3.apk` (`v0.1.0-alpha` retains its original Alpha 0.1 artifact name). Alpha uses Android's standard debug signing key; no production signing secret is stored in the repository.

## Permissions

| Permission | Why it is used |
|---|---|
| `READ_MEDIA_AUDIO` | Index device audio on Android 13+ |
| `READ_EXTERNAL_STORAGE` | Index device audio through Android 12L |
| `POST_NOTIFICATIONS` | Show media controls on Android 13+ |
| `FOREGROUND_SERVICE` | Run explicit foreground playback on supported Android versions |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Declare the media-playback foreground-service type |
| `WAKE_LOCK` | Maintain reliable active audio playback |
| `INTERNET` | Contact only a user-configured optional lyrics provider when lyrics are explicitly requested |

The app does not request broad file management. Deletion uses Android's scoped-storage confirmation sheet. Metadata edits are safe local-library overrides and never rewrite or risk corrupting audio bytes.

## Lyrics

Lyrics pasted into the editor are saved locally. Standard LRC timestamps such as `[01:23.45]` are parsed, sorted, highlighted, and followed against playback position. Original, translation, and romanization layers are supported. Sidecar `.lrc`/`.txt` files can be discovered locally. Optional online lyrics use only an explicitly configured JSON provider; no unlicensed scraper endpoint or committed API credential is required.

## Audio-analysis accuracy boundary

NEO's new normalization and AutoMix analysis are intentionally local and dependency-light. Loudness is an RMS-derived perceptual-level estimate and BPM is estimated from a short-time energy envelope. This is useful for consistent personal-library playback and conservative tempo-aware transitions, but it is not advertised as certified EBU R128/ReplayGain analysis or as a studio-grade beat-grid/DJ engine.

True dual-decoder crossfade is implemented, but simultaneous decoding is ultimately device/codec/output-route dependent. Media3 gapless behavior is also source/container/decoder dependent. NEO keeps safe fallback behavior and never removes the user's queue to implement transitions.

## Database migration

Room database version 4 migrates Alpha data forward. Existing songs, favorites, playlists, playlist tracks, categories, category tracks, listening history, lyrics, excluded folders, metadata overrides, favorite collections, and track audio-effect profiles remain intact. Migration 3→4 extends the playlist row with optional folder/order columns and creates additive tables for playlist folders/preferences, pins, hidden songs, included source folders, and cached audio analysis.

## Automation and releases

`.github/workflows/android-alpha.yml` runs unit tests, Android lint, and a debug APK build on pushes to `main`, `develop`, and hardening/fix branches, on pull requests targeting `main` or `develop`, and on version tags. Verified build artifacts and the lint report are retained with the workflow run. Development branch pushes do not publish a release unless a version tag explicitly requests one.

## Verification boundary

CI proves compilation, JVM unit tests, Android Lint, Room/KSP code generation, resource and manifest processing, and APK assembly. Physical-device QA is still necessary to measure actual frame timing/jank on weak hardware, simultaneous-decoder behavior across manufacturer codecs, Bluetooth/output-route transitions, lock-screen/notification UI, home-screen widget behavior across launchers, SD-card variants, vendor audio effects, and very large real libraries.

## Privacy

NEO PLAYER has no login, analytics SDK, tracking, advertising, or cloud sync. The Android `INTERNET` permission exists solely for the optional user-configured lyrics provider; the app does not perform background catalog, analytics, advertising, or telemetry requests. Media metadata, settings, history, collections, pins, hidden-song state, local analysis, and locally saved lyrics remain in on-device storage.

## License and credits

Source is licensed under Apache License 2.0. Major libraries: AndroidX, Jetpack Compose, Media3, ExoPlayer, Room, DataStore, and Coil.

English UI: **Made By a Wall**  
Persian UI: **ساخته‌شده توسط یک دیوار**
