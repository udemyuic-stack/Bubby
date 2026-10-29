#!/usr/bin/env node
// The command Claude Code (and other agents, with --agent <name>) runs for hooks, and with --statusline Claude Code's
// status line command. Never blocks the agent: any problem => exit 0, with no output (or the agent's "no opinion").
//
//   hook.mjs [--agent <name>] [<EventName>]   one hook event on stdin
//   hook.mjs --statusline                     status line payload on stdin: only rate_limits goes to the bridge,
//                                             then the user's previous status line runs and its output is printed
import net from "node:net";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import { sockPath, describeTool, HOOK_WAIT_MS, buddyDir } from "./lib/common.mjs";
import { validAgent, normalizePayload } from "./lib/agents.mjs";
import { answerOutput } from "./lib/questions.mjs";

const out = (o) => process.stdout.write((typeof o === "string" ? o : JSON.stringify(o)) + "\n");

const EDIT_TOOLS = ["Edit", "MultiEdit", "Write"];
const MAX_EDIT_BYTES = 512 * 1024; // all edit strings of one event; above that only the line counts go
const lineCount = (t) => (typeof t === "string" && t ? t.replace(/\r\n/g, "\n").replace(/\n$/, "").split("\n").length : 0);

// What the bridge needs of a file edit to show its lines on the phone (local only; the bridge keeps it in memory).
export function editInput(tool, input) {
  if (!EDIT_TOOLS.includes(tool) || !input || typeof input.file_path !== "string") return null;
  const s = (v) => (typeof v === "string" ? v : undefined);
  const e = tool === "MultiEdit"
    ? { file_path: input.file_path, edits: (Array.isArray(input.edits) ? input.edits : []).map((x) => ({ old_string: s(x?.old_string), new_string: s(x?.new_string) })) }
    : tool === "Write" ? { file_path: input.file_path, content: s(input.content) }
    : { file_path: input.file_path, old_string: s(input.old_string), new_string: s(input.new_string) };
  if (Buffer.byteLength(JSON.stringify(e)) <= MAX_EDIT_BYTES) return e;
  const pairs = tool === "MultiEdit" ? e.edits : tool === "Write" ? [{ old_string: "", new_string: e.content }] : [e];
  return { file_path: input.file_path, tooLarge: true, added: pairs.reduce((n, p) => n + lineCount(p.new_string), 0), removed: pairs.reduce((n, p) => n + lineCount(p.old_string), 0) };
}

export function buildEnvelope(payload) {
  if (!payload || typeof payload.hook_event_name !== "string" || !payload.hook_event_name) return null;
  const event = payload.hook_event_name;
  const tool = typeof payload.tool_name === "string" ? payload.tool_name : "";
  const agent = validAgent(payload.bubby_agent);
  const env = {
    event,
    sessionId: String(payload.session_id ?? ""),
    tool,
    command: describeTool(tool, payload.tool_input),
    notificationType: typeof payload.notification_type === "string" ? payload.notification_type : "",
  };
  if (agent) env.agent = agent;
  if (typeof payload.cwd === "string" && payload.cwd) env.project = path.basename(payload.cwd.replaceAll("\\", "/")).slice(0, 64);
  if (event === "Stop") {
    // only for the bridge: the local token count (stats.json) and the one-line summary; never forwarded as is
    if (typeof payload.transcript_path === "string") env.transcriptPath = payload.transcript_path;
    if (typeof payload.last_assistant_message === "string") env.finalText = payload.last_assistant_message.slice(0, 4000);
  }
  if (event === "PermissionRequest" && tool === "AskUserQuestion" && Array.isArray(payload.tool_input?.questions)) env.questions = payload.tool_input.questions;
  if (event === "PostToolUse") { const e = editInput(tool, payload.tool_input); if (e) env.edit = e; }
  return env;
}

