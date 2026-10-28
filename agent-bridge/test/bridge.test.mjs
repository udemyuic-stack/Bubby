import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";
import tls from "node:tls";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { startBridge } from "../bridge.mjs";
import { createUsageSource } from "../lib/usage.mjs";
import { fakePhone } from "../lib/fake-phone.mjs";
import { sockPath } from "../lib/common.mjs";
import { loadToken, revokeToken } from "../lib/pairing.mjs";

const HOOK = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "hook.mjs");
// never read the real ~/.claude credentials or touch the network in tests
const offline = () => createUsageSource({ readCredentials: async () => null });
const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/cb-"); dirs.push(d); return path.join(d, "b"); };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const start = async (extra = {}) => {
  const dir = tmp();
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, usage: offline(), ...extra });
  return { b, dir, token: loadToken(dir) };
};
const phoneFor = (b, token, extra) => fakePhone({ host: b.host, port: b.port, fp: b.fp, token, ...extra });

const runHook = (dir, payload, env = {}) => new Promise((resolve) => {
  const p = spawn(process.execPath, [HOOK], { env: { ...process.env, BUBBY_DIR: dir, ...env } });
  let out = "";
  p.stdout.on("data", (d) => (out += d));
  p.on("close", (code) => resolve({ code, out }));
  p.stdin.end(typeof payload === "string" ? payload : JSON.stringify(payload));
});
const perm = (command, session = "s1", tool = "Bash") => ({ hook_event_name: "PermissionRequest", session_id: session, tool_name: tool, tool_input: { command } });

test("key is 0600, token is 24+ chars and revocable", async () => {
  const { b, dir, token } = await start();
  assert.equal(fs.statSync(path.join(dir, "key.pem")).mode & 0o777, 0o600);
  assert.ok(token.length >= 24);
  assert.match(b.link(), /^coucou:\/\/pair\?v=1&host=127\.0\.0\.1&port=\d+&fp=[0-9a-f]{64}&token=/);
  await b.close();
  assert.ok(revokeToken(dir));
  assert.equal(loadToken(dir), null);
});

test("hello/welcome, sessions, approval allow roundtrip through hook.mjs", async () => {
  const { b, dir, token } = await start();
  const phone = await phoneFor(b, token);
  const welcome = await phone.wait((m) => m.type === "welcome");
  assert.equal(welcome.v, 1);
  await phone.wait((m) => m.type === "sessions");
  const hook = runHook(dir, perm("npm run build"));
  const ap = await phone.wait((m) => m.type === "approval");
  assert.equal(ap.command, "npm run build");
  assert.equal(ap.tool, "Bash");
  assert.match(ap.fingerprint, /^[0-9a-f]{64}$/);
  assert.equal(ap.truncated, undefined);
  phone.send({ type: "decision", fingerprint: "0".repeat(64), decision: "allow" }); // wrong fp: ignored
  phone.send({ type: "decision", fingerprint: ap.fingerprint, decision: "allow" });
  const { code, out } = await hook;
  assert.equal(code, 0);
  assert.equal(JSON.parse(out).hookSpecificOutput.decision.behavior, "allow");
  await phone.wait((m) => m.type === "approvalResolved" && m.fingerprint === ap.fingerprint);
  phone.close();
  await b.close();
});

test("deny roundtrip; same command twice gives different fingerprints", async () => {
  const { b, dir, token } = await start();
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  const h1 = runHook(dir, perm("ls"));
  const a1 = await phone.wait((m) => m.type === "approval");
  phone.send({ type: "decision", fingerprint: a1.fingerprint, decision: "deny" });
  assert.equal(JSON.parse((await h1).out).hookSpecificOutput.decision.behavior, "deny");
  const h2 = runHook(dir, perm("ls"));
  const a2 = await phone.wait((m) => m.type === "approval" && m.fingerprint !== a1.fingerprint);
  assert.notEqual(a1.fingerprint, a2.fingerprint);
  phone.send({ type: "decision", fingerprint: a2.fingerprint, decision: "deny" });
  await h2;
  phone.close();
  await b.close();
});

test("500-char truncation: truncated flag, allow ignored, deny works", async () => {
  const { b, dir, token } = await start();
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  const hook = runHook(dir, perm("x".repeat(501)));
  const ap = await phone.wait((m) => m.type === "approval");
  assert.equal(ap.command.length, 500);
  assert.equal(ap.truncated, true);
  phone.send({ type: "decision", fingerprint: ap.fingerprint, decision: "allow" });
  phone.send({ type: "ping" });
  await phone.wait((m) => m.type === "pong");
  await new Promise((r) => setTimeout(r, 100));
  phone.send({ type: "decision", fingerprint: ap.fingerprint, decision: "deny" });
  assert.equal(JSON.parse((await hook).out).hookSpecificOutput.decision.behavior, "deny");
  phone.close();
  await b.close();
});

test("exactly 500 chars is not truncated", async () => {
  const { b, dir, token } = await start();
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  const hook = runHook(dir, perm("y".repeat(500)));
  const ap = await phone.wait((m) => m.type === "approval");
  assert.equal(ap.command.length, 500);
  assert.equal(ap.truncated, undefined);
  phone.send({ type: "decision", fingerprint: ap.fingerprint, decision: "deny" });
  await hook;
  phone.close();
  await b.close();
});

