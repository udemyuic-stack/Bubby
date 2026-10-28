// Agent sessions as the phone sees them: one pill per agent (Claude Code, or a `bubby_agent` name), with the optional
// details (docs/AGENTS-V3.md section 3) and file changes (section 4). Never kept: prompts, command output, the full
// answer, file contents beyond the diff lines, full paths in anything sent.
import { PILL_ID, AGENT } from "./common.mjs";
import { buildFileDiff, KEEP_LINES } from "./filediff.mjs";

const PRIORITY = { approval: 5, question: 4, error: 3, working: 2, thinking: 2, finished: 1, idle: 0 };
export const MAX_STEPS = 20;
export const MAX_STEP = 200;
export const MAX_FILES = 20;
export const SESSIONS_BUDGET = 56 * 1024;

const cut = (s, n) => { const a = Array.from(String(s ?? "")); return a.length > n ? a.slice(0, n).join("") : a.join(""); };
const oneLineOf = (s) => String(s ?? "").replace(/[\u0000-\u001f\u007f]+/g, " ").replace(/\s+/g, " ").trim();

export const pillIdOf = (agent) => (agent ? `agent_${agent}` : PILL_ID);

// The agent's final message as one line of plain text: its first non-empty paragraph, without Markdown markers.
export function finalLineOf(text) {
  if (typeof text !== "string") return "";
  const para = [];
  for (const raw of text.replace(/\r\n/g, "\n").split("\n")) {
    const l = raw.trim();
    if (!l || /^(-{3,}|\*{3,}|_{3,})$/.test(l) || /^\|/.test(l) || /^```/.test(l)) { if (para.length) break; continue; }
    para.push(l.replace(/^#+\s*/, "").replace(/^([-*+]|\d+[.)])\s+/, "").replace(/^>\s*/, ""));
  }
  return cut(oneLineOf(para.join(" ").replace(/\*\*|__|`/g, "")), MAX_STEP);
}

// The step shown for a tool call: the command for Bash, the file name for file tools, else the tool name.
export function stepOf(tool, command) {
  const t = String(tool ?? "");
  if (!t) return "";
  if (t === "Bash") return cut(oneLineOf(command) || "Bash", MAX_STEP);
  if (["Edit", "Write", "MultiEdit", "NotebookEdit"].includes(t)) return cut(command || t, MAX_STEP);
  if (t === "Read" && command) return cut(`Read ${command}`, MAX_STEP);
  return cut(t, MAX_STEP);
}

