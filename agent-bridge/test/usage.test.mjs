import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { startBridge } from "../bridge.mjs";
import { fakePhone, pairByCode } from "../lib/fake-phone.mjs";
import { loadToken } from "../lib/pairing.mjs";
import { parseWindow, parseRateLimits, createLimits } from "../lib/limits.mjs";
import { createStats, localDay } from "../lib/stats.mjs";

const HOOK = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "hook.mjs");
const dirs = [];
const tmpRoot = () => { const d = fs.mkdtempSync("/tmp/cb-usage-"); dirs.push(d); return d; };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const run = (dir, payload, args = []) => new Promise((resolve) => {
  const p = spawn(process.execPath, [HOOK, ...args], { env: { ...process.env, BUBBY_DIR: dir } });
  let out = "";
  p.stdout.on("data", (d) => (out += d));
  p.on("close", (code) => resolve({ code, out }));
  p.stdin.end(typeof payload === "string" ? payload : JSON.stringify(payload));
});
const runHook = (dir, payload) => run(dir, payload);
const statusLine = (dir, payload) => run(dir, payload, ["--statusline"]);

const startWith = async (dir = path.join(tmpRoot(), "b"), extra = {}) => {
  const logs = [];
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, usageMinIntervalMs: 30, log: (l) => logs.push(l), ...extra });
  return { b, dir, logs };
};
const connect = (b, dir) => fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir) });

const NOW = Date.now();
const sec = (ms) => Math.floor(ms / 1000);

test("statusLine numbers: percent to 0..1, seconds to ms, out-of-range values dropped", () => {
  assert.deepEqual(parseWindow({ used_percentage: 68, resets_at: sec(NOW) + 3600 }, NOW), { used: 0.68, resetsAt: (sec(NOW) + 3600) * 1000 });
  assert.deepEqual(parseWindow({ used_percentage: 130 }, NOW), { used: 1 }, "may exceed 100: clamped");
  assert.equal(parseWindow({ used_percentage: -1 }, NOW), undefined);
  assert.equal(parseWindow({ used_percentage: "50" }, NOW), undefined);
  assert.equal(parseWindow({ used_percentage: NaN }, NOW), undefined);
  // milliseconds sent instead of seconds: far more than 400 days ahead => resetsAt dropped
  assert.deepEqual(parseWindow({ used_percentage: 10, resets_at: NOW + 3600_000 }, NOW), { used: 0.1 });
  assert.deepEqual(parseRateLimits({ five_hour: { used_percentage: 5 }, seven_day: null, plan: "Max" }, NOW), { fiveHour: { used: 0.05 }, plan: "max" });
  assert.deepEqual(parseRateLimits("x"), {});
  assert.deepEqual(parseRateLimits({ plan: "<script>" }), {});
});

test("limits: last good value kept with updatedAt, a past reset counts as 0, survives a restart", () => {
  const dir = path.join(tmpRoot(), "b");
  let t = NOW;
  const l = createLimits(dir, { now: () => t });
  assert.deepEqual(l.snapshot(), {});
  assert.equal(l.update({ five_hour: { used_percentage: 40, resets_at: sec(NOW) + 60 }, seven_day: { used_percentage: 10, resets_at: sec(NOW) + 86400 } }), true);
  assert.equal(l.update({}), false, "nothing usable: keep the last value");
  assert.equal(l.update({ five_hour: { used_percentage: -5 } }), false);
  let s = l.snapshot();
  assert.equal(s.fiveHour.used, 0.4);
  assert.equal(s.updatedAt, NOW);
  t = NOW + 120_000; // past the five-hour reset
  s = l.snapshot();
  assert.equal(s.fiveHour.used, 0);
  assert.equal(s.sevenDay.used, 0.1);
  assert.equal(fs.statSync(path.join(dir, "limits.json")).mode & 0o777, 0o600);
  const again = createLimits(dir, { now: () => NOW });
  assert.equal(again.snapshot().fiveHour.used, 0.4);
  assert.equal(again.snapshot().updatedAt, NOW);
});

