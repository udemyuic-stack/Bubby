// Bubby's hook entries in ~/.claude/settings.json: merge, diff, backup and write.
// Shared by install-hooks.mjs and bridge.mjs (first-run setup). Never overwrites the file blindly.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { unifiedDiff } from "./diff.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const HOOK_PATH = path.join(HERE, "..", "hook.mjs");
// Claude Code runs "command" through a shell, so both paths are quoted.
export const COMMAND = `"${process.execPath}" "${HOOK_PATH}"`;
export const EVENTS = ["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "PermissionRequest", "Notification", "Stop", "StopFailure", "SessionEnd"];
const TIMEOUT = { PermissionRequest: 120 }; // seconds; hook.mjs itself gives up after 110 s

const isOurs = (h) => h && typeof h.command === "string" && h.command.includes(HOOK_PATH);

export const settingsPath = () => process.env.CLAUDE_SETTINGS || path.join(os.homedir(), ".claude", "settings.json");

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

// What would change. Throws (without touching anything) if the file is not valid JSON or has an odd shape.
// diff === "" means nothing to do.
export function planHooks({ uninstall = false, file = settingsPath() } = {}) {
  let before = "{}\n";
  let existed = false;
  if (fs.existsSync(file)) { before = fs.readFileSync(file, "utf8"); existed = true; }
  let current;
  try { current = JSON.parse(before || "{}"); } catch { throw new Error(`${file} не является корректным JSON, ничего не меняю.`); }
  if (!current || typeof current !== "object" || Array.isArray(current)) throw new Error(`${file} не является JSON-объектом, ничего не меняю.`);
  const next = mergeSettings(current, uninstall);
  const after = JSON.stringify(next, null, 2) + "\n";
  const norm = JSON.stringify(current, null, 2) + "\n";
  return { file, existed, after, uninstall, diff: unifiedDiff(norm, after, file, file + " (new)") };
}

// Writes the plan: dated backup first (settings.json.bak-YYYYMMDD-HHMMSS), then an atomic rename. Returns the backup path or null.
export function applyHooks(plan) {
  const { file, existed, after } = plan;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  let backup = null;
  if (existed) {
    const stamp = new Date().toISOString().replace(/[-:]/g, "").replace(/\..*/, "").replace("T", "-");
    backup = `${file}.bak-${stamp}`;
    for (let n = 2; fs.existsSync(backup); n++) backup = `${file}.bak-${stamp}-${n}`;
    fs.copyFileSync(file, backup, fs.constants.COPYFILE_EXCL);
  }
  const tmp = `${file}.tmp-${process.pid}`;
  fs.writeFileSync(tmp, after, { mode: existed ? fs.statSync(file).mode & 0o777 : 0o600 });
  fs.renameSync(tmp, file);
  return backup;
}
