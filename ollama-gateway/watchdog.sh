#!/bin/bash
# Keeps the quick tunnel alive: if the public URL stops answering 3 times in a row, kill cloudflared so
# tunnel-loop.sh starts a fresh tunnel. A fresh quick tunnel gets a NEW address; it is written to CURRENT_URL.txt.
cd "$(dirname "$0")"
LOG=${1:-/private/tmp/tunnel3.log}
fails=0; last=""
while true; do
  url=$(grep -oE "https://[a-z0-9]+(-[a-z0-9]+)+\.trycloudflare\.com" "$LOG" | tail -1)
  if [ -n "$url" ] && [ "$url" != "$last" ]; then echo "$url" > CURRENT_URL.txt; echo "$(date +%T) tunnel url: $url"; last=$url; fails=0; fi
  if [ -n "$url" ]; then
    code=$(curl -s -m 15 -o /dev/null -w "%{http_code}" "$url/api/version")
    if [ "$code" = "401" ] || [ "$code" = "200" ]; then fails=0; else fails=$((fails+1)); echo "$(date +%T) check failed ($code) $fails/3"; fi
    if [ $fails -ge 3 ]; then echo "$(date +%T) restarting tunnel"; pkill -f "cloudflared tunnel"; fails=0; sleep 40; fi
  fi
  sleep 60
done