export function decisionOutput(decision, always) {
  if (decision === "allow") {
    const d = { behavior: "allow" };
    // "always": a session rule (gone when the Claude Code session ends), for this tool, or this exact command for Bash
    if (always && typeof always.tool === "string" && always.tool) {
      const rule = { toolName: always.tool };
      if (always.tool === "Bash") { if (typeof always.command !== "string" || !always.command) return { hookSpecificOutput: { hookEventName: "PermissionRequest", decision: d } }; rule.ruleContent = always.command; }
      d.updatedPermissions = [{ type: "addRules", rules: [rule], behavior: "allow", destination: "session" }];
    }
    return { hookSpecificOutput: { hookEventName: "PermissionRequest", decision: d } };
  }
  if (decision === "deny") return { hookSpecificOutput: { hookEventName: "PermissionRequest", decision: { behavior: "deny", message: "Denied from the phone (Bubby)" } } };
  return null;
}

const RUN_SAFE_TOOLS = new Set(["Read", "Glob", "Grep", "LS", "TodoWrite", "NotebookRead"]);

// PreToolUse answer for a phone-started task: only an explicit "allow" from the phone lets the tool run; no answer = no.
export function runDecisionOutput(decision) {
  const allow = decision === "allow";
  return { hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: allow ? "allow" : "deny", permissionDecisionReason: allow ? "Allowed from the phone (Bubby)" : "Not allowed from the phone (Bubby)" } };
}

// What to print for the bridge's reply. Nothing that allows anything without a decision a person sent from the phone.
export function replyOutput(payload, env, reply) {
  if (env.agent) return null; // other agents: display only
  if (env.event !== "PermissionRequest" || !reply || typeof reply !== "object") return null;
  if (env.tool === "AskUserQuestion") return reply.answers ? answerOutput(payload.tool_input, reply.answers) : null;
  return decisionOutput(reply.decision, reply.always);
}

// Agents that read a JSON object after every hook get "{}" ("no opinion").
const WANTS_JSON = new Set(["gemini", "copilot"]);

function talk(line, waitMs) {
  return new Promise((done) => {
    let buf = "", settled = false;
    const c = net.connect(sockPath());
    const finish = (v = null) => { if (settled) return; settled = true; clearTimeout(t); c.destroy(); done(v); };
    const t = setTimeout(() => finish(null), waitMs);
    c.on("error", () => finish(null));
    c.on("connect", () => c.write(line));
    c.on("data", (d) => {
      buf += d.toString("utf8");
      const i = buf.indexOf("\n");
      if (i < 0) return;
      let o = null;
      try { o = JSON.parse(buf.slice(0, i) || "null"); } catch {}
      finish(o);
    });
    c.on("close", () => finish(null));
  });
}

function argsOf(argv) {
  let agent = null, event = "";
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--agent") agent = validAgent(argv[++i]);
    else if (!argv[i].startsWith("--") && !event) event = argv[i];
  }
  return { agent, event };
}

async function readStdin(max) {
  const chunks = [];
  let n = 0;
  for await (const c of process.stdin) { n += c.length; if (n > max) return null; chunks.push(c); }
  let b = Buffer.concat(chunks);
  if (b[0] === 0xef && b[1] === 0xbb && b[2] === 0xbf) b = b.subarray(3);
  return b;
}

