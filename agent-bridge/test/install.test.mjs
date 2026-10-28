import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { mergeSettings, EVENTS, HOOK_PATH } from "../install-hooks.mjs";

const SCRIPT = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "install-hooks.mjs");
const run = (home, ...args) => spawnSync(process.execPath, [SCRIPT, ...args], { env: { ...process.env, HOME: home, CLAUDE_SETTINGS: "" }, encoding: "utf8" });

test("preview prints diff and changes nothing; --yes backs up and merges; --uninstall restores", () => {
  const home = fs.mkdtempSync("/tmp/cb-home-");
  const file = path.join(home, ".claude", "settings.json");
  fs.mkdirSync(path.dirname(file));
  const original = { model: "opus", hooks: { Stop: [{ hooks: [{ type: "command", command: "echo mine" }] }] } };
  fs.writeFileSync(file, JSON.stringify(original, null, 2) + "\n");
  const before = fs.readFileSync(file, "utf8");

  const preview = run(home);
  assert.equal(preview.status, 0);
  assert.match(preview.stdout, /^\+.*hook\.mjs/m);
  assert.equal(fs.readFileSync(file, "utf8"), before);

  const applied = run(home, "--yes");
  assert.equal(applied.status, 0);
  const backups = fs.readdirSync(path.dirname(file)).filter((f) => f.includes(".bak-"));
  assert.equal(backups.length, 1);
  assert.equal(fs.readFileSync(path.join(path.dirname(file), backups[0]), "utf8"), before);
  const now = JSON.parse(fs.readFileSync(file, "utf8"));
  assert.equal(now.model, "opus");
  assert.equal(now.hooks.Stop[0].hooks[0].command, "echo mine");
  for (const ev of EVENTS) assert.ok(now.hooks[ev].some((g) => g.hooks.some((h) => h.command.includes(HOOK_PATH))));

  assert.match(run(home, "--yes").stdout, /Изменений нет/);

  assert.equal(run(home, "--uninstall", "--yes").status, 0);
  assert.deepEqual(JSON.parse(fs.readFileSync(file, "utf8")), original);
  fs.rmSync(home, { recursive: true, force: true });
});

test("invalid JSON is never touched", () => {
  const home = fs.mkdtempSync("/tmp/cb-home-");
  const file = path.join(home, ".claude", "settings.json");
  fs.mkdirSync(path.dirname(file));
  fs.writeFileSync(file, "{ nope");
  const r = run(home, "--yes");
  assert.notEqual(r.status, 0);
  assert.equal(fs.readFileSync(file, "utf8"), "{ nope");
  fs.rmSync(home, { recursive: true, force: true });
});

test("mergeSettings is idempotent", () => {
  const once = mergeSettings({});
  assert.deepEqual(mergeSettings(once), once);
});

