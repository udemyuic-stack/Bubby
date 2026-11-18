import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { startBridge } from "../bridge.mjs";
import { fakePhone } from "../lib/fake-phone.mjs";
import { loadToken } from "../lib/pairing.mjs";
import { createDesktopSessions } from "../lib/desktopSessions.mjs";

const SID = "62b4de35-7cee-4db2-8a4a-df13fc231ec8";
const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/ds-"); dirs.push(d); return d; };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const layout = (cwd, ageMs) => {
  const root = tmp();
  const acct = path.join(root, "sessions", "org", "acct");
  const proj = path.join(root, "projects", "p");
  fs.mkdirSync(acct, { recursive: true }); fs.mkdirSync(proj, { recursive: true });
  fs.writeFileSync(path.join(acct, "local_1.json"), JSON.stringify({ cliSessionId: SID, cwd, title: "Task", lastActivityAt: 5 }));
  fs.writeFileSync(path.join(acct, "local_2.json"), JSON.stringify({ cliSessionId: "11111111-1111-1111-1111-111111111111", cwd, isArchived: true }));
  const t = path.join(proj, `${SID}.jsonl`);
  fs.writeFileSync(t, "{}\n");
  const when = new Date(Date.now() - ageMs);
  fs.utimesSync(t, when, when);
  return createDesktopSessions({ sessionsDir: path.join(root, "sessions"), projectsDir: path.join(root, "projects") });
};

const fakeClaude = (dir) => {
  const f = path.join(dir, "claude");
  fs.writeFileSync(f, `#!/usr/bin/env node\nconsole.log(JSON.stringify({result:"args="+process.argv.slice(2).join("|")+" cwd="+process.cwd()}))\n`, { mode: 0o755 });
  return f;
};

const boot = async (desktopSessions, work) => {
  const d = tmp();
  const dir = path.join(d, "b");
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "T", mdns: false, run: { bin: fakeClaude(d), workdir: work, root: path.dirname(work) }, desktopSessions });
  const phone = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir), caps: ["run"] });
  await phone.wait((m) => m.type === "welcome");
  return { b, phone };
};

test("lists unarchived desktop sessions and hides archived ones", () => {
  const ds = layout("/tmp", 10 * 60_000);
  const list = ds.list();
  assert.equal(list.length, 1);
  assert.equal(list[0].id, SID);
});

test("busy only when the transcript changed in the last minutes; ids are validated", () => {
  assert.equal(layout("/tmp", 10 * 60_000).busy(SID), false);
  assert.equal(layout("/tmp", 1000).busy(SID), true);
  const ds = layout("/tmp", 10 * 60_000);
  assert.equal(ds.find("../etc/passwd"), null);
  assert.equal(ds.find("11111111-1111-1111-1111-111111111111"), null);
});

test("a run with a session resumes it in the session's own folder", async () => {
  const work = tmp();
  const cwd = fs.realpathSync(work);
  const { b, phone } = await boot(layout(cwd, 10 * 60_000), work);
  await phone.wait((m) => m.type === "desktopSessions" && m.list.length === 1);
  phone.send({ type: "run", id: "t1", prompt: "go on", sessionId: SID });
  const done = await phone.wait((m) => m.type === "runState" && m.state === "done");
  assert.match(done.text, new RegExp(`--resume\\|${SID}`));
  assert.ok(done.text.endsWith(`cwd=${cwd}`));
  phone.close(); await b.close();
});

test("a busy or unknown session is refused", async () => {
  const work = tmp();
  const { b, phone } = await boot(layout(work, 1000), work);
  phone.send({ type: "run", id: "t1", prompt: "go on", sessionId: SID });
  const busy = await phone.wait((m) => m.type === "runState" && m.state === "error");
  assert.match(busy.text, /busy/);
  phone.send({ type: "run", id: "t2", prompt: "go on", sessionId: "22222222-2222-2222-2222-222222222222" });
  const unknown = await phone.wait((m) => m.type === "runState" && m.id === "t2" && m.state === "error");
  assert.match(unknown.text, /unknown session/);
  phone.close(); await b.close();
});

test("a session folder outside the projects root is refused", async () => {
  const work = tmp();
  const { b, phone } = await boot(layout("/usr", 10 * 60_000), work);
  phone.send({ type: "run", id: "t1", prompt: "go on", sessionId: SID });
  const out = await phone.wait((m) => m.type === "runState" && m.state === "error");
  assert.match(out.text, /outside/);
  phone.close(); await b.close();
});
