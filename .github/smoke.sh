#!/usr/bin/env bash
# Runs inside the emulator: install, launch, wait, keep the log. Never aborts early so the log is always saved.
APK=bubby.apk
adb install -r "$APK"
adb logcat -c
timeout 60 adb shell am start -n com.example.buddy/.ui.UiActivity
for i in 1 2 3 4 5 6; do
  sleep 5
  adb shell pidof com.example.buddy > /tmp/pid.txt 2>&1 && echo "t+$((i*5))s running: $(cat /tmp/pid.txt)" || echo "t+$((i*5))s NOT running"
done
timeout 60 adb logcat -d -t 3000 > smoke-logcat.txt 2>&1
timeout 30 adb exec-out screencap -p > smoke-screen.png 2>/dev/null
echo "---- crash lines ----"
grep -n -B2 -A25 "FATAL EXCEPTION\|Fatal signal\|ANR in" smoke-logcat.txt | head -120
if adb shell pidof com.example.buddy > /dev/null 2>&1; then echo "SMOKE OK: app is running"; exit 0; fi
echo "SMOKE FAILED: app is not running"
exit 1