test("file tools send only the file name", async () => {
  const { b, dir, token } = await start();
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  const hook = runHook(dir, { hook_event_name: "PermissionRequest", session_id: "s", tool_name: "Write", tool_input: { file_path: "/Users/x/secret/proj/a.txt", content: "TOPSECRET" } });
  const ap = await phone.wait((m) => m.type === "approval");
  assert.equal(ap.command, "a.txt");
  assert.ok(!JSON.stringify(ap).includes("TOPSECRET"));
  phone.send({ type: "decision", fingerprint: ap.fingerprint, decision: "deny" });
  await hook;
  phone.close();
  await b.close();
});

test("approval timeout: hook falls through, phone told approvalResolved", async () => {
  const { b, dir, token } = await start({ approvalTimeoutMs: 200 });
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  const hook = runHook(dir, perm("sleep 1"));
  const ap = await phone.wait((m) => m.type === "approval");
  const { code, out } = await hook;
  assert.equal(code, 0);
  assert.equal(out, "");
  await phone.wait((m) => m.type === "approvalResolved" && m.fingerprint === ap.fingerprint);
  phone.close();
  await b.close();
});

test("hook events update sessions", async () => {
  const { b, dir, token } = await start();
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  await runHook(dir, { hook_event_name: "UserPromptSubmit", session_id: "s", prompt: "SECRET PROMPT" });
  const s = await phone.wait((m) => m.type === "sessions" && m.sessions[0]?.state === "thinking");
  assert.equal(s.sessions[0].pillId, "integration_claude");
  assert.ok(!JSON.stringify(phone.msgs).includes("SECRET PROMPT"));
  await runHook(dir, { hook_event_name: "Stop", session_id: "s" });
  await phone.wait((m) => m.type === "sessions" && m.sessions[0]?.state === "finished");
  await runHook(dir, { hook_event_name: "SessionEnd", session_id: "s" });
  await phone.wait((m) => m.type === "sessions" && m.sessions.length === 0);
  phone.close();
  await b.close();
});

test("no phone connected: hook exits 0 silently and fast", async () => {
  const { b, dir } = await start();
  const t = Date.now();
  const { code, out } = await runHook(dir, perm("ls"));
  assert.equal(code, 0);
  assert.equal(out, "");
  assert.ok(Date.now() - t < 3000);
  await b.close();
});

test("bridge down: hook exits 0 silently, also on garbage stdin", async () => {
  const dir = tmp();
  fs.mkdirSync(dir, { recursive: true });
  for (const input of [perm("ls"), "not json", "", { hook_event_name: "Stop", session_id: "x" }]) {
    const { code, out } = await runHook(dir, input);
    assert.equal(code, 0);
    assert.equal(out, "");
  }
});

test("hook gives up after its wait limit with no output", async () => {
  const { b, dir, token } = await start();
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  const { code, out } = await runHook(dir, perm("ls"), { BUBBY_HOOK_WAIT_MS: "300" });
  assert.equal(code, 0);
  assert.equal(out, "");
  phone.close();
  await b.close();
});

test("wrong token gets error auth and is closed; second phone is refused", async () => {
  const { b, token } = await start();
  const bad = await phoneFor(b, token + "x");
  const err = await bad.wait((m) => m.type === "error");
  assert.equal(err.code, "auth");
  await bad.closed;
  const good = await phoneFor(b, token);
  await good.wait((m) => m.type === "welcome");
  const second = await phoneFor(b, token);
  assert.equal((await second.wait((m) => m.type === "error")).code, "busy");
  good.close();
  await b.close();
});

test("revoked token is refused live", async () => {
  const { b, dir, token } = await start();
  revokeToken(dir);
  const p = await phoneFor(b, token);
  assert.equal((await p.wait((m) => m.type === "error")).code, "auth");
  await b.close();
});

test("pinned certificate: wrong pin fails the handshake", async () => {
  const { b, token } = await start();
  await assert.rejects(phoneFor(b, token, { fp: "0".repeat(64) }));
  await b.close();
});

test("silent connection is dropped; unknown messages ignored; oversize line closes", async () => {
  const { b, token } = await start();
  const phone = await phoneFor(b, token);
  await phone.wait((m) => m.type === "welcome");
  phone.send({ type: "somethingNew", x: 1 });
  phone.send({ type: "ping" });
  await phone.wait((m) => m.type === "pong");
  phone.sock.write("a".repeat(70 * 1024) + "\n");
  await phone.closed;
  await b.close();
});

test("hello must come first", async () => {
  const { b, token } = await start();
  const p = await phoneFor(b, token, { autoHello: false });
  p.send({ type: "ping" });
  await p.closed;
  await b.close();
});

test("binds only to the chosen address; refuses foreign address", async () => {
  const { b } = await start();
  assert.equal(b.host, "127.0.0.1");
  await b.close();
  await assert.rejects(startBridge({ dir: tmp(), host: "203.0.113.9", port: 0 }));
});

test("hook socket is 0600", async () => {
  const { b, dir } = await start();
  assert.equal(fs.statSync(sockPath(dir)).mode & 0o777, 0o600);
  await b.close();
});
