#!/usr/bin/env node
// Adds (or with --uninstall removes) Bubby's entries: Claude Code's hooks and statusLine relay in
// ~/.claude/settings.json, or with --agent <name> another agent's hooks (lib/agents.mjs).
// Never overwrites a file blindly: prints the diff, applies only with --yes after a dated backup.
//
//   node install-hooks.mjs [--yes] [--uninstall] [--no-statusline] [--no-hooks]
//   node install-hooks.mjs --agent gemini|copilot [--yes] [--uninstall]
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import { planHooks, applyHooks } from "./lib/hooks.mjs";
import { planAgent, applyAgentPlan } from "./lib/agents.mjs";

export { HOOK_PATH, COMMAND, EVENTS, mergeSettings } from "./lib/hooks.mjs";

function main() {
  const args = process.argv.slice(2);
  const uninstall = args.includes("--uninstall");
  const i = args.indexOf("--agent");
  const agent = i >= 0 ? args[i + 1] : null;
  let plan;
  try {
    plan = agent ? planAgent(agent, { uninstall }) : planHooks({ uninstall, hooks: !args.includes("--no-hooks"), statusLine: !args.includes("--no-statusline") });
  } catch (e) { console.error(e.message); process.exit(1); }
  if (plan.note) console.log(plan.note);
  if (!plan.diff) { console.log("Изменений нет."); return; }
  console.log(plan.diff + "\n");
  if (!args.includes("--yes")) { console.log("Это только предпросмотр. Чтобы применить, добавьте --yes."); return; }
  const backup = agent ? applyAgentPlan(plan) : applyHooks(plan);
  if (backup) console.log(`Резервная копия: ${backup}`);
  console.log(uninstall ? "Хуки удалены." : "Хуки установлены.");
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) main();