export function createSessions({ now = Date.now } = {}) {
  const sessions = new Map(); // agent \u001f sessionId -> {agent, state, statusText, steps, updatedAt, project, files: Map path -> file}
  const pills = new Map(); // agent ("" = Claude Code) -> {steps: [], finalLine}
  let nextFileId = 1;
  const key = (agent, id) => `${agent ?? ""}\u001f${id}`;
  const pill = (agent) => { const k = agent ?? ""; if (!pills.has(k)) pills.set(k, { steps: [], finalLine: "" }); return pills.get(k); };

  return {
    get: (agent, id) => sessions.get(key(agent, id)),
    touch(agent, id, patch) {
      const k = key(agent, id);
      const s = sessions.get(k) ?? { agent: agent ?? null, state: "idle", statusText: "", steps: 0, updatedAt: 0, project: "", files: new Map() };
      Object.assign(s, patch, { updatedAt: now() });
      sessions.set(k, s);
      return s;
    },
    step(agent, text) {
      if (!text) return;
      const p = pill(agent);
      p.steps.push(cut(text, MAX_STEP));
      while (p.steps.length > MAX_STEPS) p.steps.shift();
    },
    setFinal(agent, line) { pill(agent).finalLine = line ? cut(line, MAX_STEP) : ""; },
    // a finished Edit/MultiEdit/Write: one entry per file, edits of the same file add up
    edit(agent, id, tool, input) {
      const d = buildFileDiff(tool, input);
      const s = sessions.get(key(agent, id));
      if (!d || !s) return null;
      const path = String(input.file_path);
      const f = s.files.get(path);
      if (f) {
        s.files.delete(path);
        f.added += d.added;
        f.removed += d.removed;
        f.tooLarge ||= !!d.tooLarge;
        if (f.tooLarge) f.lines = [];
        else for (const l of d.lines) if (f.lines.length < KEEP_LINES) f.lines.push(l);
        f.at = now();
        s.files.set(path, f);
      } else {
        s.files.set(path, { id: nextFileId++, ...d, isNew: !!d.isNew, tooLarge: !!d.tooLarge, at: now() });
        while (s.files.size > 50) s.files.delete(s.files.keys().next().value);
      }
      return s.files.get(path);
    },
    end(agent, id) { sessions.delete(key(agent, id)); }, // its diffs go with it
    prune(maxAgeMs) { const t = now(); for (const [k, s] of sessions) if (t - s.updatedAt > maxAgeMs) sessions.delete(k); },
    size: () => sessions.size,
    // the file with this id in one of the pill's sessions, or null ("gone")
    file(pillId, fileId) {
      for (const s of sessions.values()) {
        if (pillIdOf(s.agent) !== pillId) continue;
        for (const f of s.files.values()) if (f.id === fileId) return f;
      }
      return null;
    },
    // the `sessions` list for a phone with these capabilities
    list(caps = new Set()) {
      const byAgent = new Map();
      for (const s of sessions.values()) {
        const k = s.agent ?? "";
        if (!byAgent.has(k)) byAgent.set(k, []);
        byAgent.get(k).push(s);
      }
      const out = [];
      for (const [k, list] of byAgent) {
        let best = null, steps = 0, newest = 0;
        for (const s of list) {
          steps = Math.max(steps, s.steps);
          newest = Math.max(newest, s.updatedAt);
          if (!best || (PRIORITY[s.state] ?? 0) > (PRIORITY[best.state] ?? 0) || ((PRIORITY[s.state] ?? 0) === (PRIORITY[best.state] ?? 0) && s.updatedAt > best.updatedAt)) best = s;
        }
        const e = { pillId: pillIdOf(k || null), agent: k || AGENT, state: best.state, statusText: best.statusText, stepIndex: steps, stepCount: steps, updatedAt: newest };
        if (caps.has("details")) {
          const p = pills.get(k);
          if (best.project) e.project = cut(best.project, 64);
          if (p?.steps.length) e.steps = [...p.steps];
          if (p?.finalLine) e.finalLine = p.finalLine;
        }
        if (caps.has("diffs")) {
          const files = list.flatMap((s) => [...s.files.values()]).sort((a, b) => b.at - a.at || b.id - a.id).slice(0, MAX_FILES)
            .map((f) => ({ id: f.id, name: f.name, added: f.added, removed: f.removed, ...(f.tooLarge && { tooLarge: true }), ...(f.isNew && { isNew: true }) }));
          if (files.length) e.files = files;
        }
        out.push(e);
      }
      return out;
    },
  };
}

// The whole `sessions` line under 56 KiB: the oldest steps go first, then the oldest files, then the final lines.
export function sessionsMessage(list, budget = SESSIONS_BUDGET) {
  const msg = { type: "sessions", sessions: list.map((e) => ({ ...e, ...(e.steps && { steps: [...e.steps] }), ...(e.files && { files: [...e.files] }) })) };
  const size = () => Buffer.byteLength(JSON.stringify(msg)) + 1;
  while (size() > budget) {
    const withSteps = msg.sessions.filter((e) => e.steps?.length).sort((a, b) => b.steps.length - a.steps.length)[0];
    if (withSteps) { withSteps.steps.shift(); if (!withSteps.steps.length) delete withSteps.steps; continue; }
    const withFiles = msg.sessions.filter((e) => e.files?.length).sort((a, b) => b.files.length - a.files.length)[0];
    if (withFiles) { withFiles.files.pop(); if (!withFiles.files.length) delete withFiles.files; continue; }
    const withFinal = msg.sessions.find((e) => e.finalLine);
    if (withFinal) { delete withFinal.finalLine; continue; }
    break;
  }
  return msg;
}
