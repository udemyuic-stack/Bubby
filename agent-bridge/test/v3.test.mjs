import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { startBridge } from "../bridge.mjs";
import { fakePhone, pairByCode } from "../lib/fake-phone.mjs";
import { loadToken } from "../lib/pairing.mjs";

const HOOK = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "hook.mjs");
const ALL = ["details", "answers", "diffs"];
const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/cb-v3-"); dirs.push(d); return path.join(d, "b"); };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const start = async (extra = {}) => {
  const dir = tmp();
  const logs = [];
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, log: (l) => logs.push(l), ...extra });
  return { b, dir, logs };
};
const connect = async (b, dir, caps) => {
  const p = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir), caps });
  p.welcome = await p.wait((m) => m.type === "welcome");
  return p;
};
const hook = (dir, payload, args = [], env = {}) => new Promise((resolve) => {
  const p = spawn(process.execPath, [HOOK, ...args], { env: { ...process.env, BUBBY_DIR: dir, ...env } });
  let out = "";
  p.stdout.on("data", (d) => (out += d));
  p.on("close", (code) => resolve({ code, out }));
  p.stdin.end(typeof payload === "string" ? payload : JSON.stringify(payload));
});
const lastSessions = (p) => p.msgs.filter((m) => m.type === "sessions").at(-1);

const ASK = {
  questions: [
    { question: "Which database?", header: "DB", multiSelect: false, options: [{ label: "Postgres", description: "relational" }, { label: "SQLite", description: "a file" }] },
    { question: "Extras?", header: "Extras", multiSelect: true, options: [{ label: "Tests" }, { label: "Docs" }, { label: "Lint" }] },
  ],
};
const askPayload = (input = ASK, session = "s1") => ({ hook_event_name: "PermissionRequest", session_id: session, tool_name: "AskUserQuestion", tool_input: input });

// ----- capabilities -----

test("caps: welcome offers what the phone asked for and the bridge allows", async () => {
  const { b, dir } = await start();
  let p = await connect(b, dir, [...ALL, "chat", 42]);
  assert.deepEqual(p.welcome.caps, ALL);
  p.close(); await p.closed;
  p = await connect(b, dir, undefined);
  assert.deepEqual(p.welcome.caps, [], "no caps asked => none offered");
  p.close(); await p.closed;
  await b.close();
  const off = await start({ caps: { answers: false, diffs: false } });
  p = await connect(off.b, off.dir, ALL);
  assert.deepEqual(p.welcome.caps, ["details"]);
  p.close();
  await off.b.close();
});

test("caps on the pair connection: paired carries the offered caps", async () => {
  const { b } = await start();
  const { phone, reply } = await pairByCode({ host: b.host, port: b.port, code: b.pairingCode(), caps: ["diffs"] });
  assert.deepEqual(reply.caps, ["diffs"]);
  phone.close();
  await b.close();
});

// ----- questions -----

test("question: offered to a phone with answers, picks go back as updatedInput.answers", async () => {
  const { b, dir, logs } = await start();
  const p = await connect(b, dir, ALL);
  const h = hook(dir, askPayload());
  const q = await p.wait((m) => m.type === "question");
  assert.equal(q.pillId, "integration_claude");
  assert.match(q.fingerprint, /^[0-9a-f]{64}$/);
  assert.ok(Math.abs(q.createdAt - Date.now()) < 5000);
  assert.deepEqual(q.questions, [
    { question: "Which database?", multiSelect: false, options: [{ label: "Postgres", description: "relational" }, { label: "SQLite", description: "a file" }] },
    { question: "Extras?", multiSelect: true, options: [{ label: "Tests", description: "" }, { label: "Docs", description: "" }, { label: "Lint", description: "" }] },
  ]);
  assert.equal(lastSessions(p).sessions[0].state, "question");
  // anything that does not match exactly changes nothing
  p.answer(q.fingerprint, [["Postgres", "SQLite"], ["Tests"]]); // two for a single choice
  p.answer(q.fingerprint, [["postgres"], ["Tests"]]); // case-sensitive
  p.answer(q.fingerprint, [["Postgres"], []]); // none for a multiple choice
  p.answer(q.fingerprint, [["Postgres"], ["Tests", "Tests"]]); // duplicates
  p.answer(q.fingerprint, [["Postgres"]]); // one list short
  p.answer("0".repeat(64), [["Postgres"], ["Tests"]]); // wrong fingerprint
  p.send({ type: "decision", fingerprint: q.fingerprint, decision: "allow" }); // a decision is not an answer
  p.send({ type: "ping" });
  await p.wait((m) => m.type === "pong");
  await new Promise((r) => setTimeout(r, 100));
  assert.ok(!p.msgs.some((m) => m.type === "approvalResolved"));
  p.answer(q.fingerprint, [["SQLite"], ["Docs", "Tests"]]);
  const { code, out } = await h;
  assert.equal(code, 0);
  const o = JSON.parse(out);
  assert.equal(o.hookSpecificOutput.hookEventName, "PermissionRequest");
  assert.equal(o.hookSpecificOutput.decision.behavior, "allow");
  assert.deepEqual(o.hookSpecificOutput.decision.updatedInput, { ...ASK, answers: { "Which database?": "SQLite", "Extras?": ["Docs", "Tests"] } });
  await p.wait((m) => m.type === "approvalResolved" && m.fingerprint === q.fingerprint);
  // applied once
  p.answer(q.fingerprint, [["Postgres"], ["Tests"]]);
  assert.ok(!logs.join("\n").match(/SQLite|Docs|Postgres/), "nothing about the picks is logged");
  p.close();
  await b.close();
});

