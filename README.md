<div align="center">

![NEO PLAYER](docs/assets/banner-en.svg)

**An Android music player built around your local library, expressive playback, offline intelligence and Persian/English interfaces.**

<img alt="Version" src="https://img.shields.io/badge/version-v0.8.0--alpha-ff8c30?style=flat-square" />
<img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-354158?style=flat-square" />
<a href="https://github.com/Realjustawall/neo-player/actions/workflows/android-alpha.yml"><img alt="CI status" src="https://img.shields.io/github/actions/workflow/status/Realjustawall/neo-player/android-alpha.yml?branch=main&amp;label=CI&amp;style=flat-square" /></a>

<a href="https://github.com/Realjustawall/neo-player/releases/latest"><img src="docs/assets/buttons/download-en.svg" alt="Download" width="220" height="72" /></a>
<a href="docs/gallery.en.md"><img src="docs/assets/buttons/demo-en.svg" alt="Explore demo" width="220" height="72" /></a>
<a href="#quick-start"><img src="docs/assets/buttons/start-en.svg" alt="Quick start" width="220" height="72" /></a>
<a href="https://github.com/Realjustawall/neo-player/issues"><img src="docs/assets/buttons/issues-en.svg" alt="Feedback" width="220" height="72" /></a>

[English](README.md) · [فارسی](README.fa.md)

</div>

---

[Highlights](#features) · [Preview](#preview) · [Quick start](#quick-start) · [Development](#development) · [Documentation](#documentation) · [Security and privacy](#security) · [License and feedback](#license)

<a id="features"></a>

## Highlights

| Capability | What you get |
|---|---|
| Local library | Songs, albums, artists, folders, favorites and persistent playlists. |
| Playback | Media3 playback, gapless preference, dual-decoder crossfade, AutoMix and audio effects. |
| Track+ | Per-track artwork, wallpaper, Canvas video, visualizers and appearance profiles. |
| Lyrics and local AI | LRC/plain lyrics, word timing and optional Vosk-based English/Persian transcription. |
| Offline tools | Strict Offline, local recommendations, backup, playlist folders and M3U8 import/export. |
| Local Radio | Host/listener workflows with QR joining and refreshed synchronization timing. |

<a id="preview"></a>

## Preview

Captured from the unmodified signed Android v0.8.0-alpha APK on an Android API 34 emulator with KVM in GitHub Actions. Local audio and language preferences are disposable demo fixtures.

<p align="center">
<img src="docs/assets/screenshots/en/01-home.webp" alt="NEO Player Android — Home" width="250" />
<img src="docs/assets/screenshots/en/02-library.webp" alt="NEO Player Android — Local music library" width="250" />
<img src="docs/assets/screenshots/en/03-player.webp" alt="NEO Player Android — Now playing" width="250" />
</p>

<details>
<summary>Settings preview</summary>

<img src="docs/assets/screenshots/en/04-settings.webp" alt="Settings and appearance" width="300" />

</details>

[Open the full Android gallery](docs/gallery.en.md)

<a id="quick-start"></a>

## Quick start

1. Download the **release APK** from the button above.
2. Install it on Android 8.0/API 26 or newer and grant audio-library access when prompted.
3. Scan your music, choose Persian or English and start listening.
4. Import local lyrics/model packs or prepare optional models before enabling Strict Offline.

No account is required. Normal releases obtain optional AI models on demand; an optional full-offline build can embed them.

<a id="development"></a>

## Development

JDK 17, Android SDK 36 and Gradle 8.13.

```bash
git clone https://github.com/Realjustawall/neo-player.git
cd neo-player
gradle assembleDebug
```

Validation commands:

```bash
gradle testDebugUnitTest
gradle lintRelease
gradle assembleRelease bundleRelease
```

Optional embedded-model build:

```bash
gradle -PNEO_EMBED_VOSK_MODELS=true assembleRelease bundleRelease
```

<a id="documentation"></a>

## Documentation

- [Complete feature/build reference](REFERENCE.md)
- [Build workflows](.github/workflows)
- [Android application source](app/src/main)

<a id="security"></a>

## Security and privacy

Local analysis, library state and installed AI models run on-device. Optional provider/model preparation can use the network when Strict Offline is disabled. Grant audio permissions for indexing and keep exported backups somewhere you control.

<a id="license"></a>

## License and feedback

Licensed under [Apache-2.0](LICENSE).

[Report bugs or discuss improvements](https://github.com/Realjustawall/neo-player/issues). Include your version, OS and reproducible steps; keep credentials and private data out of reports.

---

Maintained by **[Realjustawall](https://github.com/Realjustawall)**.
