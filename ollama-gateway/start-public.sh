#!/bin/bash
# Starts: token-protected gateway -> Cloudflare quick tunnel. Ctrl+C stops both.
# Needs Ollama running on 127.0.0.1:11434 (the Ollama app or `ollama serve`).
set -e
cd "$(dirname "$0")"
CF=${CLOUDFLARED:-/Users/jamoliddin/dev-tools/cf/cloudflared}
TOKEN_FILE=.buddy-token
[ -f "$TOKEN_FILE" ] || { python3 -c 'import secrets;print(secrets.token_urlsafe(24))' > "$TOKEN_FILE"; chmod 600 "$TOKEN_FILE"; }
export BUDDY_TOKEN=$(cat "$TOKEN_FILE")
curl -s -m 3 http://127.0.0.1:11434/api/version >/dev/null || { echo "Ollama is not running on 127.0.0.1:11434"; exit 1; }
python3 gateway.py & GW=$!
trap 'kill $GW 2>/dev/null' EXIT
sleep 1
echo "Token is in $(pwd)/$TOKEN_FILE"
echo "In the app, set the key to:  https://<url below without trailing slash>|gemma4:e2b|<token>"
"$CF" tunnel --no-autoupdate --url http://127.0.0.1:11435
