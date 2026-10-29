# Tasks for Claude Code from the phone

Off by default. On the computer, start the bridge with `--allow-run`:

```
node agent-bridge/bridge.mjs --allow-run --workdir ~/project/my-app --relay <relay address>
```

- `--workdir DIR`: the folder the task runs in (default: the folder the bridge was started in).
- `--claude-bin PATH`: the `claude` command, if it is not on the PATH.

On the phone, Bubby → More → Agents shows a **Task for Claude Code** card when the computer offers it (capability `run`).
Type the task and press Send; the lock screen (PIN, pattern, fingerprint) confirms it first.

How it works and what protects you:
- The bridge runs `claude -p "<task>" --output-format json` in `--workdir`, one task at a time, at most 30 minutes, task text up to 4000 characters.
- Every tool the task uses (Bash, Edit, Write ... but not Read/Glob/Grep) is asked on the phone first through a PreToolUse hook
  (`BUBBY_RUN=1`); no answer means no. The phone's Allow needs the lock screen as usual.
- The result (or the error) comes back to the card. Stop kills the task.
- Over the relay the messages are end-to-end encrypted like everything else. Only the paired phone can start a task.
