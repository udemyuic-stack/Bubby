import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

export const PROTOCOL_V = 1;
export const MAX_LINE = 64 * 1024;
export const MAX_COMMAND = 500;
export const HOOK_WAIT_MS = 110_000;
export const APPROVAL_TIMEOUT_MS = 115_000;
export const PILL_ID = "integration_claude";
export const AGENT = "Claude Code";

export const buddyDir = () => process.env.BUBBY_DIR || path.join(os.homedir(), ".bubby");
export const sockPath = (dir = buddyDir()) => path.join(dir, "hook.sock");

export function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  try { fs.chmodSync(dir, 0o700); } catch {}
}

// Same derivation as ApprovalRelay.fingerprint in the coucou protocol (docs/ANDROID_LINK.md): SHA-256 of the fields joined by U+001F.
export const fingerprint = (pillId, sessionId, tool, command, inputKey) =>
  crypto.createHash("sha256").update([pillId, sessionId, tool, command, inputKey].join("\u001f"), "utf8").digest("hex");

// The first MAX_COMMAND characters (code points) and whether something was cut.
export function truncateCommand(text, max = MAX_COMMAND) {
  const chars = Array.from(String(text ?? ""));
  return chars.length > max ? { command: chars.slice(0, max).join(""), truncated: true } : { command: chars.join(""), truncated: false };
}

const base = (p) => (typeof p === "string" ? path.basename(p.replaceAll("\\", "/")).slice(0, 80) : "");

// What the phone is told about a tool call: the command for Bash, a file NAME for file tools, nothing else.
// Never prompts, file contents or full paths.
export function describeTool(tool, input) {
  const i = input && typeof input === "object" ? input : {};
  if (tool === "Bash") return typeof i.command === "string" ? i.command : "";
  if (["Read", "Edit", "Write", "MultiEdit", "NotebookEdit"].includes(tool)) return base(i.file_path ?? i.notebook_path);
  return "";
}

export function lanIps() {
  const out = [];
  for (const list of Object.values(os.networkInterfaces()))
    for (const a of list ?? []) if (a.family === "IPv4") out.push(a.address);
  return out;
}
export const isPrivateIPv4 = (ip) => /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip);
export function defaultHost() {
  for (const list of Object.values(os.networkInterfaces()))
    for (const a of list ?? []) if (a.family === "IPv4" && !a.internal && isPrivateIPv4(a.address)) return a.address;
  return null;
}
