#!/usr/bin/env node
// The command Claude Code runs for hooks. Never blocks Claude: any problem => exit 0 with no output.
import net from "node:net";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import { sockPath, describeTool, HOOK_WAIT_MS } from "./lib/common.mjs";

const out = (o) => process.stdout.write(JSON.stringify(o) + "\n");

export function buildEnvelope(payload) {
  if (!payload || typeof payload.hook_event_name !== "string") return null;
  return {
    event: payload.hook_event_name,
    sessionId: String(payload.session_id ?? ""),
    tool: typeof payload.tool_name === "string" ? payload.tool_name : "",
    command: describeTool(payload.tool_name, payload.tool_input),
    notificationType: typeof payload.notification_type === "string" ? payload.notification_type : "",
    // only for the bridge's local token count (stats.json); never forwarded to the phone
    ...(payload.hook_event_name === "Stop" && typeof payload.transcript_path === "string" ? { transcriptPath: payload.transcript_path } : {}),
  };
}

export function decisionOutput(decision) {
  if (decision === "allow") return { hookSpecificOutput: { hookEventName: "PermissionRequest", decision: { behavior: "allow" } } };
  if (decision === "deny") return { hookSpecificOutput: { hookEventName: "PermissionRequest", decision: { behavior: "deny", message: "Denied from the phone (Bubby)" } } };
  return null;
}

async function main() {
  let raw = "";
  for await (const c of process.stdin) { raw += c; if (raw.length > 8 * 1024 * 1024) return; }
  let payload;
  try { payload = JSON.parse(raw); } catch { return; }
  const env = buildEnvelope(payload);
  if (!env) return;
  const waits = env.event === "PermissionRequest";
  const waitMs = Number(process.env.BUBBY_HOOK_WAIT_MS) || HOOK_WAIT_MS;
  await new Promise((done) => {
    const c = net.connect(sockPath());
    let buf = "";
    const finish = () => { clearTimeout(t); c.destroy(); done(); };
    const t = setTimeout(finish, waits ? waitMs : 2000);
    c.on("error", finish);
    c.on("connect", () => c.write(JSON.stringify(env) + "\n"));
    c.on("data", (d) => {
      buf += d.toString("utf8");
      const i = buf.indexOf("\n");
      if (i < 0) return;
      try {
        const o = decisionOutput(JSON.parse(buf.slice(0, i) || "{}").decision);
        if (o && waits) out(o);
      } catch {}
      finish();
    });
    c.on("close", finish);
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) {
  main().catch(() => {}).finally(() => process.exit(0));
}