test("question: a phone without answers never hears of it; the hook falls back at once", async () => {
  const { b, dir } = await start();
  const p = await connect(b, dir, ["details"]);
  const t = Date.now();
  const { code, out } = await hook(dir, askPayload());
  assert.equal(code, 0);
  assert.equal(out, "");
  assert.ok(Date.now() - t < 3000);
  assert.ok(!p.msgs.some((m) => m.type === "question" || m.type === "approval"));
  p.answer("x", [["a"]]); // ignored without the capability
  p.close();
  await b.close();
});

test("question: outside the limits it stays on the computer", async () => {
  const { b, dir } = await start();
  const p = await connect(b, dir, ALL);
  const opt = (n) => Array.from({ length: n }, (_, i) => ({ label: `L${i}` }));
  const bad = [
    { questions: Array.from({ length: 5 }, (_, i) => ({ question: `Q${i}?`, options: opt(2) })) }, // 5 questions
    { questions: [{ question: "Q?", options: opt(9) }] }, // 9 options
    { questions: [{ question: "x".repeat(501), options: opt(2) }] },
    { questions: [{ question: "Q?", options: [{ label: "y".repeat(121) }] }] },
    { questions: [{ question: "Q?", options: [{ label: "a", description: "d".repeat(301) }] }] },
    { questions: [{ question: "Q?", options: [{ label: "a" }, { label: "a" }] }] }, // duplicate labels
    { questions: [{ question: "Q?", options: [] }] },
    { questions: [] },
  ];
  for (const input of bad) {
    const { out } = await hook(dir, askPayload(input));
    assert.equal(out, "");
  }
  assert.ok(!p.msgs.some((m) => m.type === "question"));
  p.close();
  await b.close();
});

test("question: timeout and the phone leaving resolve it; hook prints nothing", async () => {
  const { b, dir } = await start({ approvalTimeoutMs: 200 });
  const p = await connect(b, dir, ALL);
  const h = hook(dir, askPayload());
  const q = await p.wait((m) => m.type === "question");
  assert.equal((await h).out, "");
  await p.wait((m) => m.type === "approvalResolved" && m.fingerprint === q.fingerprint);
  p.answer(q.fingerprint, [["SQLite"], ["Docs"]]); // late: nothing
  const h2 = hook(dir, askPayload());
  await p.wait((m) => m.type === "question" && m.fingerprint !== q.fingerprint);
  p.close();
  assert.equal((await h2).out, "");
  await b.close();
});

// ----- details -----

test("details: project folder name, last steps, final line; never paths, prompts or output", async () => {
  const { b, dir } = await start();
  const p = await connect(b, dir, ALL);
  const cwd = "/Users/me/secret/my-project";
  await hook(dir, { hook_event_name: "UserPromptSubmit", session_id: "s", cwd, prompt: "SECRET PROMPT" });
  for (let i = 0; i < 25; i++) await hook(dir, { hook_event_name: "PreToolUse", session_id: "s", cwd, tool_name: "Bash", tool_input: { command: `echo ${i}` } });
  await hook(dir, { hook_event_name: "PreToolUse", session_id: "s", cwd, tool_name: "Edit", tool_input: { file_path: "/Users/me/secret/my-project/src/app.ts", old_string: "a", new_string: "b" } });
  await hook(dir, { hook_event_name: "PostToolUse", session_id: "s", cwd, tool_name: "Bash", tool_input: { command: "ls" }, tool_response: { stdout: "SECRET OUTPUT" } });
  await hook(dir, { hook_event_name: "Stop", session_id: "s", cwd, last_assistant_message: "\n## **Done**: the build is green.\nSECOND PARAGRAPH LINE\n\nmore" });
  const s = (await p.wait((m) => m.type === "sessions" && m.sessions[0]?.finalLine)).sessions[0];
  assert.equal(s.project, "my-project");
  assert.equal(s.steps.length, 20);
  assert.equal(s.steps[0], "echo 6", "oldest first, last 20");
  assert.equal(s.steps.at(-1), "app.ts", "a file change is its file name");
  assert.equal(s.finalLine, "Done: the build is green. SECOND PARAGRAPH LINE");
  const all = JSON.stringify(p.msgs);
  for (const secret of ["SECRET PROMPT", "SECRET OUTPUT", "/Users/me", "more"]) assert.ok(!all.includes(secret), secret);
  // a phone without details gets the v1 fields only
  p.close(); await p.closed;
  const v1 = await connect(b, dir, []);
  const plain = (await v1.wait((m) => m.type === "sessions")).sessions[0];
  assert.deepEqual(Object.keys(plain).sort(), ["agent", "pillId", "state", "statusText", "stepCount", "stepIndex", "updatedAt"]);
  v1.close();
  await b.close();
});

