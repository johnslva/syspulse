#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.imperiumvale.game"
ACTIVITY="$PACKAGE/.MainActivity"
APK="$GITHUB_WORKSPACE/imperium-build/project/app/build/outputs/apk/debug/app-debug.apk"

adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 8

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "ERROR: process died during startup"
  adb logcat -d '*:E'
  exit 1
fi

if adb logcat -d | grep -E 'FATAL EXCEPTION|AndroidRuntime:.*Process: com\.imperiumvale\.game'; then
  echo "ERROR: fatal exception detected after startup"
  exit 1
fi

echo "Startup passed; PID=$PID"

# Main menu is forced to landscape. Tap the central JOGAR button.
SIZE="$(adb shell wm size | tail -1 | awk '{print $3}' | tr -d '\r')"
WIDTH="${SIZE%x*}"
HEIGHT="${SIZE#*x}"
if (( WIDTH < HEIGHT )); then
  TMP="$WIDTH"
  WIDTH="$HEIGHT"
  HEIGHT="$TMP"
fi

X=$(( WIDTH / 2 ))
Y=$(( HEIGHT * 74 / 100 ))
adb shell input tap "$X" "$Y"
sleep 8

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "ERROR: process died after entering a match"
  adb logcat -d '*:E'
  exit 1
fi

if adb logcat -d | grep -E 'FATAL EXCEPTION|AndroidRuntime:.*Process: com\.imperiumvale\.game'; then
  echo "ERROR: fatal exception detected after entering a match"
  exit 1
fi

adb exec-out screencap -p > "$GITHUB_WORKSPACE/imperium-build/smoke-screen.png"
echo "Gameplay smoke test passed; PID=$PID"