async function hookMain(argv) {
  const { agent, event } = argsOf(argv);
  const raw = await readStdin(8 * 1024 * 1024);
  let payload = null;
  try { payload = raw ? JSON.parse(raw.toString("utf8")) : null; } catch {}
  if (payload && typeof payload === "object" && !Array.isArray(payload) && (agent || event || payload.hook_event_name)) {
    if (agent || event || !["SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "PermissionRequest", "Notification", "Stop", "StopFailure", "SessionEnd"].includes(payload.hook_event_name)) payload = normalizePayload(payload, { agent, event });
    const env = buildEnvelope(payload);
    // a task started from the phone (headless `claude -p`) has no permission dialog: its tools are asked here, before they run
    const runAsk = !!env && !env.agent && process.env.BUBBY_RUN === "1" && env.event === "PreToolUse" && !RUN_SAFE_TOOLS.has(env.tool);
    if (runAsk) env.event = "PermissionRequest";
    if (env) {
      const waits = env.event === "PermissionRequest" && !env.agent;
      const waitMs = Number(process.env.BUBBY_HOOK_WAIT_MS) || HOOK_WAIT_MS;
      const reply = await talk(JSON.stringify(env) + "\n", waits ? waitMs : 2000);
      if (runAsk) return out(runDecisionOutput(reply?.decision));
      const o = waits ? replyOutput(payload, env, reply) : null;
      if (o) return out(o);
    }
  }
  if (agent && WANTS_JSON.has(agent)) out("{}");
}

// ----- status line -----

const PREVIOUS_BUDGET_MS = 5000;
const MAX_OUTPUT = 64 * 1024;

export function previousStatusCommand(dir = buddyDir()) {
  try {
    const c = JSON.parse(fs.readFileSync(path.join(dir, "statusline-previous.json"), "utf8"))?.command;
    return typeof c === "string" && c.trim() ? c : null;
  } catch { return null; }
}

// Runs the user's own status line the way Claude Code does (sh -c; Git Bash on Windows) with the same input.
// Resolves its output (at most 64 KiB) or null if it failed or took too long (then it is killed).
export function runPrevious(command, input, budgetMs = PREVIOUS_BUDGET_MS) {
  return new Promise((resolve) => {
    let p;
    try {
      p = process.platform === "win32" ? spawn("bash", ["-c", command], { windowsHide: true, stdio: ["pipe", "pipe", "ignore"] })
        : spawn("/bin/sh", ["-c", command], { stdio: ["pipe", "pipe", "ignore"], detached: true });
    } catch { return resolve(null); }
    const kept = [];
    let size = 0, done = false;
    const finish = (v) => { if (done) return; done = true; clearTimeout(t); resolve(v); };
    const kill = () => { try { process.platform === "win32" ? p.kill() : process.kill(-p.pid, "SIGKILL"); } catch { try { p.kill("SIGKILL"); } catch {} } };
    const t = setTimeout(() => { kill(); finish(null); }, budgetMs);
    p.on("error", () => finish(null));
    p.stdout.on("data", (d) => { const room = MAX_OUTPUT - size; if (room > 0) { kept.push(d.subarray(0, room)); size += Math.min(room, d.length); } });
    p.stdin.on("error", () => {});
    p.stdin.end(input);
    // a background child may keep stdout open: the exit of the command is what counts, plus a short grace
    p.on("close", () => finish(Buffer.concat(kept)));
    p.on("exit", () => setTimeout(() => { p.stdout.destroy(); finish(Buffer.concat(kept)); }, 200).unref());
  });
}

async function statusLineMain() {
  const raw = (await readStdin(1024 * 1024)) ?? Buffer.alloc(0);
  let payload = null;
  try { payload = JSON.parse(raw.toString("utf8")); } catch {}
  const rl = payload?.rate_limits;
  // ONLY rate_limits (and which session) goes to the bridge: no cwd, model, transcript or anything else
  const sent = rl && typeof rl === "object" && !Array.isArray(rl) && JSON.stringify(rl).length <= 8192
    ? talk(JSON.stringify({ event: "StatusLine", sessionId: String(payload.session_id ?? ""), rateLimits: rl }) + "\n", 300)
    : Promise.resolve();
  const command = previousStatusCommand();
  if (command) {
    const o = await runPrevious(command, raw);
    if (o?.length) process.stdout.write(o);
  }
  await sent;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) {
  const argv = process.argv.slice(2);
  (argv.includes("--statusline") ? statusLineMain() : hookMain(argv)).catch(() => {}).finally(() => process.exit(0));
}
