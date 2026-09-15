# Changelog

## 0.2.0-alpha — In development

- preserved 0.1 data with an explicit Room migration
- added meaningful listening history and local smart mixes
- completed playlist/category browsing, removal, accessible reordering, artwork and multi-target selection
- added album, artist and genre detail experiences and collection favorites
- added safe metadata overrides, Android-confirmed deletion and file sharing
- added configurable online lyric-provider architecture, LRC import, manual timestamping, translation and romanization layers
- added sleep timer modes with fade-out and queue/song completion options
- added hardware-gated equalizer, bass boost, virtualizer and loudness enhancement
- added folder exclusions, minimum-duration filtering and MediaStore change observation
- localized the author credit per selected app language

## 0.1.0-alpha — Alpha 0.1

### Added

- original NEO PLAYER identity, adaptive icon, monochrome icon, and splash branding
- MediaStore library scan and Room metadata cache
- song, album, artist, genre, folder, playlist, category, favorites, and search experiences
- Media3 foreground playback service, system media controls, persisted queue, shuffle, repeat, seek, and speed controls
- artwork-first Now Playing screen and mini player
- local plain/LRC lyric editor and synchronized-line display
- English and Persian resources with RTL support
- six accent choices including custom hex, plus light, dark, system, and AMOLED modes
- privacy and About information with Alpha version and author credit
- GitHub Actions test, lint, APK build, and artifact workflow

### Known limitations

- Debug signing is used for this development Alpha.
- Physical-device audio routing, Bluetooth, notification, and very-large-library tests remain required after CI compilation.
- Advanced DSP, online lyric providers, safe metadata writing, sidecar discovery, and drag reordering are scheduled after the foundation is validated.