test("statusLine relay: only rate_limits reaches the bridge; the user's own status line keeps its output", async () => {
  const { b, dir } = await startWith();
  const phone = await connect(b, dir);
  await phone.wait((m) => m.type === "usage");
  const recv = path.join(dir, "..", "seen.txt");
  fs.writeFileSync(path.join(dir, "statusline-previous.json"), JSON.stringify({ type: "command", command: `cat > '${recv}'; printf 'my line'`, padding: 1 }));
  const payload = {
    session_id: "s1", cwd: "/home/me/SECRET-PROJECT", model: { display_name: "Opus" }, transcript_path: "/home/me/t.jsonl",
    rate_limits: { five_hour: { used_percentage: 68, resets_at: sec(NOW) + 3600 }, seven_day: { used_percentage: 42.5, resets_at: sec(NOW) + 5 * 86400 } },
  };
  const r = await statusLine(dir, payload);
  assert.equal(r.code, 0);
  assert.equal(r.out, "my line");
  assert.deepEqual(JSON.parse(fs.readFileSync(recv, "utf8")), payload, "same payload on its stdin");
  const u = await phone.wait((m) => m.type === "usage" && m.fiveHour);
  assert.deepEqual(Object.keys(u), ["type", "fiveHour", "sevenDay", "doneToday", "updatedAt", "limitsAgeMs"]);
  assert.deepEqual(u.fiveHour, { used: 0.68, resetsAt: (sec(NOW) + 3600) * 1000 });
  assert.equal(u.sevenDay.used, 0.425);
  assert.ok(Math.abs(u.updatedAt - Date.now()) < 5000);
  assert.ok(!JSON.stringify(phone.msgs).includes("SECRET-PROJECT"));
  assert.ok(!JSON.stringify(phone.msgs).includes("Opus"));
  phone.close();
  await b.close();
});

test("statusLine relay: no previous line prints nothing; a hanging one is killed; bridge down is fine", async () => {
  const root = tmpRoot();
  const dir = path.join(root, "b");
  fs.mkdirSync(dir, { recursive: true });
  const rl = { rate_limits: { five_hour: { used_percentage: 1 } } };
  assert.deepEqual(await statusLine(dir, rl), { code: 0, out: "" });
  assert.deepEqual(await statusLine(dir, "garbage"), { code: 0, out: "" });
  fs.writeFileSync(path.join(dir, "statusline-previous.json"), JSON.stringify({ command: "sleep 30; echo late" }));
  const t = Date.now();
  assert.deepEqual(await statusLine(dir, rl), { code: 0, out: "" });
  assert.ok(Date.now() - t < 8000);
  fs.writeFileSync(path.join(dir, "statusline-previous.json"), JSON.stringify({ command: "echo hi; (sleep 30 &)" }));
  const t2 = Date.now();
  assert.equal((await statusLine(dir, rl)).out, "hi\n");
  assert.ok(Date.now() - t2 < 3000, "a background child holding the pipe does not hold the status line");
});

test("usage without any statusLine payload: limit fields absent, doneToday and updatedAt present", async () => {
  const { b } = await startWith();
  const { phone, reply } = await pairByCode({ host: b.host, port: b.port, code: b.pairingCode() });
  assert.equal(reply.type, "paired");
  const u = await phone.wait((m) => m.type === "usage");
  assert.deepEqual(Object.keys(u), ["type", "doneToday", "updatedAt"]);
  phone.close();
  await b.close();
});

test("doneToday counts Stop hooks, survives a restart; tokensToday from the transcript", async () => {
  const root = tmpRoot();
  const dir = path.join(root, "b");
  const transcript = path.join(root, "t.jsonl");
  const now = new Date().toISOString();
  const lines = [
    { type: "user", timestamp: now, message: { role: "user", content: "SECRET PROMPT" } },
    { type: "assistant", timestamp: now, message: { id: "m1", usage: { input_tokens: 10, output_tokens: 5, cache_creation_input_tokens: 100, cache_read_input_tokens: 9999 } } },
    { type: "assistant", timestamp: now, message: { id: "m1", usage: { input_tokens: 10, output_tokens: 5, cache_creation_input_tokens: 100, cache_read_input_tokens: 9999 } } },
    { type: "assistant", timestamp: "2001-01-01T12:00:00Z", message: { id: "old", usage: { input_tokens: 1000000, output_tokens: 1 } } },
    { type: "assistant", timestamp: now, message: { id: "m2", usage: { input_tokens: 1, output_tokens: 2 } } },
  ];
  fs.writeFileSync(transcript, lines.map((l) => JSON.stringify(l)).join("\n") + "\n");

  let { b } = await startWith(dir);
  let phone = await connect(b, dir);
  await phone.wait((m) => m.type === "usage");
  await runHook(dir, { hook_event_name: "Stop", session_id: "s", transcript_path: transcript });
  await b.flushStats();
  let u = await phone.wait((m) => m.type === "usage" && m.tokensToday !== undefined);
  assert.equal(u.doneToday, 1);
  assert.equal(u.tokensToday, 118); // 10+5+100 (m1 once) + 1+2 (m2); cache reads and old days not counted
  fs.appendFileSync(transcript, JSON.stringify({ type: "assistant", timestamp: now, message: { id: "m3", usage: { input_tokens: 2, output_tokens: 0 } } }) + "\n");
  await runHook(dir, { hook_event_name: "Stop", session_id: "s", transcript_path: transcript });
  await b.flushStats();
  u = await phone.wait((m) => m.type === "usage" && m.doneToday === 2 && m.tokensToday === 120);
  assert.ok(!JSON.stringify(phone.msgs).includes("SECRET PROMPT"));
  assert.ok(!JSON.stringify(phone.msgs).includes(transcript));
  // another agent's turn is not a Claude Code turn
  await run(dir, { hook_event_name: "AfterAgent", session_id: "g" }, ["--agent", "gemini", "Stop"]);
  phone.close();
  await b.close();

  const saved = JSON.parse(fs.readFileSync(path.join(dir, "stats.json"), "utf8"));
  assert.equal(saved.day, localDay());
  assert.equal(saved.doneToday, 2);
  assert.equal(fs.statSync(path.join(dir, "stats.json")).mode & 0o777, 0o600);

  ({ b } = await startWith(dir));
  phone = await connect(b, dir);
  u = await phone.wait((m) => m.type === "usage");
  assert.equal(u.doneToday, 2);
  assert.equal(u.tokensToday, 120);
  await runHook(dir, { hook_event_name: "Stop", session_id: "s2" });
  await phone.wait((m) => m.type === "usage" && m.doneToday === 3);
  phone.close();
  await b.close();
});

