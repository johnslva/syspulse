#!/usr/bin/env bash
set -euo pipefail

PKG="com.imperiumvale.game3d"
ACTIVITY="$PKG/.AndroidLauncher"
APK="$GITHUB_WORKSPACE/imperium-3d/android/build/outputs/apk/debug/android-debug.apk"
OUT="$GITHUB_WORKSPACE/imperium-3d"

fail_if_runtime_error() {
  if adb logcat -d | grep -E 'FATAL EXCEPTION|ANR in com\.imperiumvale\.game3d'; then
    echo "ERROR: fatal Android runtime error detected"
    exit 1
  fi
}

assert_alive() {
  local phase="$1"
  local pid
  pid="$(adb shell pidof "$PKG" | tr -d '\r')"
  if [[ -z "$pid" ]]; then
    echo "ERROR: game process died during $phase"
    adb logcat -d '*:E' || true
    exit 1
  fi
  fail_if_runtime_error
  echo "$phase passed; PID=$pid"
}

adb install -r "$APK"
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY"
sleep 8
assert_alive "cold startup"
adb exec-out screencap -p > "$OUT/smoke-menu.png"

SIZE="$(adb shell wm size | tail -1 | awk '{print $3}' | tr -d '\r')"
W="${SIZE%x*}"
H="${SIZE#*x}"
if (( W < H )); then
  T="$W"
  W="$H"
  H="$T"
fi

# Menu layout can shift slightly across Android/emulator insets. These centered
# taps cover the full JOGAR button band; once the match opens, later taps land
# harmlessly on open terrain.
for YP in 65 62 59; do
  adb shell input tap $((W / 2)) $((H * YP / 100))
  sleep 0.45
done
sleep 2
assert_alive "HD match startup"
adb exec-out screencap -p > "$OUT/smoke-game.png"

# Give the adaptive renderer time to react before stress testing.
sleep 8
assert_alive "adaptive rendering"

for i in 1 2 3; do
  adb shell input keyevent 3
  sleep 1
  adb shell am start -n "$ACTIVITY"
  sleep 2
  assert_alive "resume cycle $i"
done

for i in $(seq 1 24); do
  X=$((W * 12 / 100 + (i % 8) * W * 10 / 100))
  Y=$((H * 18 / 100 + (i % 5) * H * 12 / 100))
  adb shell input tap "$X" "$Y"
done
# Camera gesture stress as well as taps.
adb shell input swipe $((W * 55 / 100)) $((H * 48 / 100)) $((W * 40 / 100)) $((H * 40 / 100)) 500
adb shell input swipe $((W * 45 / 100)) $((H * 48 / 100)) $((W * 60 / 100)) $((H * 55 / 100)) 500
sleep 8
assert_alive "touch stress"

sleep 15
assert_alive "extended simulation"
adb exec-out screencap -p > "$OUT/smoke-game-stress.png"

adb shell dumpsys meminfo "$PKG" \
  | grep -E 'TOTAL PSS|TOTAL RSS|Native Heap|Java Heap' || true

echo "Full 3D Android smoke test passed"
