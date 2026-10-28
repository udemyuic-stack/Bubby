# Agents v2: easier pairing and a usage dashboard

The v1 link (TLS with a pinned certificate, line-delimited JSON, `hello`/`welcome`, sessions, approvals) stays as it is and
keeps working. v2 only adds things, so an old phone keeps working with a new bridge and the other way round.

## Why
Connecting meant: start the bridge, copy a long `coucou://pair?...` link out of the terminal, get it to the phone, paste it.
Now: run one command on the computer, open Agents on the phone, tap the computer that shows up, type the 6 digits.

## 1. Discovery (mDNS)
The bridge advertises `_bubby-agent._tcp.local.` on its port (default 47821). TXT records: `v=1`, `name=<computer name>`,
`fp=<sha256 of the certificate, lowercase hex>`, `paired=0|1`. The phone uses Android's `NsdManager`. No npm packages: the
bridge answers mDNS queries itself with `dgram` on 224.0.0.251:5353 (also announce on start and on every query for the
service name). The old pairing link stays available as a fallback with `--link` (see 4; it contains the token, so it is
not printed by default), and for networks that block multicast, the phone also accepts `host:port` typed by hand.
The instance name is the computer name (dots replaced by `-`, at most 63 bytes); the SRV target is `bubby-<first 8 hex of
fp>.local.` with its own A record, so it never clashes with the system's own mDNS host name.

## 2. Pairing by code
The bridge shows a 6-digit code in the terminal (random, valid 5 minutes, one use). New message from the phone, sent first
on a fresh TLS connection instead of `hello`:

    {"type":"pair","v":1,"nonce":"<16 random bytes, hex>","proof":"<hex>","name":"<phone name>"}

`proof = HMAC-SHA256(key = UTF-8 code, data = "bubby-pair-v1|" + fpSeen + "|" + nonce)` in lowercase hex, where `fpSeen` is
the SHA-256 (lowercase hex) of the certificate the phone actually received in this TLS handshake (it trusts it only for this
exchange, trust on first use). The bridge computes the same with the fingerprint of its own certificate, so a man in the
middle presenting another certificate makes the proof fail.

- success: bridge replies `{"type":"paired","token":"<the 32 char token>","name":"<computer name>"}` and keeps the
  connection as a normal authenticated one (the phone then proceeds exactly as after `welcome`). The code is consumed.
- failure: `{"type":"error","error":"bad_code"}`, then the connection closes. A failed attempt burns the code: the bridge
  prints a new one. At most 5 failed attempts per minute across all connections, then `{"type":"error","error":"slow_down"}`
  and the connection closes (such a refused attempt is not checked and does not burn the code). A malformed `pair`
  (nonce not 32 lowercase hex chars, proof not 64 lowercase hex chars, `v` not 1) counts as a failed attempt.
- the bridge is already paired (TXT `paired=1`) or has no code to offer: `{"type":"error","error":"not_pairing"}`, then
  the connection closes. Another phone connected right now: the v1 `{"type":"error","code":"busy",...}`.
- the code is compared with `timingSafeEqual`; it never appears in logs.
Pairing a second phone is allowed only by running the bridge's `--new-pairing`, as in v1. "Paired" means a phone has
received the current token, by `paired` or by a successful v1 `hello`; after that no code is offered (and none is printed)
until `--new-pairing`.

## 3. Usage and statistics
After `welcome` (and whenever something changes, at most every 5 s, plus a refresh every 60 s) the bridge sends

    {"type":"usage","fiveHour":{"used":0.68,"resetsAt":1760000000000},"sevenDay":{"used":0.42,"resetsAt":1760400000000},
     "doneToday":4,"tokensToday":123456,"plan":"max","updatedAt":1759990000000}

Every field is optional; the phone shows only what is there. `used` is 0..1, times are epoch milliseconds.
- `fiveHour` / `sevenDay`: best effort. Read Claude Code's OAuth token from `~/.claude/.credentials.json` (on macOS from the
  keychain item "Claude Code-credentials" via the `security` command, on Linux/Windows the same JSON file) and call the
  usage endpoint that Claude Code itself uses (`GET https://api.anthropic.com/api/oauth/usage` with
  `Authorization: Bearer <token>` and `anthropic-beta: oauth-2025-04-20`; response has `five_hour` and `seven_day` with
  `utilization` in percent and `resets_at`). If the call or the file does not work, leave the fields out. The token never
  leaves the computer, is never logged and is never sent to the phone. Poll at most once a minute, back off on errors.
  This endpoint is not documented by Anthropic, so treat every field as possibly missing.
- `doneToday`: number of Claude Code turns finished today (local day) as seen by the hooks (`Stop`), kept in
  `~/.bubby/stats.json` so it survives restarts.
- `tokensToday`: sum of tokens if easily available from the transcript path given in hook events; otherwise omit.

The existing `sessions` message already carries the running agents (`agent`, `state`, `statusText`, step counts); the phone
builds its dashboard from `sessions` + `usage`.

## 4. One command on the computer
`node bridge.mjs` should do everything: on first run create the certificate, pick the LAN address, install the Claude Code
hooks (same logic as `install-hooks.mjs --yes`, with the `.bak` copy, after printing what it will do and asking `y/N` on a
TTY; flag `--yes` skips the question, `--no-hooks` skips it), advertise over mDNS, print the code and the big headline
"Open Bubby on your phone → Agents → tap this computer → type CODE". `--link` additionally prints the old link.
Keep it dependency-free (Node >= 18) and keep the security properties in `agent-bridge/README.md`. Update that README in
Russian like the rest of it and add a short English quick start at the top.
