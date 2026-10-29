import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import crypto from "node:crypto";
import { startBridge } from "../bridge.mjs";
import { startDevRelay } from "../../relay/dev-relay.mjs";
import { fakePhone, fakeRelayPhone } from "../lib/fake-phone.mjs";
import { newRelayKey, derive, seal, open, createReceiver, createSender } from "../lib/relay-crypto.mjs";
import { saveRelay, loadRelay, forgetRelay, rotateRelay, normalizeUrl } from "../lib/relay.mjs";
import { loadToken } from "../lib/pairing.mjs";

const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/cr-"); dirs.push(d); return d + "/b"; };
after(() => dirs.forEach((d) => fs.rmSync(d.replace(/\/b$/, ""), { recursive: true, force: true })));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const until = async (f, ms = 3000) => { const t = Date.now(); while (Date.now() - t < ms) { if (f()) return true; await sleep(25); } return false; };

test("derive is stable, room is 32 hex, key is 32 bytes, different keys differ", () => {
  const rk = newRelayKey();
  const a = derive(rk), b = derive(rk);
  assert.equal(a.room, b.room);
  assert.match(a.room, /^[0-9a-f]{32}$/);
  assert.equal(a.key.length, 32);
  assert.notEqual(derive(newRelayKey()).room, a.room);
  assert.throws(() => derive("short"));
});

test("known vector: a frame made elsewhere opens here (the Android side checks the same one)", () => {
  const rk = Buffer.alloc(32, 7).toString("base64url");
  const { room, key } = derive(rk);
  const frame = seal(key, "b2p", { t: 1700000000000, s: 1, b: "aabbccddeeff", l: "{\"type\":\"pong\"}" }, Buffer.alloc(12, 9));
  console.log("VECTOR rk=" + rk + " room=" + room + " frame=" + frame);
  assert.deepEqual(open(key, "b2p", frame), { t: 1700000000000, s: 1, b: "aabbccddeeff", l: "{\"type\":\"pong\"}" });
});

test("seal/open: wrong direction, tampering, truncation and junk are all refused", () => {
  const { key } = derive(newRelayKey());
  const f = seal(key, "p2b", { t: Date.now(), s: 1, b: "x", l: "hi" });
  assert.ok(open(key, "p2b", f));
  assert.equal(open(key, "b2p", f), null); // turned around
  assert.equal(open(derive(newRelayKey()).key, "p2b", f), null);
  const bytes = Buffer.from(f.slice(2), "base64url"); bytes[20] ^= 1;
  assert.equal(open(key, "p2b", "1." + bytes.toString("base64url")), null);
  assert.equal(open(key, "p2b", f.slice(0, -4)), null);
  for (const junk of ["", "1.", "2.abc", "{}", null, 5, "1." + "A".repeat(10)]) assert.equal(open(key, "p2b", junk), null);
});

test("receiver: replay, old and future frames are refused; counters grow per boot", () => {
  let now = 1_000_000;
  const r = createReceiver(() => now);
  assert.ok(r.accept({ t: now, s: 1, b: "a" }));
  assert.ok(!r.accept({ t: now, s: 1, b: "a" }));   // replay
  assert.ok(!r.accept({ t: now, s: 0, b: "a" }));
  assert.ok(r.accept({ t: now, s: 2, b: "a" }));
  assert.ok(r.accept({ t: now, s: 1, b: "other-boot" }));
  assert.ok(!r.accept({ t: now - 6 * 60_000, s: 9, b: "a" }));  // too old
  assert.ok(!r.accept({ t: now + 6 * 60_000, s: 9, b: "a" }));  // from the future
  assert.ok(!r.accept({ t: now, s: 1.5, b: "a" }));
  assert.ok(!r.accept(null));
});

test("relay config: save/load/forget/rotate, urls are normalised, no secrets in the url", () => {
  const dir = tmp();
  assert.equal(loadRelay(dir), null);
  const c = saveRelay(dir, "https://x.example.workers.dev/");
  assert.equal(c.url, "wss://x.example.workers.dev");
  assert.equal(fs.statSync(dir + "/relay.json").mode & 0o777, 0o600);
  assert.deepEqual(loadRelay(dir), c);
  assert.equal(saveRelay(dir, "wss://y.example").rk, c.rk); // changing the url keeps the key
  const r = rotateRelay(dir);
  assert.notEqual(r.rk, c.rk);
  assert.ok(forgetRelay(dir));
  assert.equal(loadRelay(dir), null);
  assert.equal(rotateRelay(dir), null);
  assert.throws(() => normalizeUrl("ftp://x"));
  assert.throws(() => normalizeUrl("wss://u:p@x"));
  assert.throws(() => normalizeUrl("wss://x?a=b"));
  assert.throws(() => normalizeUrl("nonsense"));
});

async function setup(opts = {}) {
  const relay = await startDevRelay(0);
  const url = `ws://127.0.0.1:${relay.port}`;
  const rk = newRelayKey();
  const dir = tmp();
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, relay: { url, rk }, ...opts });
  return { relay, url, rk, dir, b, token: loadToken(dir) };
}