// ----- diffs -----

test("diffs: file list from Edit/Write, getDiff lines, gone, and no diffs without the capability", async () => {
  const { b, dir } = await start();
  const p = await connect(b, dir, ALL);
  const post = (tool, tool_input) => hook(dir, { hook_event_name: "PostToolUse", session_id: "s", tool_name: tool, tool_input });
  await hook(dir, { hook_event_name: "SessionStart", session_id: "s" });
  await post("Edit", { file_path: "/a/b/main.js", old_string: "one\ntwo\nthree\n", new_string: "one\nTWO\nthree\nfour\n" });
  await post("Write", { file_path: "/a/b/new.txt", content: "x\ny\n" });
  const s = (await p.wait((m) => m.type === "sessions" && m.sessions[0]?.files?.length === 2)).sessions[0];
  assert.deepEqual(s.files.map((f) => f.name), ["new.txt", "main.js"], "newest first");
  const [w, e] = s.files;
  assert.deepEqual(e, { id: e.id, name: "main.js", added: 2, removed: 1 });
  assert.deepEqual(w, { id: w.id, name: "new.txt", added: 2, removed: 0, isNew: true });
  const [d] = await p.getDiff(s.pillId, e.id);
  assert.deepEqual(d, {
    type: "diff", pillId: "integration_claude", fileId: e.id, name: "main.js", added: 2, removed: 1, tooLarge: false, gone: false,
    truncated: false, part: 0, parts: 1, lines: [["@", "@@ 1"], [" ", "one"], ["-", "two"], ["+", "TWO"], [" ", "three"], ["+", "four"]],
  });
  // the same file again: counts add up, same id
  await post("Edit", { file_path: "/a/b/main.js", old_string: "four", new_string: "4" });
  const s2 = (await p.wait((m) => m.type === "sessions" && m.sessions[0]?.files?.[0]?.name === "main.js" && m.sessions[0].files[0].added === 3)).sessions[0];
  assert.equal(s2.files[0].id, e.id);
  assert.equal(s2.files.length, 2);
  // unknown file => gone
  const [g] = await p.getDiff(s.pillId, 9999);
  assert.equal(g.gone, true);
  assert.deepEqual(g.lines, []);
  // session ends => its diffs are forgotten
  await hook(dir, { hook_event_name: "SessionEnd", session_id: "s" });
  await p.wait((m) => m.type === "sessions" && m.sessions.length === 0);
  assert.equal((await p.getDiff(s.pillId, e.id))[0].gone, true);
  assert.ok(!JSON.stringify(p.msgs).includes("/a/b"), "file names only");
  p.close(); await p.closed;

  const v1 = await connect(b, dir, ["details"]);
  await hook(dir, { hook_event_name: "SessionStart", session_id: "t" });
  await hook(dir, { hook_event_name: "PostToolUse", session_id: "t", tool_name: "Write", tool_input: { file_path: "/z.txt", content: "z\n" } });
  await v1.wait((m) => m.type === "sessions" && m.sessions.length === 1);
  assert.ok(!v1.msgs.some((m) => m.type === "sessions" && m.sessions.some((x) => x.files)));
  v1.send({ type: "getDiff", pillId: "integration_claude", fileId: 1 });
  v1.send({ type: "ping" });
  await v1.wait((m) => m.type === "pong");
  assert.ok(!v1.msgs.some((m) => m.type === "diff"));
  v1.close();
  await b.close();
});

