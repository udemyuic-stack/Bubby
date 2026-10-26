#!/usr/bin/env node
// Adds (or with --uninstall removes) Bubby's hook entries in ~/.claude/settings.json.
// Never overwrites the file blindly: prints the diff, applies only with --yes after a dated backup.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { unifiedDiff } from "./lib/diff.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const HOOK_PATH = path.join(HERE, "hook.mjs");
// Claude Code runs "command" through a shell, so both paths are quoted.
export const COMMAND = `"${process.execPath}" "${HOOK_PATH}"`;
export const EVENTS = ["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "PermissionRequest", "Notification", "Stop", "StopFailure", "SessionEnd"];
const TIMEOUT = { PermissionRequest: 120 }; // seconds; hook.mjs itself gives up after 110 s

const isOurs = (h) => h && typeof h.command === "string" && h.command.includes(HOOK_PATH);

export function mergeSettings(settings, uninstall = false) {
  const out = structuredClone(settings);
  if (out.hooks !== undefined && (typeof out.hooks !== "object" || Array.isArray(out.hooks) || !out.hooks)) throw new Error('"hooks" в settings.json имеет неожиданный вид');
  if (uninstall) {
    for (const ev of Object.keys(out.hooks ?? {})) {
      if (!Array.isArray(out.hooks[ev])) continue;
      out.hooks[ev] = out.hooks[ev]
        .map((g) => (Array.isArray(g?.hooks) ? { ...g, hooks: g.hooks.filter((h) => !isOurs(h)) } : g))
        .filter((g) => !(Array.isArray(g?.hooks) && g.hooks.length === 0));
      if (out.hooks[ev].length === 0) delete out.hooks[ev];
    }
    if (out.hooks && Object.keys(out.hooks).length === 0) delete out.hooks;
    return out;
  }
  out.hooks ??= {};
  for (const ev of EVENTS) {
    const groups = (out.hooks[ev] ??= []);
    if (!Array.isArray(groups)) throw new Error(`hooks.${ev} имеет неожиданный вид`);
    if (groups.some((g) => Array.isArray(g?.hooks) && g.hooks.some(isOurs))) continue;
    groups.push({ hooks: [{ type: "command", command: COMMAND, timeout: TIMEOUT[ev] ?? 10 }] });
  }
  return out;
}

function main() {
  const args = process.argv.slice(2);
  const uninstall = args.includes("--uninstall");
  const settingsPath = process.env.CLAUDE_SETTINGS || path.join(os.homedir(), ".claude", "settings.json");
  let before = "{}\n";
  let existed = false;
  if (fs.existsSync(settingsPath)) { before = fs.readFileSync(settingsPath, "utf8"); existed = true; }
  let current;
  try { current = JSON.parse(before || "{}"); } catch { console.error(`${settingsPath} не является корректным JSON, ничего не меняю.`); process.exit(1); }
  const next = mergeSettings(current, uninstall);
  const after = JSON.stringify(next, null, 2) + "\n";
  const norm = JSON.stringify(current, null, 2) + "\n";
  const diff = unifiedDiff(norm, after, settingsPath, settingsPath + " (new)");
  if (!diff) { console.log("Изменений нет."); return; }
  console.log(diff + "\n");
  if (!args.includes("--yes")) { console.log("Это только предпросмотр. Чтобы применить, добавьте --yes."); return; }
  fs.mkdirSync(path.dirname(settingsPath), { recursive: true });
  if (existed) {
    const stamp = new Date().toISOString().replace(/[-:]/g, "").replace(/\..*/, "").replace("T", "-");
    let backup = `${settingsPath}.bak-${stamp}`;
    for (let n = 2; fs.existsSync(backup); n++) backup = `${settingsPath}.bak-${stamp}-${n}`;
    fs.copyFileSync(settingsPath, backup, fs.constants.COPYFILE_EXCL);
    console.log(`Резервная копия: ${backup}`);
  }
  const tmp = `${settingsPath}.tmp-${process.pid}`;
  fs.writeFileSync(tmp, after, { mode: existed ? fs.statSync(settingsPath).mode & 0o777 : 0o600 });
  fs.renameSync(tmp, settingsPath);
  console.log(uninstall ? "Хуки удалены." : "Хуки установлены.");
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) main();
