import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { startBridge } from "../bridge.mjs";
import { fakePhone } from "../lib/fake-phone.mjs";
import { loadToken } from "../lib/pairing.mjs";
import { runDecisionOutput } from "../hook.mjs";

const HOOK = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "hook.mjs");
const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/cr-"); dirs.push(d); return d; };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

// a stand-in for `claude`: prints the JSON the real one prints with --output-format json
const fakeClaude = (dir, body) => {
  const f = path.join(dir, "claude");
  fs.writeFileSync(f, `#!/usr/bin/env node\n${body}\n`, { mode: 0o755 });
  return f;
};

const start = async (extra) => {
  const d = tmp();
  const dir = path.join(d, "b");
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "T", mdns: false, ...extra });
  return { b, d, dir, token: loadToken(dir) };
};

test("run is not offered unless the bridge was started with --allow-run", async () => {
  const { b, token } = await start();
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  const w = await phone.wait((m) => m.type === "welcome");
  assert.ok(!w.caps.includes("run"));
  phone.send({ type: "run", id: "t1", prompt: "hello" });
  await new Promise((r) => setTimeout(r, 200));
  assert.ok(!phone.msgs.some((m) => m.type === "runState"));
  phone.close(); await b.close();
});

test("a task from the phone runs claude -p in the folder and reports the answer", async () => {
  const d = tmp();
  const bin = fakeClaude(d, `const a=process.argv.slice(2);console.log(JSON.stringify({result:"did: "+a[1]+" in "+process.cwd()+" run="+process.env.BUBBY_RUN,is_error:false}))`);
  const work = path.join(d, "work"); fs.mkdirSync(work);
  const { b, token } = await start({ run: { bin, workdir: work } });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  const w = await phone.wait((m) => m.type === "welcome");
  assert.ok(w.caps.includes("run"));
  phone.send({ type: "run", id: "t1", prompt: "list the files" });
  await phone.wait((m) => m.type === "runState" && m.state === "running");
  const done = await phone.wait((m) => m.type === "runState" && m.state === "done");
  assert.equal(done.id, "t1");
  assert.match(done.text, /did: list the files in .*work run=1/);
  phone.close(); await b.close();
});

test("one task at a time; a bad task is refused; stop kills it", async () => {
  const d = tmp();
  const bin = fakeClaude(d, `setTimeout(()=>console.log(JSON.stringify({result:"late"})),20000)`);
  const { b, token } = await start({ run: { bin, workdir: d } });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  await phone.wait((m) => m.type === "welcome");
  phone.send({ type: "run", id: "bad", prompt: "x".repeat(5000) });
  assert.equal((await phone.wait((m) => m.type === "runState" && m.id === "bad")).state, "error");
  phone.send({ type: "run", id: "a1", prompt: "slow" });
  await phone.wait((m) => m.type === "runState" && m.id === "a1" && m.state === "running");
  phone.send({ type: "run", id: "a2", prompt: "second" });
  assert.equal((await phone.wait((m) => m.type === "runState" && m.id === "a2")).state, "error");
  phone.send({ type: "runStop", id: "a1" });
  assert.equal((await phone.wait((m) => m.type === "runState" && m.id === "a1" && m.state === "stopped")).state, "stopped");
  phone.close(); await b.close();
});

test("a tool of a phone-started task waits for the phone's answer (PreToolUse), read-only tools do not", async () => {
  const d = tmp();
  const bin = fakeClaude(d, `setTimeout(()=>console.log(JSON.stringify({result:"x"})),300)`);
  const { b, dir, token } = await start({ run: { bin, workdir: d } });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token, caps: ["run"] });
  await phone.wait((m) => m.type === "welcome");
  const hook = (payload, env = {}) => new Promise((resolve) => {
    const p = spawn(process.execPath, [HOOK], { env: { ...process.env, BUBBY_DIR: dir, BUBBY_RUN: "1", ...env } });
    let out = ""; p.stdout.on("data", (x) => (out += x)); p.on("close", () => resolve(out));
    p.stdin.end(JSON.stringify(payload));
  });
  const bash = { hook_event_name: "PreToolUse", session_id: "s9", tool_name: "Bash", tool_input: { command: "rm -rf build" } };
  const pending = hook(bash);
  const ap = await phone.wait((m) => m.type === "approval" && m.command.includes("rm -rf build"));
  phone.send({ type: "decision", fingerprint: ap.fingerprint, decision: "allow" });
  const o1 = JSON.parse(await pending).hookSpecificOutput;
  assert.equal(o1.hookEventName, "PreToolUse"); assert.equal(o1.permissionDecision, "allow");
  const pending2 = hook(bash);
  const ap2 = await phone.wait((m) => m.type === "approval" && m.fingerprint !== ap.fingerprint);
  phone.send({ type: "decision", fingerprint: ap2.fingerprint, decision: "deny" });
  assert.equal(JSON.parse(await pending2).hookSpecificOutput.permissionDecision, "deny");
  // a read-only tool is not asked: no output, no approval card
  const before = phone.msgs.filter((m) => m.type === "approval").length;
  const o3 = await hook({ ...bash, tool_name: "Read", tool_input: { file_path: "/x" } });
  assert.equal(o3.trim(), "");
  assert.equal(phone.msgs.filter((m) => m.type === "approval").length, before);
  // without BUBBY_RUN nothing changes for PreToolUse (display only)
  const o4 = await hook(bash, { BUBBY_RUN: "" });
  assert.equal(o4.trim(), "");
  assert.equal(runDecisionOutput(undefined).hookSpecificOutput.permissionDecision, "deny");
  phone.close(); await b.close();
});
