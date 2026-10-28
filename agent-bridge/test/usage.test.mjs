import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { startBridge } from "../bridge.mjs";
import { fakePhone, pairByCode } from "../lib/fake-phone.mjs";
import { loadToken } from "../lib/pairing.mjs";
import { createUsageSource, parseCredentials, parseUsage, USAGE_URL } from "../lib/usage.mjs";
import { createStats, localDay } from "../lib/stats.mjs";

const HOOK = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "hook.mjs");
const SECRET = "sk-ant-oat01-SECRET-TOKEN-never-leaves";
const dirs = [];
const tmpRoot = () => { const d = fs.mkdtempSync("/tmp/cb-usage-"); dirs.push(d); return d; };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const creds = async () => ({ accessToken: SECRET, expiresAt: Date.now() + 3600_000, plan: "max" });
const okBody = JSON.stringify({
  five_hour: { utilization: 68, resets_at: "2025-10-09T08:53:20+00:00" },
  seven_day: { utilization: 42.5, resets_at: "2025-10-13T23:59:59.943648+00:00" },
  seven_day_opus: null,
});
const fakeHttp = (calls, reply = { status: 200, body: okBody }) => async (url, headers) => {
  calls.push({ url, headers });
  if (reply instanceof Error) throw reply;
  return reply;
};

const runHook = (dir, payload) => new Promise((resolve) => {
  const p = spawn(process.execPath, [HOOK], { env: { ...process.env, BUBBY_DIR: dir } });
  p.on("close", resolve);
  p.stdin.end(JSON.stringify(payload));
});

const startWith = async (usage, dir = path.join(tmpRoot(), "b")) => {
  const logs = [];
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, usage, usageMinIntervalMs: 30, log: (l) => logs.push(l) });
  return { b, dir, logs };
};

test("parseCredentials and parseUsage", () => {
  assert.deepEqual(parseCredentials(JSON.stringify({ claudeAiOauth: { accessToken: "t", expiresAt: 5, subscriptionType: "pro" } })), { accessToken: "t", expiresAt: 5, plan: "pro" });
  assert.equal(parseCredentials("{"), null);
  assert.equal(parseCredentials(JSON.stringify({ claudeAiOauth: {} })), null);
  assert.deepEqual(parseUsage(JSON.parse(okBody)), {
    fiveHour: { used: 0.68, resetsAt: Date.parse("2025-10-09T08:53:20+00:00") },
    sevenDay: { used: 0.425, resetsAt: Date.parse("2025-10-13T23:59:59.943648+00:00") },
  });
  assert.deepEqual(parseUsage({ five_hour: { utilization: 150 } }), { fiveHour: { used: 1 } });
  assert.deepEqual(parseUsage({ five_hour: { resets_at: 1760000000 } }), { fiveHour: { resetsAt: 1760000000000 } });
  assert.deepEqual(parseUsage({ five_hour: { utilization: "x" }, seven_day: "nope" }), {});
  assert.deepEqual(parseUsage(null), {});
});

test("usage source: calls the endpoint with the bearer token and beta header, at most once a minute", async () => {
  let t = 1_000_000;
  const calls = [];
  const src = createUsageSource({ httpGet: fakeHttp(calls), readCredentials: creds, now: () => t });
  const s = await src.refresh();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, USAGE_URL);
  assert.equal(calls[0].url, "https://api.anthropic.com/api/oauth/usage");
  assert.equal(calls[0].headers.Authorization, `Bearer ${SECRET}`);
  assert.equal(calls[0].headers["anthropic-beta"], "oauth-2025-04-20");
  assert.equal(s.fiveHour.used, 0.68);
  assert.equal(s.plan, "max");
  assert.ok(!JSON.stringify(s).includes(SECRET));
  t += 30_000;
  await src.refresh();
  assert.equal(calls.length, 1, "not again within a minute");
  t += 31_000;
  await src.refresh();
  assert.equal(calls.length, 2);
});

test("usage source: failures leave the fields out, back off, never log the token", async () => {
  let t = 0;
  const calls = [];
  const logs = [];
  let reply = { status: 401, body: `{"error":"bad token ${SECRET}"}` };
  const src = createUsageSource({ httpGet: async (u, h) => { calls.push(h); if (reply instanceof Error) throw reply; return reply; }, readCredentials: creds, now: () => t, log: (l) => logs.push(l) });
  assert.deepEqual(await src.refresh(), { plan: "max" });
  t += 61_000;
  await src.refresh();
  assert.equal(calls.length, 1, "backs off for longer than the normal minute after an error");
  t += 60_000;
  reply = Object.assign(new Error(`connect failed ${SECRET}`), { code: "ECONNREFUSED" });
  assert.deepEqual(await src.refresh(), { plan: "max" });
  assert.equal(calls.length, 2);
  t += 4 * 60_000 + 1;
  reply = { status: 200, body: okBody };
  assert.equal((await src.refresh()).sevenDay.used, 0.425);
  reply = { status: 200, body: "not json" };
  t += 61_000;
  assert.deepEqual(await src.refresh(), { plan: "max" });
  assert.ok(logs.length >= 1);
  assert.ok(!logs.join("\n").includes(SECRET));
  assert.ok(!logs.join("\n").includes("bad token"));
});

