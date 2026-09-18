from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one occurrence in {path}: {old!r}, found {count}")
    p.write_text(text.replace(old, new, 1))


replace_once("app/build.gradle.kts", "versionCode = 5", "versionCode = 6")
replace_once("app/build.gradle.kts", 'versionName = "0.5.0-alpha"', 'versionName = "0.6.0-alpha"')
replace_once("app/build.gradle.kts", 'buildConfigField("String", "DISPLAY_VERSION", "\\\"Alpha 0.5\\\"")', 'buildConfigField("String", "DISPLAY_VERSION", "\\\"0.6Alpha\\\"")')

p = Path("CHANGELOG.md")
text = p.read_text()
marker = "# Changelog\n\n"
section = """## 0.6.0-alpha — 0.6Alpha

- reorganized the app into Spotify-style contextual UX: Home, Search, Your Library, Create, and Now Playing
- moved track, playlist, library, cache, backup, offline, analysis, and visual tools into the context where users expect them
- preserved all existing NEO+, Track+, and Offline Pro capabilities while removing scattered launcher-style entry points
- made the selected light, dark, system, AMOLED, and accent theme propagate through menus, dialogs, sheets, panels, and system bars
- made Search show the local song library immediately and filter in memory as the user types
- verified unit tests, lint, Debug APK assembly, Release APK assembly, and package metadata before release promotion

"""
if section.strip() not in text:
    if marker not in text:
        raise SystemExit("CHANGELOG header not found")
    text = text.replace(marker, marker + section, 1)
p.write_text(text)

verify_script = r'''#!/usr/bin/env bash
set -Eeuo pipefail

APK="$GITHUB_WORKSPACE/candidate/NEO-PLAYER-0.6Alpha-release.apk"

echo "Waiting for fully booted emulator..."
adb wait-for-device
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  sleep 2
done

adb shell service check package | tee package-service.txt
grep -q 'package: found' package-service.txt

adb install --no-incremental -r "$APK" | tee install-output.txt
grep -q 'Success' install-output.txt

adb shell pm path com.neoplayer.app | tee package-path.txt
grep -q 'package:' package-path.txt

adb shell dumpsys package com.neoplayer.app \
  | grep -E 'versionCode=6|versionName=0.6.0-alpha' \
  | tee package-version.txt
grep -q 'versionCode=6' package-version.txt
grep -q 'versionName=0.6.0-alpha' package-version.txt

adb logcat -c
adb shell am force-stop com.neoplayer.app
adb shell am start -W -n com.neoplayer.app/.MainActivity | tee launch-output.txt
grep -q 'Status: ok' launch-output.txt

sleep 10
adb logcat -d -v threadtime > logcat.txt || true
adb shell dumpsys activity processes > activity-processes.txt || true
adb shell dumpsys activity exit-info com.neoplayer.app > exit-info.txt 2>/dev/null || true

PID="$(adb shell pidof com.neoplayer.app 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  echo 'APP_PROCESS_DIED_AFTER_LAUNCH=true' | tee runtime-result.txt
  grep -E -i 'FATAL EXCEPTION|AndroidRuntime|Process: com\.neoplayer\.app|Caused by:|UnsatisfiedLinkError|NoClassDefFoundError|ClassNotFoundException|SecurityException' logcat.txt \
    | tail -n 250 \
    | tee crash-summary.txt || true
  exit 42
fi

if grep -E -i 'FATAL EXCEPTION.*|Process: com\.neoplayer\.app' logcat.txt > crash-summary.txt; then
  echo 'APP_FATAL_LOG_DETECTED=true' | tee runtime-result.txt
  cat crash-summary.txt
  exit 43
fi

printf 'INSTALL_TEST=PASS\nEMULATOR=Android_14_API_34_default_x86_64_KVM\nRUNNER=ubuntu-latest\nPACKAGE=com.neoplayer.app\nVERSION_CODE=6\nVERSION_NAME=0.6.0-alpha\nPID=%s\n' "$PID" \
  | tee INSTALL_ALPHA06_KVM_VERIFICATION.txt
'''
Path(".github/scripts/verify-alpha06-install.sh").write_text(verify_script)

Path("ALPHA06_MAIN_RELEASE_TRIGGER").write_text(
    "NEO PLAYER 0.6Alpha release trigger\n"
    "tag=v0.6.0-alpha\n"
    "versionCode=6\n"
    "versionName=0.6.0-alpha\n"
    "source=verified Spotify-style UX branch\n"
)
