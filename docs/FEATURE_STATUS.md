# Alpha 0.3+ offline feature status

This file distinguishes shipped behavior from platform-dependent behavior. The original Alpha 0.3 feature set remains present; the NEO+ and Collections+ surfaces are additive layers around the existing `NeoPlayerApp`, not a replacement for it.

## Original feature set retained

- MediaStore audio permission, scanning, change observation, manual rescan, minimum-duration filtering, and folder exclusion
- song, album, artist, genre, folder, playlist, and category browsing
- global local search, sorting, collection editing, custom artwork, favorites, listening history, and smart mixes
- Media3 foreground playback, MediaSession, notification/lock-screen/Bluetooth/headset controls, audio focus, noisy-route handling, queue persistence, seek, shuffle, repeat, and speed
- Now Playing gestures, persistent mini player, queue removal/reordering (buttons and long-press drag), and sleep timer
- Room persistence for NEO-owned data and DataStore appearance/language settings
- plain, LRC-synchronized, translation, and romanization lyric layers; LRC import, timestamp authoring, and optional provider caching
- automatic adjacent `.lrc`/`.txt` sidecar discovery where Android exposes the media filesystem path
- dark/light/system/AMOLED modes, the original accent colors, the expanded Purple/Cyan/Pink/Indigo/Teal/Gold palette, and custom accents
- English and Persian resources with Android RTL layout direction
- equalizer presets, custom bands, bass boost, virtualizer, and per-track loudness enhancement when supported by the device audio stack
- per-track audio-effect profiles persisted in Room and restored when a song changes
- scoped-storage deletion confirmation, file sharing, song details, and reversible non-destructive metadata overrides
- cached/debounced unified search, embedded-cover extraction, Coil memory/disk artwork cache, Smart Local Radio, time-of-day mixes, and Paging 3 song browsing

## NEO+ advanced offline features shipped

### Playback and transitions

- true overlapping crossfade using a second ExoPlayer decoder: the current track fades out while the next local track is already playing and fading in, followed by an in-queue handoff to the primary MediaSession player
- the original codec-safe client fade implementation is retained as a fallback path rather than deleted
- user-selectable crossfade duration remains compatible with the original Settings screen
- explicit gapless preference layered on top of Media3's native encoder-metadata-aware gapless playback
- local AutoMix mode using cached BPM estimates, conservative tempo matching, beat-length-aligned transition windows, and gradual playback-speed restoration
- output-volume scaling is composed with sleep-timer fades instead of overwriting the user's active normalization state

### Local loudness normalization and analysis cache

- on-device audio decoding through Android `MediaExtractor`/`MediaCodec`; no audio or analysis leaves the device
- per-track RMS-derived integrated loudness estimate, peak estimate, BPM estimate, and cached normalization gain
- selectable normalization target from -23 to -8 LUFS-style units, with -14 as the default target
- positive gain is combined with the existing `LoudnessEnhancer`; attenuation is applied through the playback output-volume baseline
- user-selected per-track loudness/EQ profiles remain separate from automatic normalization
- analysis results are stored in Room and reused so normal playback does not repeatedly decode the same track
- library analysis is cancellable, yields cooperatively between decoder buffers/tracks, and is performed off the main thread to protect UI/playback responsiveness

### Playlist and collection management

- playlist folders persisted locally, including nested folders with parent reassignment and cycle prevention
- moving playlists into/out of folders without modifying playlist contents
- custom ordering of playlists in the library
- search inside an individual playlist
- per-playlist sort modes: Custom, Title, Artist, Album, Date Added, and Duration
- ascending/descending playlist sorting
- per-playlist List/Grid preference stored independently in Room
- generic pinned collections with independent ordering; playlists, albums, and artists can be pinned without changing the existing Favorite model
- global Hide/Unhide for tracks: hidden songs are excluded from the existing song, Paging, search, album, artist, genre, folder, playlist, and category queries while remaining accessible from the management surface for restoration
- the raw library is retained separately for unhide tools and cached audio analysis

### Library sources, search, cache, and widget

