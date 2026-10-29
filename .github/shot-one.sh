#!/usr/bin/env bash
# Runs inside the emulator: opens one screen (or tab) of the app, saves a screenshot and the log around it in ./shots.
SCREEN=${1:-home}
OPEN=$SCREEN; EXTRA=""
if [ "$SCREEN" = agents-demo ]; then OPEN=agents; EXTRA="--ez demo true"; fi
if [ "$SCREEN" = agents-diff ]; then OPEN=agents; EXTRA="--ez demo true --ez demodiff true"; fi
LIVE=""
case "$SCREEN" in *-live) LIVE=1; SCREEN_BASE=${SCREEN%-live};; *) SCREEN_BASE=$SCREEN;; esac
case "${SCREEN_BASE}" in
  *-green) OPEN=${SCREEN_BASE%-green}; EXTRA="--es theme green";;
  *-blue) OPEN=${SCREEN_BASE%-blue}; EXTRA="--es theme blue";;
  *-night) OPEN=${SCREEN_BASE%-night}; EXTRA="--es theme night";;
  *-space) OPEN=${SCREEN_BASE%-space}; EXTRA="--es theme space";;
  *-halloween) OPEN=${SCREEN_BASE%-halloween}; EXTRA="--es theme halloween";;
  *-forest) OPEN=${SCREEN_BASE%-forest}; EXTRA="--es theme forest";;
  *-ocean) OPEN=${SCREEN_BASE%-ocean}; EXTRA="--es theme ocean";;
  *-newyear) OPEN=${SCREEN_BASE%-newyear}; EXTRA="--es theme newyear";;
  *-ice) OPEN=${SCREEN_BASE%-ice}; EXTRA="--es theme ice";;
esac
[ -n "$LIVE" ] && [ "$OPEN" = "$SCREEN" ] && OPEN=$SCREEN_BASE
case "${SCREEN_BASE}" in game-room*) EXTRA="--ez autoplay true";; esac
ADB=$(command -v adb); adb() { timeout 30 "$ADB" "$@"; }
mkdir -p shots
# let the emulator finish its own start-up work (media scan, Google services) before touching it
sleep 45
PXW=$(adb shell wm size | grep -o "[0-9]*x[0-9]*" | tail -1 | cut -dx -f1); adb shell wm density $((PXW * 160 / 411)) >/dev/null 2>&1   # about 411 dp wide, like a real phone, whatever the emulator resolution
adb install -r bubby.apk
adb logcat -c
( "$ADB" logcat -v threadtime > "shots/$SCREEN-live.txt" 2>&1 & )
STATIC="--ez static true"; [ -n "$LIVE" ] && STATIC="--ez nosetup true"
if [ "$SCREEN" = home ]; then
  timeout 60 "$ADB" shell am start -W -n com.example.buddy/.ui.UiActivity $STATIC > "shots/$SCREEN-start.txt" 2>&1
else
  timeout 60 "$ADB" shell am start -W -n com.example.buddy/.ui.UiActivity --es open "$OPEN" $STATIC $EXTRA > "shots/$SCREEN-start.txt" 2>&1
fi
sleep 8
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb exec-out cat /sdcard/ui.xml > "shots/$SCREEN-ui.xml" 2>/dev/null
adb exec-out screencap -p > /tmp/raw.png
if command -v convert >/dev/null 2>&1; then convert /tmp/raw.png -resize 400x "shots/$SCREEN.png"; else cp /tmp/raw.png "shots/$SCREEN.png"; fi
if [ -n "$LIVE" ]; then   # a second picture two seconds later: the page must be moving, not frozen
  sleep 2; adb exec-out screencap -p > /tmp/raw_b.png
  if command -v convert >/dev/null 2>&1; then convert /tmp/raw_b.png -resize 400x "shots/$SCREEN-b.png"; else cp /tmp/raw_b.png "shots/$SCREEN-b.png"; fi
  sleep 5; adb exec-out screencap -p > /tmp/raw_c.png   # and 7 s after the start: the splash must be gone by now
  if command -v convert >/dev/null 2>&1; then convert /tmp/raw_c.png -resize 400x "shots/$SCREEN-c.png"; else cp /tmp/raw_c.png "shots/$SCREEN-c.png"; fi
fi
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
