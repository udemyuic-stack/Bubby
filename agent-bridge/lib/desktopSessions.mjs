import fs from "node:fs";
import os from "node:os";
import path from "node:path";

export const SESSION_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export const ACTIVE_MS = 3 * 60_000;
const MAX_LISTED = 30;
const MAX_TITLE = 80;

const readDirs = (dir) => { try { return fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => path.join(dir, e.name)); } catch { return []; } };

export function createDesktopSessions({
  sessionsDir = path.join(os.homedir(), ".config", "Claude", "claude-code-sessions"),
  projectsDir = path.join(os.homedir(), ".claude", "projects"),
  now = Date.now,
} = {}) {
  const readAll = () => {
    const found = [];
    for (const org of readDirs(sessionsDir)) {
      for (const acct of readDirs(org)) {
        let files = [];
        try { files = fs.readdirSync(acct).filter((f) => /^local_.*\.json$/.test(f)); } catch { continue; }
        for (const f of files) {
          try {
            const j = JSON.parse(fs.readFileSync(path.join(acct, f), "utf8"));
            if (j.isArchived || !SESSION_ID.test(j.cliSessionId ?? "") || typeof j.cwd !== "string") continue;
            found.push({ id: j.cliSessionId, title: String(j.title ?? "").slice(0, MAX_TITLE), cwd: j.cwd, lastActivityAt: Number(j.lastActivityAt) || 0 });
          } catch {}
        }
      }
    }
    return found.sort((a, b) => b.lastActivityAt - a.lastActivityAt);
  };

  const transcriptMtime = (id) => {
    for (const p of readDirs(projectsDir)) {
      try { return fs.statSync(path.join(p, `${id}.jsonl`)).mtimeMs; } catch {}
    }
    return 0;
  };

  return {
    list: () => readAll().slice(0, MAX_LISTED),
    find: (id) => (typeof id === "string" && SESSION_ID.test(id) ? readAll().find((s) => s.id === id) ?? null : null),
    busy: (id) => typeof id !== "string" || !SESSION_ID.test(id) || now() - transcriptMtime(id) < ACTIVE_MS,
  };
}
