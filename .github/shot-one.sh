#!/usr/bin/env bash
# Runs inside the emulator: opens one screen (or tab) of the app, saves a screenshot and the log around it in ./shots.
SCREEN=${1:-home}
OPEN=$SCREEN; EXTRA=""
if [ "$SCREEN" = agents-demo ]; then OPEN=agents; EXTRA="--ez demo true"; fi
if [ "$SCREEN" = agents-diff ]; then OPEN=agents; EXTRA="--ez demo true --ez demodiff true"; fi
case "$SCREEN" in
  *-green) OPEN=${SCREEN%-green}; EXTRA="--es theme green";;
  *-blue) OPEN=${SCREEN%-blue}; EXTRA="--es theme blue";;
  *-night) OPEN=${SCREEN%-night}; EXTRA="--es theme night";;
esac
ADB=$(command -v adb); adb() { timeout 30 "$ADB" "$@"; }
mkdir -p shots
# let the emulator finish its own start-up work (media scan, Google services) before touching it
sleep 45
adb shell wm density 250 >/dev/null 2>&1   # the emulator is 640 px wide: 250 dpi makes it about 411 dp wide, like a real phone
adb install -r bubby.apk
adb logcat -c
( "$ADB" logcat -v threadtime > "shots/$SCREEN-live.txt" 2>&1 & )
if [ "$SCREEN" = home ]; then
  timeout 60 "$ADB" shell am start -W -n com.example.buddy/.ui.UiActivity --ez static true > "shots/$SCREEN-start.txt" 2>&1
else
  timeout 60 "$ADB" shell am start -W -n com.example.buddy/.ui.UiActivity --es open "$OPEN" --ez static true $EXTRA > "shots/$SCREEN-start.txt" 2>&1
fi
sleep 8
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb exec-out cat /sdcard/ui.xml > "shots/$SCREEN-ui.xml" 2>/dev/null
adb exec-out screencap -p > /tmp/raw.png
if command -v convert >/dev/null 2>&1; then convert /tmp/raw.png -resize 400x "shots/$SCREEN.png"; else cp /tmp/raw.png "shots/$SCREEN.png"; fi
if [ "$SCREEN" = settings ] || [ "$SCREEN" = agents-demo ]; then
  SZ=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SZ%x*}; H=${SZ#*x}
  for k in 1 2; do adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/5)) 300; done
  sleep 2
  adb exec-out screencap -p > /tmp/raw2.png
  if command -v convert >/dev/null 2>&1; then convert /tmp/raw2.png -resize 400x "shots/$SCREEN-bottom.png"; else cp /tmp/raw2.png "shots/$SCREEN-bottom.png"; fi
  for k in 1 2 3; do adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/5)) 300; done
  sleep 2
  adb exec-out screencap -p > /tmp/raw3.png
  if command -v convert >/dev/null 2>&1; then convert /tmp/raw3.png -resize 400x "shots/$SCREEN-end.png"; else cp /tmp/raw3.png "shots/$SCREEN-end.png"; fi
fi
adb shell pidof com.example.buddy >> "shots/$SCREEN-start.txt" 2>&1 || echo "app not running" >> "shots/$SCREEN-start.txt"
pkill -f "adb.*logcat" || true
grep -E "com.example.buddy|AndroidRuntime|FATAL|ANR" "shots/$SCREEN-live.txt" | grep -v "nativeloader\|GoogleInput\|CarrierSvc\|MediaPlayerList\|ImsResolver" | tail -n 150 > "shots/$SCREEN-log.txt"
tail -n 150 "shots/$SCREEN-live.txt" | cut -c1-240 > "shots/$SCREEN-tail.txt"
rm -f "shots/$SCREEN-live.txt"
ls -la shots
# a dead emulator leaves an empty picture: fail so that "re-run failed jobs" tries this screen again
[ -s "shots/$SCREEN.png" ] || exit 1
