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
