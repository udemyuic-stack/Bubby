# Agents v3: official usage numbers, questions, session details, file changes, more agents

Builds on docs/AGENTS-V2.md. Protocol stays version 1 and only grows by optional capabilities (a side that sends no `caps`
gets none of them, an unknown field or message type is ignored). Ideas and message shapes follow the open-source Coucou project
(MIT, https://github.com/louis-cfm/coucou, docs/ANDROID_LINK.md and docs/AGENTS.md); code is written for Bubby, none of
Coucou's name, character, icons, sounds or media is used.

## 0. Capabilities
`hello` (and the code-pairing `pair`-then-`hello` flow of V2) may carry `"caps":["details","answers","diffs"]`. `welcome`
carries `"caps":[...]`: the ones this bridge offers to this phone now. Bridge flags: `--no-details`, `--no-answers`,
`--no-diffs` (all on by default; the phone is the user's own paired device). Only offered capabilities may be used.

## 1. Plan usage from Claude Code's statusLine (replaces the OAuth call of V2 section 3)
Claude Code runs the user's `statusLine` command with a JSON payload on stdin; for subscription users it contains
`rate_limits.five_hour` and `rate_limits.seven_day`, each `{used_percentage: 0..100 (may exceed 100), resets_at: epoch seconds}`.
- The bridge installs (with the same diff, confirmation and `.bak` safety as the hooks, `--no-statusline` skips) a `statusLine`
  entry in `~/.claude/settings.json` that runs `node hook.mjs --statusline`. It forwards ONLY the `rate_limits` object to the
  bridge's hook socket, then runs the user's previous statusLine command (saved in `~/.bubby/statusline-previous.json`,
  with the same payload on stdin) and prints its output unchanged, so the user's own status line keeps working. If there was none,
  print nothing. Uninstalling restores the previous entry. Never block or slow Claude Code: tight timeouts, exit 0 always.
- The bridge turns the numbers into `usage.fiveHour/sevenDay` (`used` = percentage/100 clamped to 0..1, `resetsAt` = ms),
  rejects out-of-range values (epoch more than 400 days ahead means milliseconds were sent: drop it), treats a window whose
  reset time is in the past as 0 used, and keeps the last good value (with `updatedAt`) until a newer payload arrives.
- Remove the OAuth/credentials-file call entirely (the token must not be read). `plan` is then omitted unless the payload
  carries it.
- `doneToday` / `tokensToday` stay as in V2.

## 2. Answering Claude Code's questions (capability `answers`)
When Claude Code asks the user questions (the `AskUserQuestion` tool, seen through the hooks), the bridge offers them to the
phone:

| direction | `type` | fields |
|---|---|---|
| bridge to phone | `question` | `pillId`, `fingerprint`, `createdAt` (ms), `questions[]`: `{question, multiSelect, options[]: {label, description}}` |
| phone to bridge | `answer` | `fingerprint`, `picks`: one list of labels per question, in order |
| bridge to phone | `approvalResolved` | `fingerprint`: answered (here or on the computer) or timed out |

Limits: at most 4 questions of at most 8 options; question text at most 500 characters, label 120, description 300, no
duplicate labels in one question: anything outside the limits is NOT offered (it stays on the computer) because the answer
must carry exact text back. The bridge applies an `answer` only if the fingerprint is the pending question, it is younger than
115 s and `picks` matches exactly (one list per question; labels equal to that question's labels, case-sensitive; exactly one
for single choice, one or more distinct for multiple). Anything else changes nothing. Applied once. The agent receives the
answers in the same shape the Claude Code hook protocol expects for AskUserQuestion (study how Coucou's hook relay does it:
a PermissionRequest/PreToolUse hook output that supplies the answers; the bridge must still never auto-answer anything). The
phone asks for the screen lock before sending an answer. Nothing about what was picked is logged. If the phone is away, the hook
falls back to the normal terminal question (the hook never blocks Claude Code, as with approvals).

## 3. Session details (capability `details`)
Each entry of `sessions` may carry: `project` (the folder name only, at most 64 chars, never a path), `steps` (the last at most
20 steps, oldest first, each at most 200 chars; a file change is shown as its file name), `finalLine` (the agent's final line
after it stopped, at most 200 chars). A field with nothing to say is left out. The whole `sessions` line stays under 56 KiB
(drop the oldest steps first). Never sent: prompts, command output, the full answer, file contents, full paths.

## 4. File changes (capability `diffs`)
With `diffs` a session may carry `files` (at most 20, newest first): `{id, name, added, removed}` plus `tooLarge:true` and
`isNew:true` where it applies; `name` is the file name only (at most 80 chars). On request:

| direction | `type` | fields |
|---|---|---|
| phone to bridge | `getDiff` | `pillId`, `fileId` |
| bridge to phone | `diff` | `pillId`, `fileId`, `name`, `added`, `removed`, `tooLarge`, `gone`, `truncated`, `part`, `parts`, `lines` |

`lines` are `[kind, text]` with kind `+`, `-`, ` ` or `@` (hunk header `@@ <line>`); at most 200 lines per file, each cut at
400 chars, sent in parts of 100 lines (`part` 0..`parts-1`, at most 2 parts); `truncated:true` when there were more. A diff the
bridge no longer has is answered `gone:true`. At most 20 `getDiff` per 10 seconds per phone. The bridge computes the lines from
the tool input the hooks already see (Edit/MultiEdit/Write old/new strings), in memory only, forgotten when the session ends.

## 5. More agents
Any hook payload may carry `bubby_agent` (a name matching `^[a-z0-9-]{1,24}$`; absent or invalid means "Claude Code"); the
session list shows that name in `agent`. `hook.mjs --agent <name>` injects it. `node bridge.mjs --install-agent <name>` (and
`install-hooks.mjs --agent <name>`) writes the hook entries for agents whose configuration is a plain file with a documented
hook format (check which of Codex CLI, Gemini CLI, Cursor, GitHub Copilot CLI, OpenCode Coucou's docs/AGENTS.md and installer
describe, and support only those you can implement safely and test, each with diff, confirmation, `.bak`, uninstall). Add a
short docs/AGENTS-OTHER.md listing what is supported and the manual JSON recipe for everything else.
