import { test } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { fingerprint, truncateCommand, describeTool } from "../lib/common.mjs";
import { tokenMatches } from "../lib/pairing.mjs";

test("fingerprint is sha256 hex over U+001F-joined fields", () => {
  const expected = crypto.createHash("sha256").update("p\u001fs\u001fBash\u001fls\u001fk1").digest("hex");
  assert.equal(fingerprint("p", "s", "Bash", "ls", "k1"), expected);
  assert.notEqual(fingerprint("p", "s", "Bash", "ls", "k2"), expected);
  assert.notEqual(fingerprint("p", "s", "Bash", "l", "sk1"), fingerprint("p", "s", "Bash", "ls", "k1"));
});

test("truncation at 500 characters", () => {
  assert.deepEqual(truncateCommand("a".repeat(500)), { command: "a".repeat(500), truncated: false });
  const t = truncateCommand("b".repeat(501));
  assert.equal(t.command.length, 500);
  assert.equal(t.truncated, true);
  const emoji = truncateCommand("😀".repeat(501));
  assert.equal(Array.from(emoji.command).length, 500);
  assert.equal(emoji.truncated, true);
});

test("describeTool never leaks paths or contents", () => {
  assert.equal(describeTool("Bash", { command: "ls -la" }), "ls -la");
  assert.equal(describeTool("Edit", { file_path: "/a/b/c.ts", new_string: "x" }), "c.ts");
  assert.equal(describeTool("mcp__x__y", { secret: "z" }), "");
});

test("tokenMatches is exact and tolerates length mismatch", () => {
  assert.ok(tokenMatches("abc", "abc"));
  assert.ok(!tokenMatches("abd", "abc"));
  assert.ok(!tokenMatches("ab", "abc"));
  assert.ok(!tokenMatches(undefined, "abc"));
});
