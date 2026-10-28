// More agents (docs/AGENTS-V3.md section 5): the `bubby_agent` name, mapping other agents' hook events and fields onto
// Claude Code's, and installers for the agents whose hook configuration is a plain JSON file with a known format.
// Event tables follow what Coucou (MIT) documents for these agents; the code is Bubby's own.
import os from "node:os";
import path from "node:path";
import { COMMAND, isOurCommand, planJsonEdit, applyPlan } from "./hooks.mjs";

export const AGENT_RE = /^[a-z0-9-]{1,24}$/;
export const validAgent = (a) => (typeof a === "string" && AGENT_RE.test(a) ? a : null);

const EVENT_MAP = {
  // Gemini CLI
  BeforeTool: "PreToolUse", BeforeToolSelection: "PreToolUse", AfterTool: "PostToolUse", BeforeAgent: "UserPromptSubmit", AfterAgent: "Stop",
  // GitHub Copilot CLI (camelCase)
  sessionStart: "SessionStart", userPromptSubmitted: "UserPromptSubmit", preToolUse: "PreToolUse", postToolUse: "PostToolUse",
  agentStop: "Stop", sessionEnd: "SessionEnd", notification: "Notification", permissionRequest: "PermissionRequest",
  // generic snake_case
  session_start: "SessionStart", session_end: "SessionEnd", user_prompt_submit: "UserPromptSubmit", pre_tool_use: "PreToolUse", post_tool_use: "PostToolUse", stop: "Stop",
};
export const canonicalEvent = (e) => (typeof e === "string" ? EVENT_MAP[e] ?? e : "");

const str = (v) => (typeof v === "string" && v ? v : null);

// Fills tool_name / tool_input / session_id / cwd from wherever another agent put them; never overwrites.
export function normalizePayload(p, { agent = null, event = "", env = process.env } = {}) {
  const o = { ...p };
  o.hook_event_name = canonicalEvent(str(p.hook_event_name) ?? event);
  const tc = p.toolCall && typeof p.toolCall === "object" ? p.toolCall : null;
  if (!str(o.tool_name)) { const t = str(p.toolName) ?? str(tc?.name) ?? str(p.tool); if (t) o.tool_name = t; }
  if (!o.tool_input || typeof o.tool_input !== "object") {
    let args = p.toolArgs;
    if (typeof args === "string") { try { args = JSON.parse(args); } catch { args = null; } }
    if (!args || typeof args !== "object") args = tc?.args && typeof tc.args === "object" ? tc.args : null;
    if (args) o.tool_input = args;
  }
  if (!str(o.session_id)) o.session_id = str(p.sessionId) ?? str(p.conversationId) ?? str(p.conversation_id) ?? str(env.GEMINI_SESSION_ID) ?? (agent ? `${agent}-session` : "");
  if (!str(o.cwd)) { const c = str(p.workdir) ?? str(p.workspacePaths?.[0]) ?? str(p.workspace_roots?.[0]); if (c) o.cwd = c; }
  if (agent) o.bubby_agent = agent;
  return o;
}

// ----- installers -----

const cmd = (agent, event) => `${COMMAND} --agent ${agent} ${event}`;

export const INSTALLERS = {
  gemini: {
    title: "Gemini CLI",
    file: () => path.join(os.homedir(), ".gemini", "settings.json"),
    // [Gemini event, canonical event, timeout ms]
    events: [["SessionStart", "SessionStart", 10000], ["SessionEnd", "SessionEnd", 10000], ["BeforeTool", "PreToolUse", 5000],
      ["AfterTool", "PostToolUse", 5000], ["BeforeAgent", "UserPromptSubmit", 5000], ["AfterAgent", "Stop", 5000]],
    transform(current, uninstall) {
      const out = structuredClone(current);
      if (out.hooks !== undefined && (!out.hooks || typeof out.hooks !== "object" || Array.isArray(out.hooks))) throw new Error('"hooks" в настройках Gemini CLI имеет неожиданный вид');
      const hooks = out.hooks ?? {};
      const strip = (groups) => groups
        .map((g) => (Array.isArray(g?.hooks) ? { ...g, hooks: g.hooks.filter((h) => !isOurCommand(h?.command)) } : g))
        .filter((g) => !(Array.isArray(g?.hooks) && g.hooks.length === 0));
      for (const [ev, canon, ms] of this.events) {
        if (hooks[ev] !== undefined && !Array.isArray(hooks[ev])) throw new Error(`hooks.${ev} в настройках Gemini CLI имеет неожиданный вид`);
        const groups = strip(hooks[ev] ?? []);
        if (!uninstall) groups.push({ matcher: "*", hooks: [{ type: "command", command: cmd("gemini", canon), timeout: ms }] });
        if (groups.length) hooks[ev] = groups; else delete hooks[ev];
      }
      if (Object.keys(hooks).length) out.hooks = hooks; else delete out.hooks;
      return out;
    },
  },
  copilot: {
    title: "GitHub Copilot CLI",
    // a file of our own: removing it deletes the file
    file: () => path.join(os.homedir(), ".copilot", "hooks", "bubby.json"),
    // display only: no permissionRequest (Copilot is fail-closed there), so nothing waits on Bubby
    events: [["sessionStart", 10], ["userPromptSubmitted", 10], ["preToolUse", 10], ["postToolUse", 10], ["agentStop", 10], ["sessionEnd", 5]],
    transform(current, uninstall) {
      if (uninstall) return null;
      return { version: 1, hooks: Object.fromEntries(this.events.map(([ev, s]) => [ev, [{ type: "command", bash: cmd("copilot", ev), timeoutSec: s }]])) };
    },
  },
};

export function planAgent(agent, { uninstall = false } = {}) {
  const inst = INSTALLERS[agent];
  if (!inst) throw new Error(`неизвестный агент «${agent}». Поддерживаются: ${Object.keys(INSTALLERS).join(", ")}. Остальные: docs/AGENTS-OTHER.md`);
  const file = inst.file();
  return { ...planJsonEdit(file, (current) => inst.transform(current, uninstall)), title: inst.title };
}

export const applyAgentPlan = (plan) => applyPlan(plan);
