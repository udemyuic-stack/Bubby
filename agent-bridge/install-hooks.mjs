#!/usr/bin/env node
// Adds (or with --uninstall removes) Bubby's hook entries in ~/.claude/settings.json.
// Never overwrites the file blindly: prints the diff, applies only with --yes after a dated backup.
// The logic lives in lib/hooks.mjs (bridge.mjs uses it too on first run).
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import { planHooks, applyHooks } from "./lib/hooks.mjs";

export { HOOK_PATH, COMMAND, EVENTS, mergeSettings } from "./lib/hooks.mjs";

function main() {
  const args = process.argv.slice(2);
  const uninstall = args.includes("--uninstall");
  let plan;
  try { plan = planHooks({ uninstall }); } catch (e) { console.error(e.message); process.exit(1); }
  if (!plan.diff) { console.log("Изменений нет."); return; }
  console.log(plan.diff + "\n");
  if (!args.includes("--yes")) { console.log("Это только предпросмотр. Чтобы применить, добавьте --yes."); return; }
  const backup = applyHooks(plan);
  if (backup) console.log(`Резервная копия: ${backup}`);
  console.log(uninstall ? "Хуки удалены." : "Хуки установлены.");
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) main();
