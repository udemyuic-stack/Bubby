# Bubby as a phone agent

Bubby can carry out a task that needs several steps on the phone from one instruction ("open Settings and turn on
dark mode", "find the last photo I downloaded", "search for flights to Istanbul in the browser and tell me the
cheapest"). It works like a computer-use agent: it looks at the screen, acts, looks again, and stops with a spoken
summary. This page describes how it is built, what it can and cannot do, and the safety rules.

## Structure

| File | Role | Android? |
|---|---|---|
| `ToolRegistry.kt` | The single list of tools: name, description, parameter schema, risk (`READ` / `ACT` / `SENSITIVE`), executor (`ACTION` / `OBSERVE` / `DEVICE` / `LOOP`), group, progress label. Generates the native tool schemas (Anthropic, OpenAI-compatible, Gemini) and the action block of the legacy JSON mode. | no |
| `ProviderFormat.kt` | Neutral transcript (`Msg`, `ToolCall`, `ToolResult` with optional screenshot) to and from each provider's wire format. | no |
| `AgentLoop.kt` | The budgeted loop, loop detection, run log. Talks to the model through `ModelClient` and to the phone through `AgentHost`. | no |
| `Safety.kt` | Privacy lists, untrusted-data wrapping, the injection gate, risk of the element under a tap, approvals. | no |
| `ScreenDescriber.kt` | Turns the accessibility tree into the compact numbered screen description. | no |
| `Brain.kt` | Prompts, tool groups, the providers (HTTP, fallback between connections, tool-calling fallback), observations. | yes |
| `PhoneAgent.kt` | `PhoneHost` (the `AgentHost` of the service), the device tools, `RunHistory`. | yes |
| `BuddyAccess.kt` | Accessibility service: tree walk, taps, gestures, screenshots, consent. | yes |
| `BuddyService.kt` | Progress on the island, the Stop chip, "stop" by voice, approval cards. | yes |
| `ui/RunsScreen.kt` | "What Bubby did": the last 5 runs, step by step. | yes |

The pure files compile on the JVM and are tested in `app/qa-jvm` with a scripted fake model and a fake phone.

## One request

1. Offline commands (`QuickIntent`) are handled first, exactly as before: no model, no network.
2. `Brain.ask` picks the tool groups the request needs (`core` always; `screen`, `phone`, `data`, `organizer`,
   `mascot` by keywords; the model can add a group with `more_tools`), builds the two system prompts (native and
   legacy JSON) and runs `AgentLoop`.
3. Each turn goes to the first working connection. If it supports tool calling the request carries the tool schemas;
   otherwise (or when tool calling was rejected) it uses the legacy JSON reply `{emotion, lang, say, actions, more}`.
   Both modes run through the same loop.
4. A turn that ends the task (`done(summary)` or a plain answer in tool mode, `"more": false` in JSON mode) returns its
   actions to the service, which runs them as before (honest error messages, inline stickers, message countdown).
   Any other turn is a step: observations are answered, actions run through `PhoneHost`, the screen is read again and
   fed back, and the model is asked again.

## Providers

| Provider | Tool calling | Images (`look`) |
|---|---|---|
| Anthropic (`sk-ant…`) | `tools` / `tool_use` / `tool_result`; cache breakpoint on the tools; raw content (thinking blocks) replayed unchanged, the transcript is append-only | yes |
| Groq, OpenRouter, custom/Ollama (`https://host\|model\|token`) | `tools` + `tool_calls`; `<tool_call>` markup in text is also understood | by model name (llama-4, *-vl, gemma3/4, gpt-4o...), remembered when refused |
| Gemini (`AIza…`) | `functionDeclarations`; thought signatures sent back | yes |

Fallbacks: a rejected tool request is answered in JSON mode at once; when the error is about tools (or the model
produced malformed calls twice in a run) the connection is remembered as "JSON mode" for 7 days. Settings > "Smart AI
tools" switches tool calling off for all connections. The connection fallback on 401/402/403/404/408/429/5xx, the
60 s block after a 429, Gemini model rotation, Groq `reasoning_effort` and the Uzbek-first Qwen order are unchanged.

## The loop

- Budget: 30 steps and 4 minutes (`Brain.AGENT_MAX_STEPS`, `Brain.AGENT_MAX_MS`, `AgentConfig`). Near the end the
  model is told how many steps are left; at the limit Bubby stops and says what it managed.
- `done(summary)` is the explicit finish; `plan(text)` is a scratchpad (shown in the run log, not to the user);
  `wait(seconds)` waits up to 10 s; `more_tools(group)` loads more tools.
- After the actions of a step the loop waits for the screen to settle and sends the new screen description with the
  results ("screen changed / UNCHANGED since last step").
- Loop detection: the same action on an unchanged screen (or failing again) 3 times adds a warning to change approach;
  5 times stops the run ("I got stuck…").
- Errors: a failed action ends the step; the model gets the error and the current screen and is told to try another
  way, then to tell the user.
- Cancel: tapping the mascot, the Stop chip, or saying "stop / стоп / to'xta" ends the run before the next step.
- Progress: every step shows a short label on the island ("Opening Settings", "Tapping «Wi-Fi»"); the final summary is
  spoken and shown.

## Eyes

`screen_text` (and the feedback after each step) is a compact description:

```
App: com.android.settings (SubSettings) | screen 1080x2400 | screen changed since last step
[1] text "Network & internet"
[2] edit hint="Search settings" #search_src_text [focused]
  [3] button "Wi-Fi · Connected"
  [4] switch "Bluetooth" [off]
  … 12 more items in this list (scroll to see them)
[5] button #more_options @1010,120
```

Numbers belong to the latest description; `tap_node` refuses a number whose element has disappeared. Clickable rows are
named after the texts inside them, long lists are collapsed, plain texts are dropped first when the screen is too big
(3600 characters / 70 elements), coordinates are shown only for elements without a label.

`look` takes a screenshot (Android 11+, accessibility capability `canTakeScreenshot`), scales it to 1024 px on the long
side and sends it as an image to providers that accept images. A black frame (FLAG_SECURE) is reported; providers
without vision get a text note instead. At most 6 screenshots per task.

## Hands

Original actions (all still listed in JSON mode): alarms, timers, search, music, video, media, volume, flashlight,
apps, links, maps, calls, messages, email drafts, stickers, settings panels, tap/type/scroll/back/home, tasks,
calendar, lists, outfits, mascot control, games...

New tools: `look`, `tap_xy` (screen or screenshot pixels), `long_press`, `double_tap`, `swipe`, `drag`,
`scroll_to_text`, `select_all`, `copy`, `paste`, `clipboard_get`, `clipboard_set`, `share_text`, `open_intent`
(view https URL, dial without calling, SMS/email drafts, settings pages, maps/navigation, Play Store page),
`toggle` (Wi-Fi, Bluetooth, airplane, mobile data, location, NFC, hotspot: opens the right panel, the agent taps the
switch, because Android 10+ does not let apps flip them), `contacts_search`, `sms_read`, `files_find`, `app_list`,
`app_info`. Every tool that needs a permission says so when it is missing.

## Safety

- Risk classes come from the registry. `READ` and `ACT` run freely after the gates below; `SENSITIVE` (sending
  messages, calls, declining a call with an SMS, auto-replies, notification replies, sharing, deleting tasks, clearing
  lists) always shows Bubby's approval card with the exact action ("Send a message — To: Alisher (sms) «hi»").
- The element under a tap decides too: Pay/Buy/Checkout, Delete/Erase/Reset, Install/Uninstall/Update, passwords and
  security settings, permission dialogs (Allow) need the approval card **and** the lock-screen check
  (`ConfirmActivity`); Send/Call buttons and Enter in a messenger need the approval card. If the user's own words did
  not ask for that kind of thing (pay, delete, install...), the action is refused and the model must ask the user.
- Untrusted data: screen text, notifications, web results, clipboard, files, contacts, SMS and screenshots are wrapped
  in `<untrusted source="…">` (tags inside are neutralised) and every prompt says they are data, not instructions.
  The old injection gate is kept and extended: after any observation, sensitive actions run only when the user's own
  words ask for them; a request shared from another app never triggers them.
- Never inside banking, payment or password apps (package list in `Safety.kt`): no screen text, no screenshot, no
  taps or typing (back/home stay allowed). Screens showing card details are hidden even in a browser.
- Passwords and card numbers are typed only when the user dictated them in the request.
- Screen reading needs the user's yes once per task (setting "Ask before reading the screen"), unless the request
  itself talks about the screen. SMS, files and clipboard need the same kind of yes unless the request asks for them;
  SMS reading also needs the Settings switch and the permission, and hides codes and bank messages.
- Run log: Home > last task card, More > "What Bubby did". It stores tool names, short arguments and results; never
  screen contents, typed text or keys.

## Limits (what it still cannot do)

- Flip Wi-Fi, Bluetooth, airplane mode or mobile data directly: it opens the panel and taps the switch itself only
  when screen control is on.
- Anything inside banking/payment/password apps, CAPTCHAs, payments without the screen lock, and typing secrets.
- Apps that draw everything on a canvas (games, some maps) expose no accessibility tree: it needs `look` and a vision
  model, and taps by coordinates are less reliable.
- Screenshots need Android 11+; the accessibility capability may require turning the service off and on once after
  the update.
- Reading other apps' files in Downloads is limited by Android (media files and its own files only).
- Results of actions that finish asynchronously (opening an app, resolving a contact) are seen through the next screen,
  not as an error value.
- Long tasks cost model calls: each step is one request (free plans may hit their daily limits sooner).

## Tests

`app/qa-jvm/run.sh` compiles the pure sources with the tests and runs them. `AgentTest.kt` covers schema generation
for all three providers, the legacy block, transcript conversion, JSON-mode fallback, malformed calls, loop detection,
step and time budgets, cancellation, approvals, prompt injection through the screen, risky taps, passwords, private
apps, screen-description compaction and the run log.
