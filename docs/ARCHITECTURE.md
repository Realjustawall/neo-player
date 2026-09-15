# Architecture decisions

## Local-first boundary

MediaStore remains the source of truth for audio files. Room is a disposable query cache for file metadata and the durable source for NEO-owned information: favorites, collections, listening history, and lyrics. A rescan replaces only cached song rows in one transaction.

## Playback ownership

`NeoPlaybackService` is the only ExoPlayer owner. UI code sends commands through a Media3 `MediaController`, so changing screens or destroying the activity cannot interrupt playback. Media3 publishes the same session to notifications, the lock screen, headsets, Bluetooth devices, and compatible car surfaces.

## State flow

Room and DataStore expose cold flows. `MainViewModel` turns them into lifecycle-aware state, while `PlaybackConnection` adapts Media3 callbacks into a small immutable playback snapshot. No filesystem or database query runs on the main thread.

## Scale targets

- keyed lazy lists avoid eagerly composing the library
- SQL grouping produces album, artist, genre, and folder facets
- search is bounded to 200 visible results per query
- artwork is decoded and cached by Coil rather than stored in Room
- playback queue media items contain only routing and display metadata

Paging 3, baseline profiles, and benchmark modules are the next optimization layer after physical testing with 10,000–30,000 tracks.

## Privacy and network policy

Alpha 0.1 deliberately omits the Internet permission. A future online-lyrics module must be optional, provider-based, legally configured, explicitly enabled, cached locally, and shipped without committed credentials.
