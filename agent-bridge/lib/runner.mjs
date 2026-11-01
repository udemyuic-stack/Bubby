// Runs one headless Claude Code task for the phone: `claude -p "<task>"` in a fixed folder of this computer.
// Off unless the computer's owner starts the bridge with --allow-run. One task at a time, at most 30 minutes.
// Tool permissions of the task go through hook.mjs (BUBBY_RUN=1): each one is asked on the phone like any other request.
import { spawn as realSpawn } from "node:child_process";
import { SESSION_ID } from "./desktopSessions.mjs";

export const MAX_PROMPT = 4000;
const MAX_OUT = 1024 * 1024;
const MAX_TEXT = 4000;
const TIMEOUT_MS = 30 * 60_000;
const PROGRESS_MS = 500;
const MAX_LINE = 1024 * 1024;

export function createRunner({ bin = "claude", workdir, spawn = realSpawn, onState, timeoutMs = TIMEOUT_MS }) {
  let cur = null; // {id, child, timer, stopped}
  const emit = (id, state, text = "", sessionId = "") => { try { onState?.({ id, state, text: String(text).slice(0, MAX_TEXT), ...(SESSION_ID.test(sessionId) ? { sessionId } : {}) }); } catch {} };

  const stepOf = (ev) => {
    if (ev?.type !== "assistant" || !Array.isArray(ev.message?.content)) return "";
    let text = "", tool = "";
    for (const c of ev.message.content) {
      if (c?.type === "text" && typeof c.text === "string" && c.text.trim()) text = c.text.trim();
      if (c?.type === "tool_use" && typeof c.name === "string") tool = c.name;
    }
    return tool ? `Using ${tool}` : text;
  };

  const finishText = (raw) => {
    // `--output-format json` prints one object with the final answer in `result`
    try {
      const j = JSON.parse(raw);
      if (typeof j?.result === "string") return { ok: !j.is_error, text: j.result };
      return { ok: true, text: raw };
    } catch { return { ok: true, text: raw }; }
  };

  return {
    busy: () => !!cur,
    start(id, prompt, cwd = workdir, resumeId = null, live = false) {
      if (typeof prompt !== "string" || !prompt.trim() || prompt.length > MAX_PROMPT) { emit(id, "error", "bad task"); return false; }
      if (typeof id !== "string" || !/^[A-Za-z0-9_-]{1,40}$/.test(id)) return false;
      if (cur) { emit(id, "error", "another task is running"); return false; }
      if (resumeId !== null && !SESSION_ID.test(resumeId)) { emit(id, "error", "bad session"); return false; }
      const args = ["-p", prompt, ...(live ? ["--output-format", "stream-json", "--verbose"] : ["--output-format", "json"]), ...(resumeId ? ["--resume", resumeId] : [])];
      let child;
      try {
        child = spawn(bin, args, { cwd, env: { ...process.env, BUBBY_RUN: "1" }, stdio: ["ignore", "pipe", "pipe"], windowsHide: true });
      } catch (e) { emit(id, "error", String(e.message ?? e)); return false; }
      const run = { id, child, stopped: false, timer: null };
      cur = run;
      let out = "", err = "", done = false, sessionId = resumeId ?? "", lineBuf = "", finalEvent = null, lastStep = "", lastSent = 0, stepTimer = null;
      const flushStep = () => { stepTimer = null; if (done || !lastStep) return; lastSent = Date.now(); emit(id, "progress", lastStep, sessionId); lastStep = ""; };
      const step = (text) => {
        if (!text) return;
        lastStep = text;
        const wait = PROGRESS_MS - (Date.now() - lastSent);
        if (wait <= 0) flushStep();
        else if (!stepTimer) { stepTimer = setTimeout(flushStep, wait); stepTimer.unref?.(); }
      };
      const onLine = (line) => {
        let ev;
        try { ev = JSON.parse(line); } catch { return; }
        if (typeof ev?.session_id === "string" && SESSION_ID.test(ev.session_id)) sessionId = ev.session_id;
        if (ev?.type === "result") finalEvent = ev;
        else step(stepOf(ev));
      };
      const end = (state, text) => { if (done) return; done = true; clearTimeout(run.timer); clearTimeout(stepTimer); if (cur === run) cur = null; emit(id, state, text, sessionId); };
      run.timer = setTimeout(() => { try { child.kill("SIGTERM"); } catch {} end("error", "stopped: took longer than 30 minutes"); }, timeoutMs);
      run.timer.unref?.();
      child.stdout?.on("data", (d) => {
        if (!live) { if (out.length < MAX_OUT) out += d.toString("utf8"); return; }
        lineBuf += d.toString("utf8");
        let i;
        while ((i = lineBuf.indexOf("\n")) >= 0) { const line = lineBuf.slice(0, i); lineBuf = lineBuf.slice(i + 1); if (line.length <= MAX_LINE) onLine(line); }
        if (lineBuf.length > MAX_LINE) lineBuf = "";
      });
      child.stderr?.on("data", (d) => { if (err.length < 8192) err += d.toString("utf8"); });
      child.on("error", (e) => end("error", e.code === "ENOENT" ? `"${bin}" was not found on the computer` : String(e.message ?? e)));
      child.on("close", (code) => {
        if (run.stopped) return end("stopped", "");
        if (live && lineBuf.trim()) onLine(lineBuf);
        const r = live ? finishText(finalEvent ? JSON.stringify(finalEvent) : "") : finishText(out.trim());
        if (code === 0 && r.ok) end("done", r.text || "(no answer)");
        else end("error", r.text || err.trim() || `exit code ${code}`);
      });
      emit(id, "running");
      return true;
    },
    stop(id) {
      if (!cur || cur.id !== id) return false;
      cur.stopped = true;
      try { cur.child.kill("SIGTERM"); } catch {}
      return true;
    },
    stopAll() { if (cur) { cur.stopped = true; try { cur.child.kill("SIGTERM"); } catch {} } },
  };
}
