# Status: what was measured and what is still wrong

Written from the CI runs of branch `claude/zen-thompson-qfzrjr`. "Not measured" means nobody has tried it on a real phone yet.

## Measured
| What | Result | Where |
|---|---|---|
| Native animated background (all 10 looks) | 29-57 fps before the 30 fps cap (now capped at 30 on purpose, half the work) on the slow CI emulator (API 34, software GPU); the old WebView background gave 0-13 fps there | CI log line `Perf: native scene N fps` |
| Frame-rate guard | two reports under 22 fps in a row switch the background off (Settings > Style turns it on again) | `ui/Perf.kt` |
| Match-3 rules | 697 checks pass (fill, runs, specials, dust, fuzzed games) | `app/qa-jvm/Match3Test.kt` |
| Agent bridge | 97 tests pass, including the transcript watcher | `agent-bridge/test` |
| Whole-app type check | no errors | local `check.sh` and CI build |

## Known shortcomings
- **Real phone speed: not measured.** Only emulator numbers exist. The guard is the safety net, not a proof.
- **Native scenes follow the HTML mockups approximately.** Soft blurred effects are pre-rendered or replaced by gradients. Which look differs most from its mockup: not compared side by side yet.
- **Native home** (HomeScreen without WebView) approximates the mockups; the WebView home remains only as a fallback for a look without a native scene (none left today).
- **Match-3 board animation** was rewritten (cached piece bitmaps) and was not re-checked on the emulator afterwards; the CI emulator sometimes dies before the live screenshot.
- **Claude Code live view.** Hooks are read by Claude Code when a session starts, so a session that was already running when the bridge was installed stayed silent. The bridge now also follows sessions through `~/.claude/projects/*/*.jsonl` (state, tool in use, project folder; `--no-transcripts` turns it off). Needs the new bridge on the computer and the new APK; permission requests and questions still come only from hooks (a session started before the hooks were installed can show its state but cannot ask the phone for approval).
- **Leftovers not ported:** small Settings/More mockup effects, delete-slide, outfit emoji drop, lazy lists, status-bar icon contrast on pale looks, bottom cards on short screens, Settings header accent bars.
- **Code cleanliness:** several large files (`HomeScreen.kt`, `MatchBoardView.kt`, scene files of 400-600 lines) should be split; not started.
- **Emulator flakes:** "device offline" and Google-services kills happen in CI. They are warnings, not failures; an app crash (`FATAL EXCEPTION`) still fails the job.
