import fs from "node:fs";
import path from "node:path";
import { SESSION_ID } from "./desktopSessions.mjs";

export const MAX_RUNS = 20;
const STATES = new Set(["done", "error", "stopped"]);

const clean = (e, now) => {
  if (!e || typeof e !== "object" || typeof e.id !== "string" || !/^[A-Za-z0-9_-]{1,40}$/.test(e.id) || !STATES.has(e.state)) return null;
  return {
    id: e.id,
    sessionId: typeof e.sessionId === "string" && SESSION_ID.test(e.sessionId) ? e.sessionId : "",
    project: String(e.project ?? "").slice(0, 80),
    prompt: String(e.prompt ?? "").slice(0, 200),
    state: e.state,
    text: String(e.text ?? "").slice(0, 1000),
    at: Number.isFinite(e.at) ? e.at : now(),
  };
};

export function createRunHistory(dir, { max = MAX_RUNS, now = Date.now } = {}) {
  const file = path.join(dir, "runs.json");
  let list = [];
  try {
    const j = JSON.parse(fs.readFileSync(file, "utf8"));
    if (Array.isArray(j)) list = j.map((e) => clean(e, now)).filter(Boolean).slice(0, max);
  } catch {}

  const save = () => {
    try {
      fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
      const tmp = `${file}.${process.pid}.tmp`;
      fs.writeFileSync(tmp, JSON.stringify(list), { mode: 0o600 });
      fs.renameSync(tmp, file);
    } catch {}
  };

  return {
    list: () => list.map((e) => ({ ...e })),
    add(entry) {
      const e = clean(entry, now);
      if (!e) return false;
      list = [e, ...list.filter((x) => x.id !== e.id)].slice(0, max);
      save();
      return true;
    },
  };
}