- positive source-folder allow-list in addition to the existing folder exclusion system; exclusions continue to take precedence
- source-folder discovery reads MediaStore independently of the active allow-list, so selecting one folder never makes other available folders impossible to add later
- folder discovery on Android 8/9 derives real parent paths from the legacy MediaStore data column instead of collapsing every source into one generic folder
- recent local searches stored in DataStore
- autocomplete/suggestions from local song title, artist, album, and genre metadata
- Storage & Cache panel with measured temporary-cache size, refresh, and explicit clear-cache action
- clearing cache removes regeneratable app/artwork/analysis cache only; it does not remove music files, playlists, favorites, lyrics, history, categories, or settings
- Android home-screen playback widget with current title/artist plus previous, play/pause, next, and app-open actions
- widget command receiver is not exported to arbitrary third-party broadcasts
- global and per-playlist List/Grid browsing options are available in the additive collection surfaces

## Performance and stability hardening shipped

- position refreshes no longer rebuild the complete Media3 queue on every UI polling tick
- slider seek commands are coalesced instead of flooding the MediaSession IPC channel
- controller connection failure is recoverable with bounded reconnect backoff and explicit resource release
- queue persistence is debounced and JSON serialization is moved away from the hot application-thread path
- MediaStore column indexes are resolved once per cursor instead of repeatedly for every field of every song
- library rescans are serialized with a coroutine mutex to prevent concurrent destructive work
- Room library refresh is incremental: unchanged tracks are not rewritten and removed IDs are deleted in bounded chunks
- playlist/category bulk operations and reorders are transactional
- listening-history counters use atomic Room updates and paused time is not counted as active listening
- Favorite IDs and Favorite collection keys are exposed to the UI as sets for constant-time membership checks
- EQ/effect persistence and duration-triggered rescans are debounced to avoid write/scan storms during UI interaction
- sidecar lyric file I/O and audio analysis run off the main thread
- track-effect restoration uses cancellation-aware latest-track semantics to prevent a previous track profile from being applied after a rapid transition
- automatic MediaStore rescans preserve the user's configured minimum-duration threshold
- source-folder filtering uses normalized path boundaries instead of unsafe raw prefix matching

## Platform-dependent or intentionally constrained

- true dual-decoder overlap depends on a device being able to decode the two involved local formats simultaneously. NEO keeps the primary MediaSession queue authoritative and preserves a non-overlap fallback implementation in code.
- AutoMix is intentionally conservative: it uses local BPM estimation and bounded tempo matching rather than claiming cloud-quality semantic DJ sequencing or a studio-grade beat grid.
- loudness normalization is a local RMS-derived perceptual-level approximation. It is not advertised as certified EBU R128 or bitstream ReplayGain metadata analysis.
- Media3 can provide encoder-metadata-aware gapless playback only when the source/container/decoder exposes usable gapless information.
- Virtualizer and some platform audio effects are deprecated or manufacturer-dependent and degrade safely when unavailable.
- Direct tag rewriting is still not performed because scoped storage and codec-specific writers can corrupt source audio; reversible library overrides remain the safe model.
- optional online lyrics still require an explicitly configured legal JSON provider. No scraper endpoint or credential is committed.
- SD-card visibility follows MediaStore/scoped-storage access granted by Android.

## Persistence and migration

Database version 4 migrates existing Alpha data forward. Existing songs, favorites, playlists, categories, listening history, lyrics, metadata overrides, favorite collections, and track audio effects are retained. Migration 3→4 extends playlists with optional folder/order fields and creates only additive tables for playlist folders/preferences, pins, hidden tracks, included source folders, and cached audio analysis.

## Verification boundary

GitHub Actions runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug`, then uploads the installable debug APK and lint report. Passing CI proves compilation, JVM unit tests, Android Lint, resource/manifest processing, Room/KSP generation, and APK assembly. Physical-device QA is still required for frame-time/jank measurement, simultaneous-decoder behavior across vendor codecs, Bluetooth/output-route transitions, notification/lock-screen appearance, home-screen widget behavior across launchers, manufacturer audio effects, SD cards, and very large (5,000+ track) real libraries.
