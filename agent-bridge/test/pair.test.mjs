import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import tls from "node:tls";
import crypto from "node:crypto";
import { startBridge } from "../bridge.mjs";
import { fakePhone, pairByCode } from "../lib/fake-phone.mjs";
import { ensureCert } from "../lib/cert.mjs";
import { loadToken, isPaired, pairProof, createCodePairing } from "../lib/pairing.mjs";

const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/cb-pair-"); dirs.push(d); return path.join(d, "b"); };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const start = async (extra = {}) => {
  const dir = tmp();
  const codes = [];
  const logs = [];
  const b = await startBridge({
    dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false,
    onCode: (c, why) => codes.push({ c, why }), log: (l) => logs.push(l), ...extra,
  });
  return { b, dir, codes, logs };
};
const pair = (b, code, extra = {}) => pairByCode({ host: b.host, port: b.port, code, ...extra });

test("proof is HMAC-SHA256(code, 'bubby-pair-v1|' + fp + '|' + nonce) in lowercase hex", () => {
  const expected = crypto.createHmac("sha256", "123456").update("bubby-pair-v1|" + "ab".repeat(32) + "|" + "00".repeat(16)).digest("hex");
  assert.equal(pairProof("123456", "ab".repeat(32), "00".repeat(16)), expected);
  assert.match(expected, /^[0-9a-f]{64}$/);
});

test("pairing by code: paired reply with the token, then an authenticated connection", async () => {
  const { b, dir, logs } = await start();
  const code = b.pairingCode();
  assert.match(code, /^\d{6}$/);
  assert.equal(b.paired(), false);
  const { phone, reply } = await pair(b, code, { name: "Pixel" });
  assert.deepEqual(reply, { type: "paired", token: loadToken(dir), name: "Test" });
  assert.equal(phone.fpSeen, b.fp);
  await phone.wait((m) => m.type === "sessions");
  phone.send({ type: "ping" });
  await phone.wait((m) => m.type === "pong");
  assert.ok(isPaired(dir));
  assert.equal(b.pairingCode(), null, "code consumed, no new one until --new-pairing");
  assert.ok(!logs.join("\n").includes(code), "code never logged");
  assert.ok(!logs.join("\n").includes(reply.token), "token never logged");
  phone.close();
  await phone.closed;
  // the token from "paired" works with v1 hello on the next connection
  const again = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: reply.token });
  await again.wait((m) => m.type === "welcome");
  again.close();
  // once paired, pairing is closed
  const late = await pair(b, code);
  assert.deepEqual(late.reply, { type: "error", error: "not_pairing" });
  await late.phone.closed;
  await b.close();
});

test("wrong code: bad_code, connection closed, code burnt and a new one printed", async () => {
  const { b, codes, logs } = await start();
  const first = b.pairingCode();
  const wrong = first === "000000" ? "000001" : "000000";
  const { phone, reply } = await pair(b, wrong);
  assert.deepEqual(reply, { type: "error", error: "bad_code" });
  await phone.closed;
  const second = b.pairingCode();
  assert.notEqual(second, first);
  assert.deepEqual(codes.map((x) => x.why), ["start", "failed"]);
  // the burnt code no longer works, the new one does
  const old = await pair(b, first);
  assert.equal(old.reply.error, "bad_code");
  const third = b.pairingCode();
  const ok = await pair(b, third);
  assert.equal(ok.reply.type, "paired");
  assert.ok(!logs.join("\n").match(new RegExp(`${first}|${second}|${third}`)));
  ok.phone.close();
  await b.close();
});

test("MITM: the phone saw another certificate => bad_code even with the right code", async () => {
  const { b, dir } = await start();
  // simulated: the proof is made with a different fingerprint
  const sim = await pair(b, b.pairingCode(), { fpSeen: "f".repeat(64) });
  assert.equal(sim.reply.error, "bad_code");
  // real TLS proxy with its own certificate, forwarding the plaintext to the bridge
  const evil = ensureCert(tmp());
  const proxy = tls.createServer({ key: evil.key, cert: evil.cert }, (inner) => {
    const outer = tls.connect({ host: b.host, port: b.port, rejectUnauthorized: false });
    inner.pipe(outer).pipe(inner);
    inner.on("error", () => {}); outer.on("error", () => {});
    inner.on("close", () => outer.destroy()); outer.on("close", () => inner.destroy());
  });
  await new Promise((r) => proxy.listen(0, "127.0.0.1", r));
  const code = b.pairingCode();
  const mitm = await pairByCode({ host: "127.0.0.1", port: proxy.address().port, code });
  assert.equal(mitm.phone.fpSeen, evil.fp);
  assert.notEqual(evil.fp, b.fp);
  assert.deepEqual(mitm.reply, { type: "error", error: "bad_code" });
  assert.notEqual(b.pairingCode(), code, "the code the MITM relayed is burnt");
  assert.equal(isPaired(dir), false);
  proxy.close();
  await b.close();
});

