#!/usr/bin/env bash
set -Eeuo pipefail

APK="$GITHUB_WORKSPACE/candidate/NEO-PLAYER-Alpha-0.5-release.apk"

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
  | grep -E 'versionCode=5|versionName=0.5.0-alpha' \
  | tee package-version.txt
grep -q 'versionCode=5' package-version.txt
grep -q 'versionName=0.5.0-alpha' package-version.txt

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

printf 'INSTALL_TEST=PASS\nEMULATOR=Android_14_API_34_default_x86_64_KVM\nRUNNER=ubuntu-latest\nPACKAGE=com.neoplayer.app\nVERSION_CODE=5\nVERSION_NAME=0.5.0-alpha\nPID=%s\n' "$PID" \
  | tee INSTALL_ALPHA05_KVM_VERIFICATION.txt