test("diffs: 200 lines at most, parts of 100, each line cut at 400; tooLarge has counts only", async () => {
  const { b, dir } = await start();
  const p = await connect(b, dir, ["diffs"]);
  await hook(dir, { hook_event_name: "SessionStart", session_id: "s" });
  const content = Array.from({ length: 300 }, (_, i) => (i === 0 ? "w".repeat(1000) : `line ${i}`)).join("\n") + "\n";
  await hook(dir, { hook_event_name: "PostToolUse", session_id: "s", tool_name: "Write", tool_input: { file_path: "/big.txt", content } });
  const f = (await p.wait((m) => m.type === "sessions" && m.sessions[0]?.files)).sessions[0].files[0];
  assert.equal(f.added, 300);
  const parts = await p.getDiff("integration_claude", f.id);
  assert.equal(parts.length, 2);
  assert.deepEqual(parts.map((x) => [x.part, x.parts, x.lines.length, x.truncated]), [[0, 2, 100, true], [1, 2, 100, true]]);
  assert.equal(parts[0].lines[1][1].length, 400);
  for (const x of parts) assert.ok(Buffer.byteLength(JSON.stringify(x)) < 64 * 1024);
  const huge = "q\n".repeat(150_000);
  await hook(dir, { hook_event_name: "PostToolUse", session_id: "s", tool_name: "Write", tool_input: { file_path: "/huge.txt", content: huge } });
  const h = (await p.wait((m) => m.type === "sessions" && m.sessions[0]?.files?.[0]?.name === "huge.txt")).sessions[0].files[0];
  assert.deepEqual(h, { id: h.id, name: "huge.txt", added: 150000, removed: 0, tooLarge: true, isNew: true });
  const [hd] = await p.getDiff("integration_claude", h.id);
  assert.equal(hd.tooLarge, true);
  assert.deepEqual(hd.lines, []);
  p.close();
  await b.close();
});

test("diffs: at most 20 getDiff per 10 seconds", async () => {
  const { b, dir } = await start();
  const p = await connect(b, dir, ["diffs"]);
  for (let i = 0; i < 25; i++) p.send({ type: "getDiff", pillId: "integration_claude", fileId: i });
  p.send({ type: "ping" });
  await p.wait((m) => m.type === "pong");
  await new Promise((r) => setTimeout(r, 100));
  assert.equal(p.msgs.filter((m) => m.type === "diff").length, 20);
  p.close();
  await b.close();
});

// ----- more agents -----

test("agents: --agent and bubby_agent get their own pill; invalid names are Claude Code", async () => {
  const { b, dir } = await start();
  const p = await connect(b, dir, ALL);
  const r = await hook(dir, { hook_event_name: "BeforeTool", session_id: "g1", tool_name: "run_shell_command", cwd: "/x/gem-proj" }, ["--agent", "gemini", "PreToolUse"]);
  assert.equal(r.out, "{}\n", "Gemini reads {} as no opinion");
  const s = (await p.wait((m) => m.type === "sessions" && m.sessions.some((x) => x.agent === "gemini"))).sessions.find((x) => x.agent === "gemini");
  assert.equal(s.pillId, "agent_gemini");
  assert.equal(s.state, "working");
  assert.equal(s.project, "gem-proj");
  // Copilot: camelCase event from argv, toolName/toolArgs fields, no session id
  const c = await hook(dir, { timestamp: 1, cwd: "/w", toolName: "bash", toolArgs: JSON.stringify({ command: "ls" }) }, ["--agent", "copilot", "preToolUse"]);
  assert.equal(c.out, "{}\n");
  await p.wait((m) => m.type === "sessions" && m.sessions.some((x) => x.agent === "copilot"));
  // the payload field works without the flag
  await hook(dir, { hook_event_name: "UserPromptSubmit", session_id: "o1", bubby_agent: "my-tool" });
  await p.wait((m) => m.type === "sessions" && m.sessions.some((x) => x.agent === "my-tool" && x.state === "thinking"));
  await hook(dir, { hook_event_name: "UserPromptSubmit", session_id: "c1", bubby_agent: "Bad Name!" });
  await p.wait((m) => m.type === "sessions" && m.sessions.some((x) => x.agent === "Claude Code"));
  // another agent's permission request is never sent to the phone, and nothing is printed
  const t = Date.now();
  const pr = await hook(dir, { hook_event_name: "PermissionRequest", session_id: "o1", bubby_agent: "my-tool", tool_name: "Bash", tool_input: { command: "rm -rf /" } });
  assert.equal(pr.out, "");
  assert.ok(Date.now() - t < 3000);
  assert.ok(!p.msgs.some((m) => m.type === "approval"));
  p.close();
  await b.close();
});

test("agents: with the bridge down a Gemini/Copilot hook still prints {} and exits 0", async () => {
  const dir = tmp();
  fs.mkdirSync(dir, { recursive: true });
  assert.deepEqual(await hook(dir, "garbage", ["--agent", "gemini", "PreToolUse"]), { code: 0, out: "{}\n" });
  assert.deepEqual(await hook(dir, { toolName: "x" }, ["--agent", "copilot", "preToolUse"]), { code: 0, out: "{}\n" });
  assert.deepEqual(await hook(dir, { hook_event_name: "Stop", session_id: "x" }, ["--agent", "other"]), { code: 0, out: "" });
});
