import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { EventEmitter } from "node:events";
import { startBridge } from "../bridge.mjs";
import { fakePhone } from "../lib/fake-phone.mjs";
import { loadToken } from "../lib/pairing.mjs";
import { terminalCommand, openTerminal, terminalAvailable } from "../lib/terminal.mjs";

const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/tm-"); dirs.push(d); return d; };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const UUID = "11111111-2222-4333-8444-555555555555";

test("the prompt is one argv element after --, never a shell string", () => {
  const prompt = `say "hi"; $(rm -rf /) \n -x`;
  const c = terminalCommand({ cwd: "/p/a", bin: "claude", prompt, sessionId: UUID, resume: false });
  assert.equal(c.cmd, "ghostty");
  assert.deepEqual(c.args, ["--working-directory=/p/a", "-e", "claude", "--session-id", UUID, "--", prompt]);
  const r = terminalCommand({ cwd: "/p/a", bin: "claude", prompt: "go", sessionId: UUID, resume: true });
  assert.deepEqual(r.args.slice(2, 5), ["claude", "--resume", UUID]);
});

test("openTerminal reports a missing terminal and a started one", async () => {
  const bad = () => { const e = new EventEmitter(); setImmediate(() => e.emit("error", Object.assign(new Error("x"), { code: "ENOENT" }))); return e; };
  const miss = await openTerminal({ cwd: "/", bin: "claude", prompt: "p", sessionId: UUID }, bad);
  assert.equal(miss.ok, false);
  assert.match(miss.reason, /ghostty.*not found/);
  const good = () => { const e = new EventEmitter(); e.unref = () => {}; setImmediate(() => e.emit("spawn")); return e; };
  assert.deepEqual(await openTerminal({ cwd: "/", bin: "claude", prompt: "p", sessionId: UUID }, good), { ok: true });
});

test("terminalAvailable needs a display", () => {
  assert.equal(terminalAvailable({}), false);
  assert.equal(terminalAvailable({ WAYLAND_DISPLAY: "wayland-0" }), true);
});

const start = async (run) => {
  const d = tmp();
  const dir = path.join(d, "b");
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "T", mdns: false, run });
  return { b, d, token: loadToken(dir) };
};

test("with --terminal a phone task opens a terminal and reports the session id", async () => {
  const d = tmp();
  const calls = [];
  const { b, token } = await start({ bin: "claude", workdir: d, root: path.dirname(d), terminal: true, terminalAvailable: () => true, openTerminal: async (o) => { calls.push(o); return { ok: true }; } });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  await phone.wait((m) => m.type === "welcome");
  phone.send({ type: "run", id: "t1", prompt: "list files" });
  const st = await phone.wait((m) => m.type === "runState" && m.id === "t1");
  assert.equal(st.state, "done");
  assert.match(st.sessionId, /^[0-9a-f-]{36}$/);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].cwd, d);
  assert.equal(calls[0].prompt, "list files");
  assert.equal(calls[0].resume, false);
  phone.close(); await b.close();
});

test("a failing terminal and a bad prompt are reported, not silent", async () => {
  const d = tmp();
  const { b, token } = await start({ bin: "claude", workdir: d, root: path.dirname(d), terminal: true, terminalAvailable: () => true, openTerminal: async () => ({ ok: false, reason: "no ghostty" }) });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  await phone.wait((m) => m.type === "welcome");
  phone.send({ type: "run", id: "t1", prompt: "go" });
  const e = await phone.wait((m) => m.type === "runState" && m.id === "t1");
  assert.deepEqual([e.state, e.text], ["error", "no ghostty"]);
  phone.send({ type: "run", id: "t2", prompt: "x".repeat(5000) });
  assert.equal((await phone.wait((m) => m.type === "runState" && m.id === "t2")).state, "error");
  phone.close(); await b.close();
});

test("no display: the task falls back to the headless runner", async () => {
  const d = tmp();
  const f = path.join(d, "claude");
  fs.writeFileSync(f, `#!/usr/bin/env node\nconsole.log(JSON.stringify({result:"headless"}))\n`, { mode: 0o755 });
  let opened = 0;
  const { b, token } = await start({ bin: f, workdir: d, root: path.dirname(d), terminal: true, terminalAvailable: () => false, openTerminal: async () => { opened++; return { ok: true }; } });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  await phone.wait((m) => m.type === "welcome");
  phone.send({ type: "run", id: "t1", prompt: "go" });
  const done = await phone.wait((m) => m.type === "runState" && m.state === "done");
  assert.equal(done.text, "headless");
  assert.equal(opened, 0);
  phone.close(); await b.close();
});

test("a session the bridge opened can be continued from the phone, once it is idle", async () => {
  const d = tmp();
  const calls = [];
  const { b, token } = await start({ bin: "claude", workdir: d, root: path.dirname(d), terminal: true, terminalAvailable: () => true, openTerminal: async (o) => { calls.push(o); return { ok: true }; } });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  await phone.wait((m) => m.type === "welcome");
  phone.send({ type: "run", id: "t1", prompt: "first" });
  const first = await phone.wait((m) => m.type === "runState" && m.id === "t1");
  phone.send({ type: "run", id: "t2", prompt: "second", sessionId: first.sessionId });
  const second = await phone.wait((m) => m.type === "runState" && m.id === "t2");
  assert.equal(second.state, "done");
  assert.equal(second.sessionId, first.sessionId);
  assert.equal(calls[1].resume, true);
  assert.equal(calls[1].sessionId, first.sessionId);
  phone.send({ type: "run", id: "t3", prompt: "third", sessionId: "99999999-2222-4333-8444-555555555555" });
  const unknown = await phone.wait((m) => m.type === "runState" && m.id === "t3");
  assert.deepEqual([unknown.state, unknown.text], ["error", "unknown session"]);
  phone.close(); await b.close();
});
