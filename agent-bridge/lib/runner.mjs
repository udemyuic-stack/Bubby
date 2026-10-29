// Runs one headless Claude Code task for the phone: `claude -p "<task>"` in a fixed folder of this computer.
// Off unless the computer's owner starts the bridge with --allow-run. One task at a time, at most 30 minutes.
// Tool permissions of the task go through hook.mjs (BUBBY_RUN=1): each one is asked on the phone like any other request.
import { spawn as realSpawn } from "node:child_process";

export const MAX_PROMPT = 4000;
const MAX_OUT = 1024 * 1024;
const MAX_TEXT = 4000;
const TIMEOUT_MS = 30 * 60_000;

export function createRunner({ bin = "claude", workdir, spawn = realSpawn, onState, timeoutMs = TIMEOUT_MS }) {
  let cur = null; // {id, child, timer, stopped}
  const emit = (id, state, text = "") => { try { onState?.({ id, state, text: String(text).slice(0, MAX_TEXT) }); } catch {} };

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
    start(id, prompt) {
      if (typeof prompt !== "string" || !prompt.trim() || prompt.length > MAX_PROMPT) { emit(id, "error", "bad task"); return false; }
      if (typeof id !== "string" || !/^[A-Za-z0-9_-]{1,40}$/.test(id)) return false;
      if (cur) { emit(id, "error", "another task is running"); return false; }
      let child;
      try {
        child = spawn(bin, ["-p", prompt, "--output-format", "json"], { cwd: workdir, env: { ...process.env, BUBBY_RUN: "1" }, stdio: ["ignore", "pipe", "pipe"], windowsHide: true });
      } catch (e) { emit(id, "error", String(e.message ?? e)); return false; }
      const run = { id, child, stopped: false, timer: null };
      cur = run;
      let out = "", err = "", done = false;
      const end = (state, text) => { if (done) return; done = true; clearTimeout(run.timer); if (cur === run) cur = null; emit(id, state, text); };
      run.timer = setTimeout(() => { try { child.kill("SIGTERM"); } catch {} end("error", "stopped: took longer than 30 minutes"); }, timeoutMs);
      run.timer.unref?.();
      child.stdout?.on("data", (d) => { if (out.length < MAX_OUT) out += d.toString("utf8"); });
      child.stderr?.on("data", (d) => { if (err.length < 8192) err += d.toString("utf8"); });
      child.on("error", (e) => end("error", e.code === "ENOENT" ? `"${bin}" was not found on the computer` : String(e.message ?? e)));
      child.on("close", (code) => {
        if (run.stopped) return end("stopped", "");
        const r = finishText(out.trim());
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