test("statusLine relay: the user's own line is saved and restored; padding stays", () => {
  const home = fs.mkdtempSync("/tmp/cb-home-");
  const file = path.join(home, ".claude", "settings.json");
  fs.mkdirSync(path.dirname(file));
  const mine = { type: "command", command: "~/bin/my-line.sh", padding: 2 };
  const original = { statusLine: mine };
  fs.writeFileSync(file, JSON.stringify(original, null, 2) + "\n");
  const prev = path.join(home, ".bubby", "statusline-previous.json");
  assert.match(run(home).stdout, /--statusline/);
  assert.ok(!fs.existsSync(prev), "preview saves nothing");
  assert.equal(run(home, "--yes").status, 0);
  const now = JSON.parse(fs.readFileSync(file, "utf8"));
  assert.match(now.statusLine.command, /hook\.mjs" --statusline$/);
  assert.equal(now.statusLine.padding, 2);
  assert.deepEqual(JSON.parse(fs.readFileSync(prev, "utf8")), mine);
  assert.equal(fs.statSync(prev).mode & 0o777, 0o600);
  assert.match(run(home, "--yes").stdout, /Изменений нет/);
  assert.equal(run(home, "--uninstall", "--yes").status, 0);
  assert.deepEqual(JSON.parse(fs.readFileSync(file, "utf8")), original);
  assert.ok(!fs.existsSync(prev));
  // --no-statusline leaves it alone
  run(home, "--yes", "--no-statusline");
  assert.deepEqual(JSON.parse(fs.readFileSync(file, "utf8")).statusLine, mine);
  fs.rmSync(home, { recursive: true, force: true });
});

test("a statusLine that is not an object is left alone", () => {
  const home = fs.mkdtempSync("/tmp/cb-home-");
  const file = path.join(home, ".claude", "settings.json");
  fs.mkdirSync(path.dirname(file));
  fs.writeFileSync(file, JSON.stringify({ statusLine: "echo hi" }));
  assert.equal(run(home, "--yes").status, 0);
  const now = JSON.parse(fs.readFileSync(file, "utf8"));
  assert.equal(now.statusLine, "echo hi");
  assert.ok(now.hooks.Stop);
  fs.rmSync(home, { recursive: true, force: true });
});

test("Gemini CLI hooks: merged into ~/.gemini/settings.json with a backup, removed cleanly", () => {
  const home = fs.mkdtempSync("/tmp/cb-home-");
  const file = path.join(home, ".gemini", "settings.json");
  fs.mkdirSync(path.dirname(file));
  const original = { theme: "dark", hooks: { BeforeTool: [{ matcher: "write_file", hooks: [{ type: "command", command: "my-check" }] }] } };
  fs.writeFileSync(file, JSON.stringify(original, null, 2) + "\n");
  const preview = run(home, "--agent", "gemini");
  assert.match(preview.stdout, /--agent gemini PreToolUse/);
  assert.deepEqual(JSON.parse(fs.readFileSync(file, "utf8")), original);
  assert.equal(run(home, "--agent", "gemini", "--yes").status, 0);
  assert.equal(fs.readdirSync(path.dirname(file)).filter((f) => f.includes(".bak-")).length, 1);
  const now = JSON.parse(fs.readFileSync(file, "utf8"));
  assert.equal(now.theme, "dark");
  assert.equal(now.hooks.BeforeTool[0].hooks[0].command, "my-check");
  for (const ev of ["SessionStart", "SessionEnd", "BeforeTool", "AfterTool", "BeforeAgent", "AfterAgent"]) {
    const ours = now.hooks[ev].flatMap((g) => g.hooks).filter((h) => h.command.includes(HOOK_PATH));
    assert.equal(ours.length, 1, ev);
    assert.equal(typeof ours[0].timeout, "number");
  }
  assert.match(run(home, "--agent", "gemini", "--yes").stdout, /Изменений нет/);
  assert.equal(run(home, "--agent", "gemini", "--uninstall", "--yes").status, 0);
  assert.deepEqual(JSON.parse(fs.readFileSync(file, "utf8")), original);
  fs.rmSync(home, { recursive: true, force: true });
});

test("Copilot CLI hooks: our own file, display-only events, deleted on uninstall", () => {
  const home = fs.mkdtempSync("/tmp/cb-home-");
  const file = path.join(home, ".copilot", "hooks", "bubby.json");
  assert.equal(run(home, "--agent", "copilot", "--yes").status, 0);
  const now = JSON.parse(fs.readFileSync(file, "utf8"));
  assert.equal(now.version, 1);
  assert.deepEqual(Object.keys(now.hooks), ["sessionStart", "userPromptSubmitted", "preToolUse", "postToolUse", "agentStop", "sessionEnd"]);
  assert.match(now.hooks.preToolUse[0].bash, /--agent copilot preToolUse$/);
  assert.equal(now.hooks.preToolUse[0].type, "command");
  assert.ok(!now.hooks.permissionRequest, "nothing waits on Bubby");
  assert.match(run(home, "--agent", "copilot", "--yes").stdout, /Изменений нет/);
  assert.equal(run(home, "--agent", "copilot", "--uninstall", "--yes").status, 0);
  assert.ok(!fs.existsSync(file));
  assert.equal(fs.readdirSync(path.dirname(file)).filter((f) => f.includes(".bak-")).length, 1);
  assert.notEqual(run(home, "--agent", "codex").status, 0, "unknown agents are refused");
  fs.rmSync(home, { recursive: true, force: true });
});
