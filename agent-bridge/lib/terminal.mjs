import { spawn as realSpawn } from "node:child_process";

export const TERMINAL_BIN = "ghostty";

export const terminalAvailable = (env = process.env) => Boolean(env.DISPLAY || env.WAYLAND_DISPLAY);

export function terminalCommand({ term = TERMINAL_BIN, cwd, bin, prompt, sessionId, resume }) {
  const session = resume ? ["--resume", sessionId] : ["--session-id", sessionId];
  return { cmd: term, args: [`--working-directory=${cwd}`, "-e", bin, ...session, "--", prompt] };
}

export function openTerminal(opts, spawn = realSpawn) {
  const { cmd, args } = terminalCommand(opts);
  return new Promise((resolve) => {
    let child;
    try {
      child = spawn(cmd, args, { cwd: opts.cwd, stdio: "ignore", detached: true, env: process.env });
    } catch (e) {
      return resolve({ ok: false, reason: String(e.message ?? e) });
    }
    child.once("error", (e) => resolve({ ok: false, reason: e.code === "ENOENT" ? `"${cmd}" was not found on the computer` : String(e.message ?? e) }));
    child.once("spawn", () => { child.unref?.(); resolve({ ok: true }); });
  });
}
