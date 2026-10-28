# Other agents on the phone

Bubby shows Claude Code sessions out of the box. Other agents can show their sessions too (their own pill on the
phone, with the same details and file changes), through `agent-bridge/hook.mjs --agent <name>`. Only Claude Code's
permission requests and questions can be answered from the phone; for every other agent Bubby is **display only** and
never answers anything for it.

## Supported installers

| Agent | Command | File written | Events |
|---|---|---|---|
| Gemini CLI | `node install-hooks.mjs --agent gemini --yes` (or `node bridge.mjs --install-agent gemini`) | merged into `~/.gemini/settings.json` | SessionStart, SessionEnd, BeforeTool, AfterTool, BeforeAgent, AfterAgent |
| GitHub Copilot CLI | `node install-hooks.mjs --agent copilot --yes` (or `node bridge.mjs --install-agent copilot`) | its own file `~/.copilot/hooks/bubby.json` | sessionStart, userPromptSubmitted, preToolUse, postToolUse, agentStop, sessionEnd |

Each shows the diff first, applies only after `y` (or `--yes`), keeps a dated `.bak-YYYYMMDD-HHMMSS` copy, never touches
somebody else's hook entries, and refuses a file that is not valid JSON. Remove with
`node install-hooks.mjs --agent <name> --uninstall --yes` (Gemini: our entries are taken out; Copilot: our file is deleted).
The hook prints `{}` ("no opinion") for both, exits 0 at once, and Copilot's `permissionRequest` is not installed, so
nothing ever waits on Bubby.

Not installed automatically: Codex CLI, Cursor, OpenCode, Amp and the rest, because their hook formats are either a code
plugin rather than a plain file, or not stable/documented enough to edit safely. Use the recipe below.

## Manual recipe (any agent that can run a command per event)

Run this command from your agent's hooks, with the event name as the last argument (Claude Code's names, or the
agent's own names that `lib/agents.mjs` maps: Gemini's `BeforeTool`..., Copilot's camelCase, snake_case):

```sh
node /path/to/agent-bridge/hook.mjs --agent my-tool PreToolUse
```

- `my-tool` must match `^[a-z0-9-]{1,24}$`; anything else is shown as Claude Code.
- The hook reads the event JSON on stdin. It understands Claude Code's fields (`session_id`, `tool_name`, `tool_input`,
  `cwd`, `last_assistant_message`) and the common variants (`sessionId`, `conversationId`, `toolName`, `toolArgs`,
  `toolCall.name/args`, `workdir`, `workspacePaths`).
- Useful events: `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse` (with Edit/MultiEdit/Write-style input
  `file_path` + `old_string`/`new_string` or `content`, for the file changes), `Stop`, `SessionEnd`.

Or, without `--agent`, put the field `bubby_agent` into a Claude Code-shaped payload:

```sh
echo '{"hook_event_name":"UserPromptSubmit","session_id":"t1","bubby_agent":"demo"}' | node agent-bridge/hook.mjs
```

A "demo" pill appears on the phone (pill id `agent_demo`).
