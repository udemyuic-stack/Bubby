#!/usr/bin/env bash
# Runs inside the emulator: opens one screen (or tab) of the app, saves a screenshot and the log around it in ./shots.
SCREEN=${1:-home}
ADB=$(command -v adb); adb() { timeout 30 "$ADB" "$@"; }
mkdir -p shots
# let the emulator finish its own start-up work (media scan, Google services) before touching it
sleep 45
adb install -r bubby.apk
adb logcat -c
( "$ADB" logcat -v threadtime > "shots/$SCREEN-live.txt" 2>&1 & )
if [ "$SCREEN" = home ]; then
  timeout 60 "$ADB" shell am start -W -n com.example.buddy/.ui.UiActivity > "shots/$SCREEN-start.txt" 2>&1
else
  timeout 60 "$ADB" shell am start -W -n com.example.buddy/.ui.UiActivity --es open "$SCREEN" > "shots/$SCREEN-start.txt" 2>&1
fi
sleep 8
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb exec-out cat /sdcard/ui.xml > "shots/$SCREEN-ui.xml" 2>/dev/null
adb exec-out screencap -p > /tmp/raw.png
if command -v convert >/dev/null 2>&1; then convert /tmp/raw.png -resize 400x "shots/$SCREEN.png"; else cp /tmp/raw.png "shots/$SCREEN.png"; fi
adb shell pidof com.example.buddy >> "shots/$SCREEN-start.txt" 2>&1 || echo "app not running" >> "shots/$SCREEN-start.txt"
pkill -f "adb.*logcat" || true
grep -E "com.example.buddy|AndroidRuntime|FATAL|ANR" "shots/$SCREEN-live.txt" | grep -v "nativeloader\|GoogleInput\|CarrierSvc\|MediaPlayerList\|ImsResolver" | tail -n 150 > "shots/$SCREEN-log.txt"
tail -n 150 "shots/$SCREEN-live.txt" | cut -c1-240 > "shots/$SCREEN-tail.txt"
rm -f "shots/$SCREEN-live.txt"
ls -la shots
