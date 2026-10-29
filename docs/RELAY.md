# Relay: reaching your computer from outside the home Wi-Fi

At home the phone talks to the computer's Bridge directly over the local network. Away from home (mobile data, another
Wi-Fi) it cannot. The **relay** is a tiny service you host yourself on your own Cloudflare account (free tier) that passes
messages between the two. It is a dumb pipe: it cannot read, change or invent anything that goes through it.

## How it is built

```
phone  ──wss──►  your Cloudflare Worker (a Durable Object "room")  ◄──wss──  computer (bridge.mjs)
        end-to-end encrypted frames; the relay only forwards them
```

- **Room.** The computer makes a random 32-byte relay key `rk` once. Both sides derive from it, with HMAC-SHA256, the room
  id (the relay URL path `/r/<room>`) and the AES-256 key. The relay only ever sees the room id.
- **Frames.** Every protocol line is sealed with AES-256-GCM: `1.` + base64url(iv ‖ ciphertext ‖ tag). The direction
  (`b2p` bridge→phone, `p2b` phone→bridge) is the authenticated data, so a frame cannot be replayed back at its sender.
- **Replay.** Each plaintext carries a timestamp (accepted within ±5 minutes), a boot id and a counter that must grow
  inside one boot.
- **Key delivery.** The phone learns the relay address and key only over the normal home-network link, which is
  certificate-pinned, and only if it offers the `relay` capability. They are stored encrypted (Android Keystore).
  Pairing by code is **not** possible over the relay.
- **One phone.** A room holds one computer and one phone; a second phone is told "busy".
- `bridge.mjs --new-pairing` rotates the relay key together with the pairing token.

## Set it up (about five minutes)

1. Deploy the relay (needs Node 18+ and a free Cloudflare account):
   ```
   cd relay
   npx wrangler login
   npx wrangler deploy
   ```
   It prints an address like `https://bubby-relay.<you>.workers.dev`.
2. On the computer: `node agent-bridge/bridge.mjs --relay https://bubby-relay.<you>.workers.dev`
3. Connect the phone once on the home network (as usual). It receives the relay address and key by itself.
4. Away from home the phone tries the home network first (2.5 s), then the relay. The Agents screen then shows
   "via relay" next to the computer's name.

To stop using it: `node agent-bridge/bridge.mjs --forget-relay`.

## Trying it without Cloudflare

`node relay/dev-relay.mjs 8787` runs the same relay locally without TLS (content is still encrypted, the address is
visible), for example `--relay ws://192.168.1.10:8787`.

## Limits and honesty

- The relay can see that a room exists, when frames flow and how big they are. It cannot see their content.
- Anyone who gets your `relay.json` (computer) or the phone's stored key can join your room: keep them private.
- Cloudflare's free tier is plenty for one phone and one computer.
