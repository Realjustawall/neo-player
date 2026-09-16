# Alpha 0.2 QA checklist

Use at least Android 8/API 26, Android 12/API 31, and Android 15/API 35. Test one Persian-locale device and one library above 5,000 tracks.

| Area | Test | Expected result |
|---|---|---|
| Install | Fresh debug install | Branded splash, then permission rationale |
| Permission | Deny then grant audio | No crash; permission screen remains until granted |
| Scan | Add, rename, and remove tracks then rescan | Facets and search reflect MediaStore |
| Playback | Play, pause, seek, next, previous | Correct audio and metadata |
| Background | Lock device and background app | Playback continues with notification/lock controls |
| Routing | Bluetooth and wired disconnect | Platform audio focus/noisy handling pauses safely |
| Queue | Add next, append, remove, clear, restart | Queue operations work and queue restores |
| Modes | Shuffle and three repeat states | Highlight and behavior remain in sync |
| Collections | Create/delete playlist and category; add a track | Data persists after restart |
| Collection editing | Rename, reorder, remove songs, choose artwork | The edited collection persists after restart |
| Favorites | Like and unlike tracks | Home and song rows update |
| Smart mixes | Play tracks for at least 30 seconds and skip tracks | Most/Never played and history counters update locally |
| Search | Type English and Persian metadata | Results update without submit |
| Lyrics | Paste/import plain lyrics and timed LRC; add translation and romanization | Layers persist; timed line follows playback |
| Lyrics provider | Run with no endpoint, then a configured legal endpoint, then airplane mode | Online controls are hidden when unconfigured; failures do not affect offline lyrics |
| Offline | Airplane mode | Every Alpha feature remains usable |
| Missing media | Delete current file then attempt playback | Media3 reports failure without app process crash |
| Appearance | Switch all accents and display modes | selection persists and contrast remains readable |
| RTL | Choose Persian | system recreates app with correct RTL flow and translated core UI |
| Audio effects | Start playback, then change preset/bass/virtualizer/loudness | Supported effects attach to the active session; unsupported devices degrade safely |
| Sleep timer | Test preset/custom/end-of-song/end-of-queue/cancel | Playback pauses at the chosen boundary and timed mode fades out |
| Privacy | Clear listening history | Counts, skips, and listening duration reset without deleting music |
| Accessibility | TalkBack and 200% font scale | controls have labels; essential text remains operable |

Automated gates: `testDebugUnitTest`, `lintDebug`, and `assembleDebug`. Hardware behaviors require the device matrix above and are never represented as device-certified until executed on that matrix.