test("stats: a new local day starts from zero; tokensToday absent until a transcript is read", async () => {
  const dir = path.join(tmpRoot(), "b");
  let d = new Date(2026, 9, 28, 23, 59);
  const s = createStats(dir, { now: () => d });
  assert.deepEqual(s.today(), { doneToday: 0 });
  await s.recordStop();
  await s.recordStop("relative/path.jsonl");
  assert.deepEqual(s.today(), { doneToday: 2 });
  assert.equal(createStats(dir, { now: () => d }).today().doneToday, 2);
  d = new Date(2026, 9, 29, 0, 1);
  assert.deepEqual(s.today(), { doneToday: 0 });
  fs.writeFileSync(path.join(dir, "stats.json"), "garbage");
  assert.deepEqual(createStats(dir, { now: () => d }).today(), { doneToday: 0 });
});

test("usage changes are throttled; no duplicate message without a change", async () => {
  const { b, dir } = await startWith();
  const phone = await connect(b, dir);
  await phone.wait((m) => m.type === "usage");
  for (let i = 0; i < 3; i++) await runHook(dir, { hook_event_name: "Stop", session_id: "s" });
  await phone.wait((m) => m.type === "usage" && m.doneToday === 3);
  const usages = phone.msgs.filter((m) => m.type === "usage");
  assert.ok(usages.length <= 4);
  for (let i = 1; i < usages.length; i++) assert.ok(usages[i].doneToday > usages[i - 1].doneToday);
  phone.close();
  await b.close();
});

test("the OAuth path is gone: nothing reads Claude's credentials", () => {
  const root = path.join(path.dirname(fileURLToPath(import.meta.url)), "..");
  for (const f of ["bridge.mjs", "hook.mjs", ...fs.readdirSync(path.join(root, "lib")).map((x) => `lib/${x}`)]) {
    const src = fs.readFileSync(path.join(root, f), "utf8");
    assert.ok(!src.includes(".credentials.json") && !src.includes("api/oauth"), f);
  }
});

test("usage carries the age of the limit numbers (measured on the computer); a new status line makes them fresh again", async () => {
  const root = tmpRoot();
  const dir = path.join(root, "b");
  fs.mkdirSync(dir, { recursive: true });
  // numbers saved three hours ago, then the bridge starts again: they must look old, not live
  fs.writeFileSync(path.join(dir, "limits.json"), JSON.stringify({ fiveHour: { used: 0.5, resetsAt: Date.now() + 3600_000 }, updatedAt: Date.now() - 3 * 3600_000 }));
  const { b, logs } = await startWith(dir);
  const phone = await connect(b, dir);
  const first = await phone.wait((m) => m.type === "usage");
  assert.equal(first.fiveHour.used, 0.5);
  assert.ok(first.limitsAgeMs > 2.9 * 3600_000 && first.limitsAgeMs < 3.1 * 3600_000, `age ${first.limitsAgeMs}`);
  assert.ok(b.limitsAgeMs() > 2.9 * 3600_000);
  // Claude Code answers: the status line brings 57 %
  await statusLine(dir, { session_id: "s1", rate_limits: { five_hour: { used_percentage: 57, resets_at: sec(NOW) + 3600 } } });
  const fresh = await phone.wait((m) => m.type === "usage" && m.fiveHour?.used === 0.57);
  assert.ok(fresh.limitsAgeMs < 5000, `age ${fresh.limitsAgeMs}`);
  assert.ok(logs.some((l) => /limits: 5 h 57%/.test(l)));
  phone.close(); await b.close();
});

test("without any limits there is no age field", async () => {
  const { b, dir } = await startWith();
  const phone = await connect(b, dir);
  const u = await phone.wait((m) => m.type === "usage");
  assert.equal(u.limitsAgeMs, undefined);
  phone.close(); await b.close();
});
