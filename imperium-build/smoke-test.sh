#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.imperiumvale.game"
ACTIVITY="$PACKAGE/.MainActivity"
APK="$GITHUB_WORKSPACE/imperium-build/project/app/build/outputs/apk/debug/app-debug.apk"

check_health() {
  local phase="$1"
  local pid
  pid="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
  if [[ -z "$pid" ]]; then
    echo "ERROR: process died during $phase"
    adb logcat -d '*:E'
    exit 1
  fi
  if adb logcat -d | grep -E 'FATAL EXCEPTION|AndroidRuntime:.*Process: com\.imperiumvale\.game|ANR in com\.imperiumvale\.game'; then
    echo "ERROR: fatal exception or ANR during $phase"
    exit 1
  fi
  echo "$phase passed; PID=$pid"
}

adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 8
check_health "startup"

SIZE="$(adb shell wm size | tail -1 | awk '{print $3}' | tr -d '\r')"
WIDTH="${SIZE%x*}"
HEIGHT="${SIZE#*x}"
if (( WIDTH < HEIGHT )); then
  TMP="$WIDTH"
  WIDTH="$HEIGHT"
  HEIGHT="$TMP"
fi

# Dismiss Android's one-time immersive-mode education card if it is present.
adb shell input tap $(( WIDTH * 70 / 100 )) $(( HEIGHT * 46 / 100 ))
sleep 1

# Enter the match from the central JOGAR button.
adb shell input tap $(( WIDTH / 2 )) $(( HEIGHT * 74 / 100 ))
sleep 8
check_health "match startup"

# Exercise lifecycle transitions that often expose SurfaceView race conditions.
for i in 1 2 3; do
  adb shell input keyevent KEYCODE_HOME
  sleep 1
  adb shell am start -n "$ACTIVITY" >/dev/null
  sleep 2
  check_health "resume cycle $i"
done

# Controlled touch stress: selections, movement commands and HUD taps.
for i in $(seq 1 24); do
  x=$(( WIDTH * (15 + (i * 29) % 70) / 100 ))
  y=$(( HEIGHT * (18 + (i * 17) % 62) / 100 ))
  adb shell input tap "$x" "$y"
  sleep 0.08
done
sleep 3
check_health "touch stress"

# Let the simulation and AI run long enough to catch delayed failures.
sleep 12
check_health "extended simulation"

adb exec-out screencap -p > "$GITHUB_WORKSPACE/imperium-build/smoke-screen.png"
adb shell dumpsys meminfo "$PACKAGE" | grep -E 'TOTAL PSS|TOTAL RSS|Java Heap|Native Heap' || true

echo "Full Android smoke test passed"