test("malformed pair messages are failed attempts", async () => {
  const { b } = await start();
  for (const bad of [
    { type: "pair", v: 1, nonce: "short", proof: "0".repeat(64) },
    { type: "pair", v: 1, nonce: "0".repeat(32) },
    { type: "pair", v: 2, nonce: "0".repeat(32), proof: pairProof(b.pairingCode(), b.fp, "0".repeat(32)) },
  ]) {
    const before = b.pairingCode();
    const p = await fakePhone({ host: b.host, port: b.port, autoHello: false });
    p.send(bad);
    assert.equal((await p.wait((m) => m.type === "error")).error, "bad_code");
    await p.closed;
    assert.notEqual(b.pairingCode(), before);
  }
  await b.close();
});

test("rate limit: after 5 failures in a minute even the right code gets slow_down", async () => {
  const { b, dir } = await start();
  for (let i = 0; i < 5; i++) {
    const c = b.pairingCode();
    const { reply } = await pair(b, c === "999999" ? "999998" : "999999");
    assert.equal(reply.error, "bad_code");
  }
  const code = b.pairingCode();
  const { reply, phone } = await pair(b, code);
  assert.deepEqual(reply, { type: "error", error: "slow_down" });
  await phone.closed;
  assert.equal(b.pairingCode(), code, "a refused (not checked) attempt does not burn the code");
  assert.equal(isPaired(dir), false);
  await b.close();
});

test("rate limit window slides (unit, fake clock)", () => {
  let t = 0;
  const shown = [];
  const cp = createCodePairing({ fp: "a".repeat(64), now: () => t, onCode: (c) => shown.push(c) });
  cp.start();
  const n = "1".repeat(32);
  for (let i = 0; i < 5; i++) assert.equal(cp.attempt({ v: 1, nonce: n, proof: "0".repeat(64) }), "bad_code");
  assert.equal(cp.attempt({ v: 1, nonce: n, proof: pairProof(cp.current(), "a".repeat(64), n) }), "slow_down");
  t = 60_001;
  assert.equal(cp.attempt({ v: 1, nonce: n, proof: pairProof(cp.current(), "a".repeat(64), n) }), "ok");
  assert.equal(cp.current(), null);
  assert.equal(cp.attempt({ v: 1, nonce: n, proof: "0".repeat(64) }), "not_pairing");
  assert.equal(shown.length, 6);
  cp.stop();
});

test("code expires after its TTL and a new one is printed", async () => {
  const { b, codes } = await start({ codeTtlMs: 150 });
  const first = b.pairingCode();
  await new Promise((r) => setTimeout(r, 250));
  assert.ok(codes.some((x) => x.why === "expired"));
  assert.notEqual(b.pairingCode(), first);
  assert.equal((await pair(b, first)).reply.error, "bad_code");
  await b.close();
});

test("v1 hello still works and closes code pairing; --new-pairing reopens it", async () => {
  const { b, dir } = await start();
  const token = loadToken(dir);
  const p = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token });
  await p.wait((m) => m.type === "welcome");
  assert.ok(isPaired(dir));
  assert.equal(b.pairingCode(), null);
  b.newPairing();
  await p.closed;
  assert.equal(isPaired(dir), false);
  assert.notEqual(loadToken(dir), token);
  const { reply, phone } = await pair(b, b.pairingCode());
  assert.equal(reply.token, loadToken(dir));
  phone.close();
  await b.close();
});

test("a restart keeps the paired state: no code offered", async () => {
  const { b, dir } = await start();
  const { phone } = await pair(b, b.pairingCode());
  phone.close();
  await b.close();
  const b2 = await startBridge({ dir, host: "127.0.0.1", port: 0, mdns: false });
  assert.equal(b2.paired(), true);
  assert.equal(b2.pairingCode(), null);
  await b2.close();
});
