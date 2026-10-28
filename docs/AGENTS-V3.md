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

## 6. Clarifications from the bridge implementation (agent-bridge)
These pin down what the spec above left open; the bridge does exactly this.
- **Caps on pairing.** `pair` may carry `caps` like `hello`, and `paired` then carries `caps` like `welcome` (for a phone
  that keeps the pairing connection). `welcome` and `paired` always carry `caps`, `[]` when nothing is offered. Unknown
  names in the phone's list are ignored. Offered order: `details`, `answers`, `diffs`.
- **statusLine.** The relay sends the bridge `{"event":"StatusLine","sessionId":...,"rateLimits":<rate_limits as is>}`;
  nothing else of the payload. The previous status line (saved whole, `padding` etc. kept in settings.json) runs through
  `/bin/sh -c` (Git Bash `bash -c` on Windows) with a 5 s budget and 64 KiB of output, then is killed and prints nothing.
  A `statusLine` that is not an object is left alone. `used_percentage` must be a number >= 0 (above 100 is clamped to
  `used: 1`); a bad `resets_at` drops only `resetsAt`. The last good values persist in `~/.bubby/limits.json` across
  restarts. `usage.updatedAt` is the time of those values; with none it is the send time. `plan` comes only from
  `rate_limits.plan` (letters, digits, space, `_`, `-`, at most 20, lowercased).
- **doneToday** counts Claude Code's turns only (`Stop` without `bubby_agent`).
- **Questions.** Also not offered: two questions with the same text (Claude Code's answers are keyed by the text), a
  non-boolean `multiSelect`, an empty label. `description` is always sent (`""` when absent). The answer goes back as
  `{question text: label}` and, for a multiple choice, `{question text: [labels]}` in the phone's order. A `decision`
  for a question and an `answer` for an approval are ignored. Questions are only offered while a phone with `answers`
  is connected; they come only from Claude Code (`tool_name` `AskUserQuestion` on `PermissionRequest`).
- **Details.** `project` is the session's `cwd` folder name. Steps: Bash = the command on one line, a file tool = the
  file name, Read = `Read <name>`, other tools = the tool name. `finalLine` = the first paragraph of the Stop hook's
  `last_assistant_message`, without Markdown markers, one line, at most 200 chars; cleared on the next prompt. One pill
  per agent: its entry shows the most urgent of its sessions; `steps`/`files` cover all of them.
- **Diffs.** `id` / `fileId` are positive integers, unique per bridge run. Edits of the same file in a session add up
  in one entry (same id, moved to the front). The `@` line text is `@@ <n>` with `n` the 1-based line in the edited
  text (the tool input), not in the file. A diff over 200 KB / 4000 lines is `tooLarge` with counts only. An unknown or
  forgotten file is answered `{"type":"diff",...,"gone":true,"part":0,"parts":1,"lines":[]}` with `name:""`, counts 0.
  Over the rate, `getDiff` is dropped without an answer.
- **More agents.** Pill id `agent_<name>` (Claude Code keeps `integration_claude`), `agent` = the name as given. Another
  agent's `PermissionRequest` is never sent to the phone and gets no decision (display only). Installers exist for
  Gemini CLI and GitHub Copilot CLI only; see docs/AGENTS-OTHER.md.

## 7. Phone side notes (clarifications, keep the bridge in line with them)
- File `id`: a whole number (0 to 2^53) is preferred, as in Coucou; a short string (at most 64 printable characters) is also
  accepted. The phone sends `getDiff.fileId` back with the same JSON type it received. A file with another kind of id, or
  with an empty `name` after taking the last path segment, is dropped.
- The phone reads lines of up to 256 KiB (a diff part of 100 lines of 400 characters can pass 64 KiB once escaped) and skips
  a longer line instead of dropping the connection. Old phones never get `diff` lines because they send no `caps`.
- Everything the bridge sends is drawn as plain text after removing control and format characters (bidi overrides,
  zero-width), with the lengths above; nothing is parsed as HTML or styled text. A `question` whose labels are equal once
  cleaned counts as having duplicate labels and is dropped (it stays on the computer). Labels go back exactly as received.
- A `question` is shown on the Agents screen and on the floating mascot (with its text hidden while the phone is locked). It
  expires on the phone 115 s after `createdAt` (a bridge clock ahead of the phone counts as now). "On the computer" only hides
  it on the phone; nothing is sent.
- `diff` messages that break the limits (more than 2 parts, `part` out of range, other ids) are ignored; a part with more
  than 100 lines is cut and shown as truncated. The phone itself also keeps at most 20 `getDiff` per 10 s.
