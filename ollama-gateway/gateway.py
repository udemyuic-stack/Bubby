#!/usr/bin/env python3
"""Token-protected front door for a local Ollama, meant to sit behind ngrok/jprq.

Ollama has no authentication; exposing it directly gives the whole internet your Mac's GPU.
This proxy listens on 127.0.0.1 only, requires `Authorization: Bearer <BUDDY_TOKEN>`, forwards a
tiny allow-list of endpoints, caps body size and concurrency, and never forwards model management
(pull/delete/create).

  BUDDY_TOKEN=... python3 gateway.py            # listens on 127.0.0.1:11435
"""
import hmac
import http.server
import json
import os
import sys
import threading
import urllib.error
import urllib.request

UPSTREAM = os.environ.get("OLLAMA_URL", "http://127.0.0.1:11434")
PORT = int(os.environ.get("GATEWAY_PORT", "11435"))
TOKEN = os.environ.get("BUDDY_TOKEN", "")
MAX_BODY = 256 * 1024
ALLOWED_POST = {"/v1/chat/completions", "/api/chat"}
ALLOWED_GET = {"/api/version"}
slots = threading.BoundedSemaphore(int(os.environ.get("GATEWAY_CONCURRENCY", "2")))


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))

    def reply(self, code, body=b"", ctype="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        got = self.headers.get("Authorization", "")
        return hmac.compare_digest(got.encode(), ("Bearer " + TOKEN).encode())

    def do_GET(self):
        if not self.authorized():
            return self.reply(401, b'{"error":"unauthorized"}')
        if self.path not in ALLOWED_GET:
            return self.reply(404, b'{"error":"not found"}')
        self.forward("GET", None)

    def do_POST(self):
        if not self.authorized():
            return self.reply(401, b'{"error":"unauthorized"}')
        if self.path not in ALLOWED_POST:
            return self.reply(404, b'{"error":"not found"}')
        n = int(self.headers.get("Content-Length", "0") or 0)
        if n <= 0 or n > MAX_BODY:
            return self.reply(413, b'{"error":"bad body size"}')
        body = self.rfile.read(n)
        try:
            data = json.loads(body)
            data["stream"] = False  # the app never streams; keeps one request = one response
            body = json.dumps(data).encode()
        except ValueError:
            return self.reply(400, b'{"error":"bad json"}')
        if not slots.acquire(blocking=False):
            return self.reply(429, b'{"error":"busy, try again"}')
        try:
            self.forward("POST", body)
        finally:
            slots.release()

    def forward(self, method, body):
        req = urllib.request.Request(UPSTREAM + self.path, data=body, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                self.reply(r.status, r.read())
        except urllib.error.HTTPError as e:
            self.reply(e.code, e.read())
        except Exception as e:
            self.reply(502, json.dumps({"error": "upstream: %s" % e}).encode())


if __name__ == "__main__":
    if len(TOKEN) < 16:
        sys.exit("Set BUDDY_TOKEN to a random string of 16+ characters (python3 -c 'import secrets;print(secrets.token_urlsafe(24))')")
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print("gateway on 127.0.0.1:%d -> %s" % (PORT, UPSTREAM))
    srv.serve_forever()
