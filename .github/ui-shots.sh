#!/usr/bin/env bash
# Opens the native UI, visits every tab and some screens, and saves downscaled screenshots in ./shots.
ADB=$(command -v adb); adb() { timeout 25 "$ADB" "$@"; }
mkdir -p shots
adb shell wm size 720x1440
adb shell wm density 320
W=720; H=1440
pkg=com.example.buddy
shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > /tmp/raw.png
  if command -v convert >/dev/null 2>&1; then convert /tmp/raw.png -resize 400x "shots/$1.png"; else cp /tmp/raw.png "shots/$1.png"; fi
}
adb shell am force-stop $pkg
adb logcat -c
adb shell am start -W -n $pkg/.ui.UiActivity > shots/ui-start.txt 2>&1
adb shell dumpsys activity activities 2>&1 | grep -i "mResumedActivity\|topResumedActivity" >> shots/ui-start.txt
adb shell pidof $pkg >> shots/ui-start.txt 2>&1
sleep 4
adb logcat -d | grep -E "com.example.buddy|FATAL|AndroidRuntime|UiActivity" | head -120 > shots/ui-start-log.txt
adb shell dumpsys activity activities 2>&1 | grep -iE "buddy|ResumedActivity" | head -20 >> shots/ui-start-log.txt
shot home 1
for i in 1 2 3 4; do
  x=$(( W * (2 * i + 1) / 10 ))
  adb shell input tap $x $(( H - 170 ))
  shot "tab$i" 2
done
for s in ${UI_SCREENS:-ai setup outfits music agents fun permissions}; do
  adb shell am start -n $pkg/.ui.UiActivity --es open "$s"
  shot "$s" 3
done
adb logcat -d -t 400 *:E > shots/errors.txt 2>&1 || true
adb logcat -d -t 2000 | grep -i "buddy\|AndroidRuntime\|ActivityManager" > shots/ui-log.txt 2>&1 || true
echo "screenshots: $(ls shots | wc -l)"
