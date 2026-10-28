import { test } from "node:test";
import assert from "node:assert/strict";
import { diffText, buildFileDiff } from "../lib/filediff.mjs";
import { offerable, answersFromPicks, answersFit, answerOutput } from "../lib/questions.mjs";
import { createSessions, sessionsMessage, finalLineOf, stepOf, SESSIONS_BUDGET } from "../lib/sessions.mjs";
import { normalizePayload, canonicalEvent, validAgent } from "../lib/agents.mjs";
import { buildEnvelope, replyOutput, editInput } from "../hook.mjs";

test("diffText: hunks with context and @@ headers", () => {
  const old = Array.from({ length: 20 }, (_, i) => `l${i}`).join("\n");
  const nw = old.replace("l2\n", "L2\n").replace("l17\n", "");
  const d = diffText(old, nw);
  assert.equal(d.added, 1);
  assert.equal(d.removed, 2);
  const heads = d.lines.filter((l) => l[0] === "@");
  assert.equal(heads.length, 2);
  assert.deepEqual(heads[0], ["@", "@@ 1"]);
  assert.equal(d.lines.filter((l) => l[0] === " ").length, 2 + 3 + 3 + 2);
  assert.deepEqual(diffText("same\n", "same\n"), { added: 0, removed: 0, lines: [] });
  assert.equal(diffText("a\n".repeat(3000), "b\n".repeat(3000)).tooLarge, true);
});

test("buildFileDiff: Edit, MultiEdit, Write, no-ops and hook-side tooLarge", () => {
  assert.equal(buildFileDiff("Edit", { file_path: "/p/a.js", old_string: "x", new_string: "x" }), null);
  assert.equal(buildFileDiff("Read", { file_path: "/p/a.js" }), null);
  const m = buildFileDiff("MultiEdit", { file_path: "C:\\w\\b.py", edits: [{ old_string: "a", new_string: "b" }, { old_string: "c", new_string: "" }] });
  assert.equal(m.name, "b.py");
  assert.deepEqual([m.added, m.removed], [1, 2]);
  const w = buildFileDiff("Write", { file_path: "/n.txt", content: "1\n2\n" });
  assert.deepEqual([w.added, w.isNew, w.lines[0]], [2, true, ["@", "@@ 1"]]);
  assert.deepEqual(buildFileDiff("Write", { file_path: "/n.txt", tooLarge: true, added: 9, removed: 0 }), { name: "n.txt", added: 9, removed: 0, tooLarge: true, isNew: true, lines: [] });
  assert.equal(buildFileDiff("Edit", { file_path: "/" + "x".repeat(200) + ".js", old_string: "a", new_string: "b" }).name.length, 80);
});

test("hook editInput: big edits travel as counts only", () => {
  assert.deepEqual(editInput("Edit", { file_path: "/a", old_string: "o", new_string: "n", replace_all: true }), { file_path: "/a", old_string: "o", new_string: "n" });
  const big = editInput("Write", { file_path: "/a", content: "z\n".repeat(400_000) });
  assert.deepEqual(big, { file_path: "/a", tooLarge: true, added: 400000, removed: 0 });
  assert.equal(editInput("Bash", { command: "x" }), null);
});

test("questions: offerable keeps exact text; picks become Claude Code's answers map", () => {
  const qs = offerable([{ question: "Q?", options: [{ label: "A", description: "a" }, { label: "B" }] }, { question: "M?", multiSelect: true, options: [{ label: "x" }, { label: "y" }] }]);
  assert.deepEqual(answersFromPicks(qs, [["B"], ["y", "x"]]), { "Q?": "B", "M?": ["y", "x"] });
  assert.equal(answersFromPicks(qs, [["B"], ["z"]]), null);
  assert.equal(answersFromPicks(qs, "B"), null);
  assert.equal(offerable([{ question: "Q?", options: [{ label: "A" }] }, { question: "Q?", options: [{ label: "A" }] }]), null, "same question twice: the answers map could not tell them apart");
  assert.equal(offerable([{ question: "Q?", multiSelect: "yes", options: [{ label: "A" }] }]), null);
  const input = { questions: [{ question: "Q?", options: [{ label: "A" }] }], extra: 1 };
  assert.ok(answersFit(input, { "Q?": "A" }));
  assert.ok(!answersFit(input, { "Q?": "A", "R?": "A" }));
  assert.ok(!answersFit(input, { "Q?": ["A"] }));
  assert.deepEqual(answerOutput(input, { "Q?": "A" }).hookSpecificOutput.decision.updatedInput, { ...input, answers: { "Q?": "A" } });
  assert.equal(answerOutput(input, { "Q?": "nope" }), null);
});

