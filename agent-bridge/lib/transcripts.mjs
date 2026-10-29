// Live view without hooks: Claude Code writes every turn of a session to ~/.claude/projects/<project>/<session>.jsonl.
// Hooks are only read when a session starts, so a session that was already running when the bridge was installed
// would stay silent on the phone. This watcher reads the tail of the recently written transcripts and reports the same
// things the hooks would (state, the tool in use, the project folder name). It tells the phone nothing the hooks do not:
// a tool name, a file NAME or the Bash command, never prompts, answers or file contents.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describeTool } from "./common.mjs";

const TAIL_BYTES = 192 * 1024;
export const ACTIVE_MS = 3 * 60_000;   // a transcript untouched for this long is not a live session
export const BUSY_MS = 20_000;         // written within this long: the agent is working, otherwise it waits for you

export const transcriptsRoot = () => path.join(process.env.CLAUDE_CONFIG_DIR || path.join(os.homedir(), ".claude"), "projects");

// The state of a session from the last lines of its transcript (oldest first) and the age of the file.
export function readState(lines, ageMs) {
  let tool = null, toolId = "", cwd = "", kind = "";
  for (let i = lines.length - 1; i >= 0; i--) {
    let o;
    try { o = JSON.parse(lines[i]); } catch { continue; }
    if (!o || o.isSidechain || (o.type !== "user" && o.type !== "assistant")) continue;
    if (!cwd && typeof o.cwd === "string") cwd = o.cwd;
    if (kind) { if (cwd) break; continue; }
    const c = o.message?.content;
    if (o.type === "assistant") {
      const use = Array.isArray(c) ? [...c].reverse().find((x) => x?.type === "tool_use") : null;
      if (use && o.message?.stop_reason !== "end_turn") { kind = "tool"; toolId = String(use.id ?? ""); tool = { name: String(use.name ?? ""), text: describeTool(use.name, use.input) }; }
      else if (Array.isArray(c) && c.some((x) => x?.type === "text")) kind = "answered";
      else kind = "thinking";
    } else {
      kind = Array.isArray(c) && c.some((x) => x?.type === "tool_result") ? "result" : "prompt";
    }
  }
  if (!kind) return null;
  const busy = ageMs < BUSY_MS;
  const base = { cwd, toolId };
  if (kind === "tool") return { ...base, state: busy ? "working" : "idle", statusText: busy ? `Using ${tool.name.slice(0, 30)}` : "Waiting for you", tool };
  if (kind === "result" || kind === "prompt" || kind === "thinking") return { ...base, state: busy ? "thinking" : "idle", statusText: busy ? "Thinking" : "Waiting for you" };
  return { ...base, state: ageMs < 60_000 ? "finished" : "idle", statusText: ageMs < 60_000 ? "Done" : "Waiting for you" };
}

function tailLines(file, size) {
  const fd = fs.openSync(file, "r");
  try {
    const len = Math.min(size, TAIL_BYTES);
    const buf = Buffer.alloc(len);
    fs.readSync(fd, buf, 0, len, size - len);
    const lines = buf.toString("utf8").split("\n");
    if (len < size) lines.shift(); // starts in the middle of a line
    return lines.filter(Boolean);
  } finally { fs.closeSync(fd); }
}

/**
 * onSession(id, info) for a live session whose state changed (info = readState + project + tool step),
 * onEnd(id) for one that went quiet. `skip(id)` = the hooks already report this session.
 */
export function createTranscriptWatcher({ root = transcriptsRoot(), intervalMs = 1500, now = Date.now, skip = () => false, onSession, onEnd }) {
  const seen = new Map(); // session id -> {mtime, key}
  let timer = null;

  function scan() {
    const t = now();
    let dirs = [];
    try { dirs = fs.readdirSync(root, { withFileTypes: true }).filter((d) => d.isDirectory()); } catch { /* no Claude Code here */ }
    const live = new Set();
    for (const d of dirs) {
      let files = [];
      try { files = fs.readdirSync(path.join(root, d.name)); } catch { continue; }
      for (const f of files) {
        if (!f.endsWith(".jsonl")) continue;
        const id = f.slice(0, -6);
        const file = path.join(root, d.name, f);
        let st;
        try { st = fs.statSync(file); } catch { continue; }
        if (t - st.mtimeMs > ACTIVE_MS || skip(id)) continue;
        live.add(id);
        const prev = seen.get(id);
        // an unchanged file still changes state by ageing (working -> waiting, done -> idle): read it again when its phase changes
        const age = t - st.mtimeMs;
        const phase = age < BUSY_MS ? 0 : age < 60_000 ? 1 : 2;
        if (prev && prev.mtime === st.mtimeMs && prev.phase === phase) continue;
        let s;
        try { s = readState(tailLines(file, st.size), age); } catch { continue; }
        const cur = { mtime: st.mtimeMs, phase, key: "", toolId: prev?.toolId ?? "" };
        if (s) {
          const project = s.cwd ? path.basename(s.cwd.replaceAll("\\", "/")).slice(0, 64) : d.name.replace(/^-+/, "").split("-").pop();
          const step = s.tool && s.toolId !== cur.toolId ? s.tool : null;
          if (s.toolId) cur.toolId = s.toolId;
          cur.key = `${s.state}|${s.statusText}`;
          if (cur.key !== prev?.key || step) onSession(id, { state: s.state, statusText: s.statusText, project, step });
        }
        seen.set(id, cur);
      }
    }
    for (const id of [...seen.keys()]) if (!live.has(id)) { seen.delete(id); onEnd(id); }
  }

  return {
    scan,
    start() { if (!timer) { scan(); timer = setInterval(scan, intervalMs); timer.unref?.(); } },
    stop() { if (timer) clearInterval(timer); timer = null; },
  };
}
