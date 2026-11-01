import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRunHistory } from "../lib/runHistory.mjs";

const SID = "f1db632f-51d8-4640-bfae-aa095f9b854b";

test("history keeps the last 20, newest first, file mode 600", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hist-"));
  const h = createRunHistory(dir);
  for (let i = 0; i < 25; i++) h.add({ id: `t${i}`, state: "done", sessionId: SID, project: "p", prompt: "x".repeat(500), text: "ok", at: i });
  const list = h.list();
  assert.equal(list.length, 20);
  assert.equal(list[0].id, "t24");
  assert.ok(list[0].prompt.length <= 200);
  assert.equal(fs.statSync(path.join(dir, "runs.json")).mode & 0o777, 0o600);
});

test("history rejects bad ids, states and session ids", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hist-"));
  const h = createRunHistory(dir);
  h.add({ id: "../x", state: "done" });
  h.add({ id: "ok", state: "running" });
  h.add({ id: "ok2", state: "done", sessionId: "nope" });
  const list = h.list();
  assert.equal(list.length, 1);
  assert.equal(list[0].sessionId ?? "", "");
});