test("hook replies: nothing allows anything without a decision; answers only for AskUserQuestion", () => {
  const ask = { hook_event_name: "PermissionRequest", session_id: "s", tool_name: "AskUserQuestion", tool_input: { questions: [{ question: "Q?", options: [{ label: "A" }] }] } };
  const bash = { hook_event_name: "PermissionRequest", session_id: "s", tool_name: "Bash", tool_input: { command: "ls" } };
  const env = (p) => buildEnvelope(p);
  for (const reply of [null, {}, { decision: "maybe" }, { decision: "allow" }, { answers: { "Q?": "B" } }, "allow"]) assert.equal(replyOutput(ask, env(ask), reply), null);
  assert.ok(replyOutput(ask, env(ask), { answers: { "Q?": "A" } }));
  assert.equal(replyOutput(bash, env(bash), { answers: { "Q?": "A" } }), null);
  assert.equal(replyOutput(bash, env(bash), { decision: "allow" }).hookSpecificOutput.decision.behavior, "allow");
  const other = { ...bash, bubby_agent: "gemini" };
  assert.equal(replyOutput(other, env(other), { decision: "allow" }), null, "other agents: display only");
  assert.equal(env({ ...bash, bubby_agent: "UPPER" }).agent, undefined);
});

test("agents: event and field mapping", () => {
  assert.equal(canonicalEvent("AfterAgent"), "Stop");
  assert.equal(canonicalEvent("userPromptSubmitted"), "UserPromptSubmit");
  assert.equal(canonicalEvent("Custom"), "Custom");
  assert.equal(validAgent("a".repeat(25)), null);
  const n = normalizePayload({ toolName: "bash", toolArgs: '{"command":"ls"}', workdir: "/w/proj" }, { agent: "copilot", event: "preToolUse", env: {} });
  assert.deepEqual([n.hook_event_name, n.tool_name, n.tool_input.command, n.session_id, n.cwd, n.bubby_agent], ["PreToolUse", "bash", "ls", "copilot-session", "/w/proj", "copilot"]);
  const g = normalizePayload({ hook_event_name: "BeforeTool", toolCall: { name: "x", args: { a: 1 } } }, { agent: "gemini", env: { GEMINI_SESSION_ID: "gs" } });
  assert.deepEqual([g.hook_event_name, g.tool_name, g.tool_input, g.session_id], ["PreToolUse", "x", { a: 1 }, "gs"]);
});

test("sessions: final line, steps, and the 56 KiB budget drops the oldest steps first", () => {
  assert.equal(finalLineOf("# Title\n\n- **Fixed** the `bug`\n"), "Title");
  assert.equal(finalLineOf("\n\n**Fixed** the `bug` in a.js\nand more\n\nnext"), "Fixed the bug in a.js and more");
  assert.equal(finalLineOf("x".repeat(500)).length, 200);
  assert.equal(stepOf("Bash", "npm test\n&& echo"), "npm test && echo");
  assert.equal(stepOf("Read", "a.ts"), "Read a.ts");
  assert.equal(stepOf("Grep", ""), "Grep");
  const s = createSessions();
  for (let a = 0; a < 20; a++) {
    const agent = `agent-${a}`;
    s.touch(agent, "s", { state: "working" });
    for (let i = 0; i < 20; i++) s.step(agent, `${i}:` + "é".repeat(190));
  }
  const list = s.list(new Set(["details"]));
  assert.ok(Buffer.byteLength(JSON.stringify({ type: "sessions", sessions: list })) > SESSIONS_BUDGET);
  const msg = sessionsMessage(list);
  assert.ok(Buffer.byteLength(JSON.stringify(msg)) + 1 <= SESSIONS_BUDGET);
  assert.equal(msg.sessions.length, 20);
  for (const e of msg.sessions) if (e.steps) assert.ok(e.steps.at(-1).startsWith("19:"), "the newest steps are kept");
  assert.equal(list[0].steps.length, 20, "the input list is not changed");
});
