// Bubby's entries in ~/.claude/settings.json (hooks and the statusLine relay) and, through planJsonEdit/applyPlan,
// in other agents' config files (lib/agents.mjs). Shared by install-hooks.mjs and bridge.mjs (first-run setup).
// Never overwrites a file blindly: strict JSON read, diff, dated .bak copy, atomic write.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { unifiedDiff } from "./diff.mjs";
import { buddyDir, ensureDir } from "./common.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const HOOK_PATH = path.join(HERE, "..", "hook.mjs");
// Claude Code runs "command" through a shell, so both paths are quoted.
export const COMMAND = `"${process.execPath}" "${HOOK_PATH}"`;
export const STATUS_COMMAND = `${COMMAND} --statusline`;
export const EVENTS = ["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "PermissionRequest", "Notification", "Stop", "StopFailure", "SessionEnd"];
const TIMEOUT = { PermissionRequest: 120 }; // seconds; hook.mjs itself gives up after 110 s

export const isOurCommand = (c) => typeof c === "string" && c.includes(HOOK_PATH);
const isOurs = (h) => h && isOurCommand(h.command);
export const isOurStatusLine = (sl) => !!sl && typeof sl === "object" && isOurCommand(sl.command) && sl.command.includes("--statusline");

export const settingsPath = () => process.env.CLAUDE_SETTINGS || path.join(os.homedir(), ".claude", "settings.json");
export const previousStatusLinePath = (dir = buddyDir()) => path.join(dir, "statusline-previous.json");

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

// statusLine after installing or removing the relay. Installing swaps only `command` (padding etc. stay) and says what
// to save as the previous status line; removing puts the saved one back (or drops the key). A statusLine we do not
// understand (not an object) is left alone, with a note.
export function mergeStatusLine(settings, { uninstall = false, previous = null } = {}) {
  const out = structuredClone(settings);
  const sl = out.statusLine;
  if (uninstall) {
    if (!isOurStatusLine(sl)) return { settings: out, save: null, clear: false };
    if (previous && typeof previous === "object" && !Array.isArray(previous)) out.statusLine = previous;
    else delete out.statusLine;
    return { settings: out, save: null, clear: true };
  }
  if (sl !== undefined && (!sl || typeof sl !== "object" || Array.isArray(sl))) return { settings: out, save: null, clear: false, note: '"statusLine" имеет неожиданный вид, не трогаю его' };
  if (isOurStatusLine(sl)) return { settings: out, save: null, clear: false };
  out.statusLine = { ...(sl ?? {}), type: "command", command: STATUS_COMMAND };
  return { settings: out, save: sl ?? null, clear: !sl };
}

function readJsonObject(file) {
  let before = "", existed = false;
  if (fs.existsSync(file)) { before = fs.readFileSync(file, "utf8"); existed = true; }
  let current;
  try { current = before.trim() ? JSON.parse(before) : {}; } catch { throw new Error(`${file} не является корректным JSON, ничего не меняю.`); }
  if (!current || typeof current !== "object" || Array.isArray(current)) throw new Error(`${file} не является JSON-объектом, ничего не меняю.`);
  return { current, existed };
}

// transform(current) => the new object, or null to delete the file (only ever a file of our own).
// diff === "" means nothing to do.
export function planJsonEdit(file, transform) {
  const { current, existed } = readJsonObject(file);
  const next = transform(current);
  const norm = JSON.stringify(current, null, 2) + "\n";
  if (next === null) return { file, existed, after: null, diff: existed ? unifiedDiff(norm, "", file, "/dev/null") : "" };
  const after = JSON.stringify(next, null, 2) + "\n";
  return { file, existed, after, diff: unifiedDiff(norm, after, file, file + " (new)") };
}

// Claude Code: hooks and/or the statusLine relay. Throws without touching anything if settings.json is not valid
// JSON or has an odd shape.
export function planHooks({ uninstall = false, file = settingsPath(), hooks = true, statusLine = true, dir = buddyDir() } = {}) {
  let previous = null;
  try { previous = JSON.parse(fs.readFileSync(previousStatusLinePath(dir), "utf8")); } catch {}
  let sl = { save: null, clear: false };
  const plan = planJsonEdit(file, (current) => {
    let next = hooks ? mergeSettings(current, uninstall) : current;
    if (statusLine) {
      sl = mergeStatusLine(next, { uninstall, previous });
      next = sl.settings;
    }
    return next;
  });
  return { ...plan, uninstall, saveStatusLine: sl.save, clearStatusLine: sl.clear, note: sl.note, dir };
}

const stamp = () => new Date().toISOString().replace(/[-:]/g, "").replace(/\..*/, "").replace("T", "-");

// Writes a plan: dated backup first (<file>.bak-YYYYMMDD-HHMMSS), then an atomic rename (or the deletion).
// Returns the backup path or null.
export function applyPlan(plan) {
  const { file, existed, after } = plan;
  let backup = null;
  if (existed) {
    const s = stamp();
    backup = `${file}.bak-${s}`;
    for (let n = 2; fs.existsSync(backup); n++) backup = `${file}.bak-${s}-${n}`;
    fs.copyFileSync(file, backup, fs.constants.COPYFILE_EXCL);
  }
  if (after === null) { if (existed) fs.rmSync(file, { force: true }); return backup; }
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = `${file}.tmp-${process.pid}`;
  fs.writeFileSync(tmp, after, { mode: existed ? fs.statSync(file).mode & 0o777 : 0o600 });
  fs.renameSync(tmp, file);
  return backup;
}

// Claude Code plan: also keeps statusline-previous.json in step (saved before the write, removed after an uninstall).
export function applyHooks(plan) {
  const prev = previousStatusLinePath(plan.dir);
  if (plan.saveStatusLine) {
    ensureDir(plan.dir);
    fs.writeFileSync(prev, JSON.stringify(plan.saveStatusLine, null, 2) + "\n", { mode: 0o600 });
  }
  let backup;
  try { backup = applyPlan(plan); } catch (e) { if (plan.saveStatusLine) fs.rmSync(prev, { force: true }); throw e; }
  if (plan.clearStatusLine) fs.rmSync(prev, { force: true });
  return backup;
}