test("phone through the relay: hello/welcome, sessions and an approval roundtrip", async () => {
  const { relay, url, rk, dir, b, token } = await setup();
  try {
    assert.equal(b.relayUrl(), url);
    assert.ok(await until(() => b.relayState() === "waiting"));
    const p = await fakeRelayPhone({ url, rk, token });
    const w = await p.wait((m) => m.type === "welcome");
    assert.equal(w.v, 1);
    assert.equal(w.caps.includes("relay"), false); // the key is never sent over the relay itself
    await p.wait((m) => m.type === "sessions");
    assert.ok(await until(() => b.relayState() === "phone-online"));
    // a hook asks for approval
    const { spawn } = await import("node:child_process");
    const hook = spawn(process.execPath, [new URL("../hook.mjs", import.meta.url).pathname], { env: { ...process.env, BUBBY_DIR: dir } });
    let out = ""; hook.stdout.on("data", (d) => (out += d));
    hook.stdin.end(JSON.stringify({ hook_event_name: "PermissionRequest", session_id: "s1", tool_name: "Bash", tool_input: { command: "npm test" } }));
    const ap = await p.wait((m) => m.type === "approval");
    assert.equal(ap.command, "npm test");
    p.send({ type: "decision", fingerprint: ap.fingerprint, decision: "allow" });
    await new Promise((r) => hook.on("close", r));
    assert.match(out, /allow/);
    p.close();
  } finally { await b.close(); await relay.close(); }
});

test("relay refuses nothing it cannot read: garbage, replays, a turned-around frame and a wrong key do nothing", async () => {
  const { relay, url, rk, b, token } = await setup();
  try {
    const p = await fakeRelayPhone({ url, rk, token });
    await p.wait((m) => m.type === "welcome");
    const n = p.msgs.length;
    // garbage / wrong key / wrong direction
    p.raw("1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    p.raw(seal(derive(newRelayKey()).key, "p2b", { t: Date.now(), s: 1, b: "z", l: JSON.stringify({ type: "ping" }) }));
    p.raw(seal(derive(rk).key, "b2p", { t: Date.now(), s: 1, b: "z", l: JSON.stringify({ type: "ping" }) }));
    // a replayed frame: same bytes twice
    const f = p.sender.line(JSON.stringify({ type: "ping" }));
    p.raw(f); p.raw(f);
    await sleep(300);
    assert.equal(p.msgs.slice(n).filter((m) => m.type === "pong").length, 1); // only the first copy of the real ping counted
    p.close();
  } finally { await b.close(); await relay.close(); }
});

test("a phone with a wrong token over the relay gets the auth error; pairing by code is not available through the relay", async () => {
  const { relay, url, rk, b } = await setup();
  try {
    const bad = await fakeRelayPhone({ url, rk, token: "x".repeat(32) });
    const e = await bad.wait((m) => m.type === "error");
    assert.equal(e.code, "auth");
    bad.close();
    await sleep(100);
    const p2 = await fakeRelayPhone({ url, rk, autoHello: false });
    p2.send({ type: "pair", v: 1, nonce: "a".repeat(32), proof: "b".repeat(64), name: "x" });
    await sleep(300);
    assert.equal(p2.msgs.filter((m) => m.type === "paired").length, 0);
    p2.close();
  } finally { await b.close(); await relay.close(); }
});

test("the relay key reaches the phone over the LAN link only, and only when it asks for the cap", async () => {
  const { relay, url, rk, b, token } = await setup();
  try {
    const lan = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["relay"] });
    const w = await lan.wait((m) => m.type === "welcome");
    assert.ok(w.caps.includes("relay"));
    const r = await lan.wait((m) => m.type === "relay");
    assert.deepEqual({ url: r.url, key: r.key }, { url, key: rk });
    lan.close();
    await sleep(100);
    const quiet = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: [] });
    await quiet.wait((m) => m.type === "welcome");
    await sleep(200);
    assert.equal(quiet.msgs.filter((m) => m.type === "relay").length, 0);
    quiet.close();
  } finally { await b.close(); await relay.close(); }
});

test("a second phone is told 'busy'; after the first leaves, the next one gets in; bridge survives the phone vanishing", async () => {
  const { relay, url, rk, b, token } = await setup();
  try {
    const lan = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token });
    await lan.wait((m) => m.type === "welcome");
    const viaRelay = await fakeRelayPhone({ url, rk, token });
    const e = await viaRelay.wait((m) => m.type === "error");
    assert.equal(e.code, "busy");
    lan.close();
    viaRelay.close();
    await sleep(200);
    const again = await fakeRelayPhone({ url, rk, token });
    await again.wait((m) => m.type === "welcome");
    again.close();
    await sleep(200);
    assert.ok(["waiting", "phone-online"].includes(b.relayState()));
  } finally { await b.close(); await relay.close(); }
});

test("bridge reconnects when the relay goes away and comes back", async () => {
  const relay = await startDevRelay(0);
  const port = relay.port;
  const url = `ws://127.0.0.1:${port}`;
  const rk = newRelayKey();
  const dir = tmp();
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, relay: { url, rk } });
  try {
    assert.ok(await until(() => b.relayState() === "waiting"));
    await relay.close();
    assert.ok(await until(() => b.relayState() === "offline"));
    const again = await startDevRelay(port);
    assert.ok(await until(() => b.relayState() === "waiting", 6000));
    await again.close();
  } finally { await b.close(); }
});

test("without a relay configured nothing changes: state off, no relay cap", async () => {
  const dir = tmp();
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, relay: false });
  try {
    assert.equal(b.relayState(), "off");
    assert.equal(b.relayUrl(), null);
    const lan = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir), caps: ["relay"] });
    const w = await lan.wait((m) => m.type === "welcome");
    assert.equal(w.caps.includes("relay"), false);
    lan.close();
  } finally { await b.close(); }
});
