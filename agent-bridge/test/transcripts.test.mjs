import { test, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { startBridge } from "../bridge.mjs";
import { fakePhone } from "../lib/fake-phone.mjs";
import { loadToken } from "../lib/pairing.mjs";
import { readState } from "../lib/transcripts.mjs";

const dirs = [];
const tmp = () => { const d = fs.mkdtempSync("/tmp/cb-tx-"); dirs.push(d); return d; };
after(() => dirs.forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const line = (o) => JSON.stringify(o) + "\n";
const toolUse = (name, input, id = "t1") => line({ type: "assistant", cwd: "/home/me/shop", message: { stop_reason: "tool_use", content: [{ type: "tool_use", id, name, input }] } });

test("readState: tool, result, prompt, answer, age", () => {
  const tool = readState([toolUse("Edit", { file_path: "/a/b/App.kt" }).trim()], 1000);
  assert.equal(tool.state, "working");
  assert.equal(tool.statusText, "Using Edit");
  assert.equal(tool.tool.text, "App.kt");
  assert.equal(tool.cwd, "/home/me/shop");
  assert.equal(readState([toolUse("Bash", { command: "ls" }).trim()], 60_000).state, "idle");
  const res = line({ type: "user", cwd: "/x", message: { content: [{ type: "tool_result", tool_use_id: "t1" }] } }).trim();
  assert.equal(readState([toolUse("Bash", { command: "ls" }).trim(), res], 2000).state, "thinking");
  const done = line({ type: "assistant", message: { stop_reason: "end_turn", content: [{ type: "text", text: "all done" }] } }).trim();
  assert.equal(readState([done], 5000).state, "finished");
  assert.equal(readState([done], 120_000).state, "idle");
  // a sub-agent's lines and broken lines are ignored
  const side = line({ type: "assistant", isSidechain: true, message: { stop_reason: "tool_use", content: [{ type: "tool_use", id: "z", name: "Read", input: {} }] } }).trim();
  assert.equal(readState([done, side, "{broken"], 1000).state, "finished");
  assert.equal(readState([], 0), null);
});

test("the phone sees a session that never called a hook, and the hooks take over later", async () => {
  const root = tmp(), dir = path.join(tmp(), "b");
  fs.mkdirSync(path.join(root, "-home-me-shop"));
  const file = path.join(root, "-home-me-shop", "abc-123.jsonl");
  fs.writeFileSync(file, toolUse("Edit", { file_path: "/a/App.kt" }));
  const b = await startBridge({ dir, host: "127.0.0.1", port: 0, name: "Test", mdns: false, transcripts: root, transcriptsMs: 50 });
  try {
    const p = await fakePhone({ host: b.host, port: b.port, fp: b.fp, token: loadToken(dir), caps: ["details"] });
    await p.wait((m) => m.type === "welcome");
    const first = await p.wait((m) => m.type === "sessions" && m.sessions[0]?.statusText === "Using Edit");
    assert.equal(first.sessions[0].state, "working");
    assert.equal(first.sessions[0].project, "shop");
    assert.deepEqual(first.sessions[0].steps, ["App.kt"]);
    // the file grows: the next tool shows up
    fs.appendFileSync(file, toolUse("Bash", { command: "gradle test" }, "t2"));
    const second = await p.wait((m) => m.type === "sessions" && m.sessions[0]?.statusText === "Using Bash");
    assert.equal(second.sessions[0].steps.at(-1), "gradle test");
    // it ends: the session leaves the list
    fs.utimesSync(file, new Date(Date.now() - 10 * 60_000), new Date(Date.now() - 10 * 60_000));
    await p.wait((m) => m.type === "sessions" && m.sessions.length === 0);
    p.close?.();
  } finally { await b.close(); }
});