test("usage source: no credentials or expired token => no call, no fields", async () => {
  const calls = [];
  assert.deepEqual(await createUsageSource({ httpGet: fakeHttp(calls), readCredentials: async () => null }).refresh(), {});
  assert.deepEqual(await createUsageSource({ httpGet: fakeHttp(calls), readCredentials: async () => { throw new Error("x"); } }).refresh(), {});
  const expired = createUsageSource({ httpGet: fakeHttp(calls), readCredentials: async () => ({ accessToken: SECRET, expiresAt: 1, plan: "pro" }) });
  assert.deepEqual(await expired.refresh(), { plan: "pro" });
  assert.equal(calls.length, 0);
});

test("bridge sends a usage message after welcome: shape with a faked HTTP source, token never sent", async () => {
  const calls = [];
  const { b, dir, logs } = await startWith(createUsageSource({ httpGet: fakeHttp(calls), readCredentials: creds }));
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir) });
  await phone.wait((m) => m.type === "welcome");
  const u = await phone.wait((m) => m.type === "usage" && m.fiveHour);
  assert.deepEqual(Object.keys(u), ["type", "fiveHour", "sevenDay", "doneToday", "plan", "updatedAt"]);
  assert.deepEqual(u.fiveHour, { used: 0.68, resetsAt: Date.parse("2025-10-09T08:53:20+00:00") });
  assert.deepEqual(u.sevenDay, { used: 0.425, resetsAt: Date.parse("2025-10-13T23:59:59.943648+00:00") });
  assert.equal(u.doneToday, 0);
  assert.equal(u.plan, "max");
  assert.ok(Math.abs(u.updatedAt - Date.now()) < 5000);
  assert.ok(!JSON.stringify(phone.msgs).includes(SECRET));
  assert.ok(!logs.join("\n").includes(SECRET));
  phone.close();
  await b.close();
});

test("usage after paired too; with the source failing the limit fields are absent", async () => {
  const { b } = await startWith(createUsageSource({ httpGet: fakeHttp([], new Error("down")), readCredentials: creds }));
  const { phone, reply } = await pairByCode({ host: b.host, port: b.port, code: b.pairingCode() });
  assert.equal(reply.type, "paired");
  const u = await phone.wait((m) => m.type === "usage");
  await new Promise((r) => setTimeout(r, 100));
  for (const m of phone.msgs.filter((x) => x.type === "usage")) {
    assert.equal(m.fiveHour, undefined);
    assert.equal(m.sevenDay, undefined);
  }
  assert.equal(u.doneToday, 0);
  assert.equal(u.plan, "max");
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
  const off = () => createUsageSource({ readCredentials: async () => null });

  let { b } = await startWith(off(), dir);
  let phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir) });
  await phone.wait((m) => m.type === "usage");
  await runHook(dir, { hook_event_name: "Stop", session_id: "s", transcript_path: transcript });
  await b.flushStats();
  let u = await phone.wait((m) => m.type === "usage" && m.tokensToday !== undefined);
  assert.equal(u.doneToday, 1);
  assert.equal(u.tokensToday, 118); // 10+5+100 (m1 once) + 1+2 (m2); cache reads and old days not counted
  // the transcript grows; only the new part is read
  fs.appendFileSync(transcript, JSON.stringify({ type: "assistant", timestamp: now, message: { id: "m3", usage: { input_tokens: 2, output_tokens: 0 } } }) + "\n");
  await runHook(dir, { hook_event_name: "Stop", session_id: "s", transcript_path: transcript });
  await b.flushStats();
  u = await phone.wait((m) => m.type === "usage" && m.doneToday === 2 && m.tokensToday === 120);
  assert.ok(!JSON.stringify(phone.msgs).includes("SECRET PROMPT"));
  assert.ok(!JSON.stringify(phone.msgs).includes(transcript));
  phone.close();
  await b.close();

  const saved = JSON.parse(fs.readFileSync(path.join(dir, "stats.json"), "utf8"));
  assert.equal(saved.day, localDay());
  assert.equal(saved.doneToday, 2);
  assert.equal(fs.statSync(path.join(dir, "stats.json")).mode & 0o777, 0o600);

  ({ b } = await startWith(off(), dir));
  phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir) });
  u = await phone.wait((m) => m.type === "usage");
  assert.equal(u.doneToday, 2);
  assert.equal(u.tokensToday, 120);
  await runHook(dir, { hook_event_name: "Stop", session_id: "s2" }); // no transcript: still counted
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
  await s.recordStop("relative/path.jsonl"); // ignored path, still a finished turn
  assert.deepEqual(s.today(), { doneToday: 2 });
  assert.equal(createStats(dir, { now: () => d }).today().doneToday, 2);
  d = new Date(2026, 9, 29, 0, 1);
  assert.deepEqual(s.today(), { doneToday: 0 });
  assert.deepEqual(createStats(dir, { now: () => d }).today(), { doneToday: 0 });
  fs.writeFileSync(path.join(dir, "stats.json"), "garbage");
  assert.deepEqual(createStats(dir, { now: () => d }).today(), { doneToday: 0 });
});

test("usage changes are throttled and the phone gets one message per change", async () => {
  const { b, dir } = await startWith(createUsageSource({ readCredentials: async () => null }));
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir) });
  await phone.wait((m) => m.type === "usage");
  for (let i = 0; i < 3; i++) await runHook(dir, { hook_event_name: "Stop", session_id: "s" });
  await phone.wait((m) => m.type === "usage" && m.doneToday === 3);
  const usages = phone.msgs.filter((m) => m.type === "usage");
  assert.ok(usages.length <= 4);
  for (let i = 1; i < usages.length; i++) assert.ok(usages[i].doneToday > usages[i - 1].doneToday, "no duplicate messages for no change");
  phone.close();
  await b.close();
});
