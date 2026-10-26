#!/usr/bin/env bash
# Runs inside the emulator: first the launch check, then screenshots of every screen.
bash .github/smoke.sh
result=$?
bash .github/ui-shots.sh || true
pkill -f "adb.*logcat" || true
if [ -f shots/live-logcat.txt ]; then
  grep -v "GoogleInputMethod\|nativeloader" shots/live-logcat.txt | tail -n 1500 > shots/live-tail.txt
  rm -f shots/live-logcat.txt
fi
exit $result
