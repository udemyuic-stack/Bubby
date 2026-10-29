# Spec: animated backgrounds, speed and UI/UX defects found by using the app on a phone

Status: DRAFT, awaiting approval. No code changed yet.

## Evidence (measured on the phone, 2026-10-29)

Device: Samsung Galaxy A13 (SM-A135F), 3.7 GB RAM with 1.9 GB swap in use, WebView 155.0.8059.46, Halloween look, home tab, assistant idle.

`dumpsys gfxinfo com.example.buddy` after 8 s on screen:

- 157 frames, 100% janky, 50th percentile 150 ms, 90th 200 ms (about 5-6 fps).
- GPU time per frame only 10-18 ms. Not GPU-bound.
- Top thread: RenderThread 54% CPU, `mali-cmar-backe` 23%, VizWebView 14%, Chrome_InProcGp 11%. App total 152% CPU while idle.
- WebView runs in-process (`Chrome_InProcGp`, `VizWebView` are inside the app process), so web compositing and native drawing share one RenderThread.
- `Perf` never logged an fps line, so the existing auto-fallback (switch the animated background off below 22 fps) did not trigger. At 5 fps it should have.

## Findings, most severe first

### Blocking

1. Home page is a full mockup, not a lightweight scene. `assets/home/<look>.html` is 88-142 KB, 17-34 KB of JS, 47-61 `.animate()` calls, 16-57 infinite CSS animations, up to 5 rAF loops (`ocean`) and a per-page `setInterval`. All of it runs permanently, including parts that are off screen or covered.
2. `backdrop-filter` on many layers over an animating scene. `home/space.html` has about 29 `backdrop-filter` declarations (blur 5-26 px); the visible home has petals, cards, pills and bubble all blurring what is behind them. Every frame of the scenery underneath invalidates every blur. On a Mali-G52 this is the dominant cost. `scene/*.html` also still carries the same dead CSS (the elements are removed, the rules are not).
3. Large `filter: blur()` and SVG `feGaussianBlur` on animated elements (`blur(14px)`, `blur(26px)`, `stdDeviation` 14-22 on big shapes in `space`, `forest`, `halloween`, `ice`, `blue`). Filtered layers re-rasterize on every animated frame, nothing is promoted (`will-change` count is 0 in all pages).
4. `Perf` fallback is broken in practice. Probe starts 3 s after `onPageFinished` and needs two slow reports in a row; the home page and the scene are two reporters, a fast one resets the counter (`slow = 0`). Also `done`/`slow` are process-wide and ignore a page that is hidden (fps 0).

### Reliability risk

5. `setLayerType(LAYER_TYPE_HARDWARE, null)` on both WebViews adds an extra offscreen texture per view; WebView already composites itself. Costs memory on a swapping 3.7 GB phone.
6. Canvas loops (`forest` rain 140 drops, `newyear` snow) clear and redraw a full `414*868*DPR` canvas every frame with no frame cap.
7. Both WebViews are alive at once on non-home tabs only when the scene is shown, but the home WebView is kept running (`restWeb()` only on pause) when another tab is on top.

### Nits

8. `devicePixelRatio` cap is 2 in canvases but CSS layers are rasterized at full device scale.
9. `prefers-reduced-motion` is honoured by some JS (`RM`) but the app never sets it from a low-end signal.

## Goal

Home tab and other tabs hold at least 30 fps (target 45+) on the A13 for every look with animated background on, with no visible loss on strong phones.

## Plan (in order, each step measured with gfxinfo before the next)

1. Measurement harness: script `adb shell dumpsys gfxinfo` after `reset`, 10 s per look, print janky %, p50/p90. Record a baseline table for all looks on the A13. Do this first.
2. Cheap wins, no visual change:
   - drop `LAYER_TYPE_HARDWARE` on both WebViews;
   - strip dead CSS and unused DOM from `scene/*.html`;
   - add `will-change: transform` / `contain: layout paint` to large animated layers; pause (`animation-play-state: paused`) layers that are off screen or covered by the sub screen;
   - cap canvas loops to 30 fps and DPR to 1 on low-end.
3. Replace `backdrop-filter` on the home page with a static translucent fill (same gradients, no blur) when the device is low-end; keep real blur on strong phones. Detect low-end with `ActivityManager.isLowRamDevice()` / memory class / `Build.VERSION.MEDIA_PERFORMANCE_CLASS`, pass as `?lite=1` to the page (same pattern as `?nosplash=1`).
4. Lite mode also: no `filter:blur()` / `feGaussianBlur` on animated elements (pre-baked radial gradients instead), fewer rAF particles, no `setInterval` work while the page is not visible.
5. Fix `Perf`: count each reporter separately, threshold 22 fps stays, first probe at 2 s, ignore only fps 0, and on a hit choose the cheaper step first (lite mode) before turning the background off completely.
6. Re-measure all looks. Acceptance below.

## Acceptance

- A13, every look, home tab, idle 10 s: janky frames under 25%, p90 under 33 ms.
- Strong phone / emulator: visual diff against current build is none or negligible in normal mode.
- `Perf` fallback logs and triggers at forced 5 fps in a test.
- Scene and home page resting while app is paused: RenderThread under 5% CPU.

## Out of scope

New looks, redesign, changing native screens, game screens.

## Files expected to change

`app/src/main/assets/home/*.html`, `app/src/main/assets/scene/*.html`, `ui/SceneView.kt`, `ui/HomeWebView.kt`, `ui/Perf.kt`, `ui/Glass.kt` (low-end flag), one measurement script under `docs/` or `scripts/`.

## Open questions

- Is lite mode (no blur) acceptable visually on weak phones, or do you prefer the animated background auto-off on them?
- Per-look baseline: want me to cycle all looks on the A13 before touching code (about 10 min)?
- `specs/constitution.md` is missing; want me to create one?


---

# Part 2. Defects found by driving the real app

Method: app installed on Samsung Galaxy A13 (SM-A135F, 1080x2408), Halloween look, English, animated background ON. Opened every tab through the bottom bar, took a screenshot and a 6 s `gfxinfo` window per screen, `adb` build as installed on the phone at HEAD `55b1edc`. Only the Halloween look was exercised; the other nine looks are NOT yet checked (see "Not covered").

## A. Measured speed per screen (6 s window each)

| Screen | Janky | p50 | p90 | Verdict |
|---|---|---|---|---|
| Home (first run) | 100% | 150 ms | 200 ms | unusable, 5-6 fps |
| Home (second run) | 100% | 150 ms | 200 ms | same |
| Home (after other tabs) | 69.5% | 57 ms | 85 ms | bad |
| Tasks | 56-60% | 44-46 ms | 69-73 ms | bad |
| Calendar | 92.6% | 73 ms (150 ms in first run) | 109-300 ms | worst native tab |
| Settings | 90% | 69 ms | 97 ms | bad |
| More | 63.7% | 53 ms | 73 ms | bad |

Logcat: `Choreographer: Skipped 35 / 45 frames! The application may be doing too much work on its main thread.` while switching tabs.
Even Tasks (one card, no web page on top) is at 46 ms p50, so the scene WebView behind native screens already costs more than 16 ms by itself. The home page is 3x worse than that.
Variance between runs is large (Home 57 ms vs 150 ms), consistent with thermal/memory pressure: swap in use 1.9 GB.

## B. Visual and animation defects (each: where, what is seen, expected)

HOME
1. **Grey rectangle artifact** at top-left, about x 75-150 px, y 115-180 px (screenshot coordinates of 897 px width), right under the spider. A hard-edged grey/white square is visible next to the spider on the first and the later run. Expected: nothing. Likely a blurred or `filter` layer with an opaque box (same family as defect 6).
2. **"Stop" tile uses a play icon (triangle)**. The tile is labelled `Stop` but draws the same triangle as a Play button. Expected: a stop square when it stops, play only when it starts. (Confirm in code which state the tile shows; label and icon disagree on screen.)
3. **Petal tiles overlap each other and clip text.** `Test: hello` is cut by the `Stop` tile (tile bottoms overlap by about 40 px) and by a cobweb overlay drawn over the text. `Tasks`, `Calendar`, `Settings`, `More` overlap in pairs. Text sits under the overlapping tile edge. Expected: no tile covers another tile's label.
4. **Spider-web decoration drawn on top of labels** (`Test:` / `hello`, `Tasks`, `More`). Decoration must be below text.
5. **Cards cut by the system navigation bar.** The `Last task: start` card (`steps: 2 · done · see what Bubby did`) and the next card run under the Android navigation bar; the second card's text is covered. No bottom inset / padding on the home scroll content. Seen on every home screenshot.
6. **Dark, hard-edged blurry patches in the sky** (dark purple blob top centre, orange blob top right) show a visible edge instead of a soft fall-off. Large `filter: blur()` layers are clipped by their own box. This is also the speed cost (see Part 1, finding 3).
7. **Status line text unreadable**: `STATUS` and `WHAT YOU CAN SAY` section titles (bottom of home) are drawn over dark tree silhouettes, contrast clearly below 4.5:1.
8. **Overlay chips cut off**: `Assistant is on` pill has a clipped right edge (small notch at the right).
9. **Glow orb (white circle) with a thin curved line through it** in the middle of the tiles, no label, no meaning, sits under the tiles. Looks like a leftover decoration, not content.

CALENDAR
10. **Pumpkin has a square glow box**: a visible rectangular darker halo with hard corners around the pumpkin illustration (shown at x 190-640, y 580-1000). Expected: round soft glow. Same family as defect 1 and 6.
11. **Title overlapped by decoration**: the spider crawls over the `C` of `Calendar`; the title is a light font on light background (`Calendar`, `Settings`, `Tasks`, `More` titles are thin and low contrast against the pale top of the Halloween gradient).
12. **Date chips on the day strip have a clipped/blurry bottom edge**: the bottom of the egg shapes fades into a darker band, and the bat flies in front of `Fr 30`/`Sa 31`. Today (`Th 29`) is not visibly different from the other days: no selected / today state.
13. **FAB `+ Event` and tab bar both sit over the trees, `+ Event` is dark brown on a brown tree**: low separation, no shadow ring. Expected: clear contrast against any background.

TASKS
14. **Task row**: bell icon is dark, circle checkbox is thin and low contrast (about 1.5:1 against the cream card). Expected: 3:1 for the non-text control.
15. **Spider/bat overlap the `Tasks` title** (same as 11).
16. **Empty area under the list**: large empty space with decorative ghosts; no "add your first task" hint besides the FAB. (Only one task exists, so this is just the sparse state.)
17. `Long press to delete` hint is the only way to delete: no visible delete or swipe affordance, not discoverable. UX.

SETTINGS
18. **Page opens already scrolled / content under the tab bar**: `Style` section header and the first look tiles (`Cube`, `Mochi`) sit under the floating bottom bar and are cut, same inset problem as defect 5.
19. **Animated background switch** helper text says `Turn off to save battery` but the real reason users need it is speed: no warning that it costs frames; no "performance" mention. The switch handle is cream on dark brown, state is readable, OK.
20. The `Language` chips: `Auto (as I speak)` selected state is almost the same colour as the unselected chips (pale cream with a slightly lighter border). Selected state barely visible. Button colour issue requested by the user.
21. `Manage connections` button (dark brown, white text) and `AI connections: 3` card (orange fill, dark text) use two unrelated colour systems on one card. The orange card looks like a button but is not.

MORE
22. **List rows have icons in 5 unrelated saturated colours** (orange key, purple bolt, green note, red face, orange play) on a pastel Halloween look: they do not follow the look's palette. They look like the default theme leaking through.
23. **Second half of the page has no card background**: `Stay awake: on` (green bar) and `What Bubby did` rows float directly on the trees with no card, text drawn over branches: unreadable (`Stay awake: on`, `Steps of the latest tasks on the phone`). The first five rows have glass cards, these two do not. Inconsistent.
24. `What Bubby did` icon tile is grey-brown, unlike every other icon tile on the page.
25. `What Bubby sends` card is cut by the tab bar mid-sentence (`Your messages go only to the AI service you chose`).

NAVIGATION / GENERAL
26. **Home has no bottom bar, other tabs do.** Pressing Back on Home exits to the launcher instead of staying (my automated Back key left the app). Expected: Back on a sub tab goes Home, Back on Home exits (this part is standard) but the screen-to-screen transition shows no slide: tab changes jump.
27. **No loading/placeholder state** while the scene and the home page load: first frame after launch is plain, then the whole background pops in (splash fallback fires 3 s later: `HomeWeb: fallback fired, splash cover left=0`).
28. **Logcat shows the splash is still `on` 3 s after start** (`HomeWeb: after 3 s splash=on`), then the fallback fires: the page is slow enough to miss its own 3 s handshake in a cold start.

## C. Not covered yet (needs a second pass)

- The other nine looks (glass, green, blue, night, space, forest, ocean, newyear, ice): colours, overlaps and fps are unknown.
- Dark mode, large text (200%), TalkBack, landscape.
- Game screens, Outfits, Agents, Music, AI connection sub screens.
- Tap feedback / press animations (cannot be judged from a still screenshot): need a screen recording (`adb shell screenrecord`) to check the spring press, sparkle, check-burst and the day bounce that the last commits added.

## D. Acceptance for Part 2

- Zero items in B reproduce on the A13 in Halloween, then on each of the nine other looks.
- Every text and every control meets 4.5:1 (text) / 3:1 (controls) over the animated background at its worst frame.
- Last card on every scrolling screen can scroll fully above the tab bar and the system navigation bar.
- Per-screen `gfxinfo` table in section A: janky under 25%, p90 under 33 ms.

## Part 3: Perfetto trace result (root cause evidence)

An 8 second trace was recorded on the Samsung A13 with Bubby in the foreground on the Calendar tab, Night look, animated background ON, so no home page WebView was visible.

CPU time per thread of the Bubby process during the 8 seconds:

- RenderThread: 4735 ms (about 59% of the wall time, the busiest thread by far)
- mali-cmar-backend (GPU driver): 1314 ms
- Chrome_InProcGp (WebView GPU thread): 950 ms
- Main thread: 729 ms
- VizWebView (WebView compositor): 622 ms

Where the RenderThread spends its time (sum over the trace):

- `drawLayer 1080 x 2408` through `WebViewFunctor::drawGl`: 3785 ms in 122 draws, up to 60 ms for a single draw. This is one full-screen WebView layer being composited on every frame. It is the SceneView WebView (transparent, `LAYER_TYPE_HARDWARE`) that sits behind every tab. It runs even when the visible screen is a native tab.
- A second layer of size 205 x 153 drawn 121 times for 1134 ms (up to 17 ms): this matches the `CollarView` software blur layer or a similar small view that redraws every frame.
- Choreographer frames took 90 to 94 ms.

Conclusion: the slowness is caused by the always-on full-screen WebView scene layer, which costs 30 to 60 ms of GPU/RenderThread time per frame on this phone, plus a small software-blurred view that invalidates every frame. This also explains why turning the animation off in the earlier measurement did not help right away: the SceneView layer stays attached and is drawn until the activity is restarted, and the native views keep invalidating. It must be re-measured after the fix, with the SceneView not attached at all.

Plan impact:

1. Do not keep a full-screen WebView composited behind native tabs. Detach it or set it to GONE, and pause the page, whenever the visible tab does not need it.
2. Replace the scene with a native Canvas or ValueAnimator layer for the cheap looks, as the project rules require for new animation work.
3. Stop the per-frame invalidation of the small blurred view (render the blur once into a bitmap).
4. Re-run the same trace after each step and compare RenderThread time.

Screenshots: `specs/screens/trace-calendar-night.png` (Calendar tab during the trace), `specs/screens/settings-voice-night.png` (Settings, voice section).

## Part 4: 3D

True 3D is not implemented in the app today. Only pseudo-3D exists: the Halloween ghost CSS (`preserve-3d`, `perspective`), the outfit preview tilt (`cameraDistance`, `rotationX/Y` in `Fx.kt`), and the memory-card flip in `GameScreen.kt`. If 3D is added later, it could fit the mascot and the home screen (a light parallax or tilt of the mascot driven by the accelerometer, native Canvas only). It must be optional and switched off on weak phones, and it must be measured on the A13 before shipping. It is not part of this spec's scope.

## Part 5: Not finished in this pass

The looks Space, Forest, Ocean, New Year and Glacier were not cycled with screenshots and fps. The games and the remaining sub-screens (Outfits, Agents, Music, AI connection, What Bubby did) were not played through. Those need a second pass after the root cause is fixed, because fps numbers are dominated by the layer above right now.

## Part 6: Look cycle results (A13, hotword ON, stay awake on)

Frame times per tab (p50 / janky share). Measurements were taken in one long sequence; the numbers rise as the sequence goes on (30 ms for Space up to 61 to 69 ms at the end), so a thermal or memory effect, or the app's own fps guard, may be part of the picture. Thermal status read 1 (light) at the end. Each look must be re-measured from a fresh start.

- Space: home 30 ms / 27%, tasks 34 / 36%, calendar 29 / 28%, more 32 / 32%, settings 31 / 33%
- Forest: 44 to 48 ms, 40 to 46% janky on all tabs
- Ocean: 36 to 42 ms, 33 to 42% janky
- New Year: 53 to 69 ms, 52 to 73% janky
- Glacier run: 61 to 69 ms, 62 to 76% janky

Caveats found while checking the screens:

- At the end of the run the Settings screen showed "Animated background" OFF and the selected look was "Rainy forest", not Glacier. So either the app's fps guard (`Perf.kt`, two slow reports in a row set `scene_anim=false` and restart the activity) switched the background off during the run, or a tap missed. The New Year and Glacier rows may therefore have been measured with the background off or on the wrong look. This is itself a defect: the guard changes a user setting on its own and only shows a short toast that is easy to miss, with no lasting marker in Settings.
- The look picker marks the active look only with a small tick in the corner.

New UI defects seen:

- Glacier home: the "Stop" tile is cut off at the top by the status bar when the page is scrolled (`specs/screens/glacier-home-stop-clipped.png`).
- Glacier home: tile labels (Tasks, Calendar, Settings, More) and the "Sound" and "Assistant is on" pills are grey on beige with low contrast, and the tiles overlap each other (`specs/screens/glacier-home-contrast.png`).
- Agents screen: the "Agents" section heading is clipped under the header and overlaps it (`specs/screens/glacier-agents-heading-overlap.png`). The line "You can allow only after the lock screen check" under the Disconnect button is unclear.
- Halloween home: a small grey rectangle shows at the top left next to the spider web (`specs/screens/halloween-home-restored.png`). The "Stop" tile shows a play triangle here but a square on the other looks.
- Glass and Glacier share the same Mochi mascot and look almost the same (`specs/screens/look-picker.png`).
- The phone screen locked during the long run and adb dropped once; stay-awake while charging was enabled for the rest of the test.

Still not done: the games, the Outfits, Music, AI connection and What Bubby did screens, and pressing every button. No destructive or outward-facing button (Disconnect, delete, send) was pressed.

## Part 7: Games and More (Halloween look, hotword ON)

Frame times in the games (p50 / janky): Bubby's room 81 to 93 ms / 95 to 98%, Guess the number 85 ms / 97%, Rock-paper-scissors 77 ms / 94%, Memory match 85 ms / 97%, 20 questions 73 ms / 90%, Riddles 69 ms / 92%, Quiz 89 ms / 96%, Word chain 105 ms / 99%. Every game is far below 60 fps; the games themselves are fine, the shared cause is the layer from Part 3 behind them.

What worked: Bubby's room accepts a valid swap (moves 10 to 9, score 30) and rejects an invalid one with a clear message ("Not allowed: a swap has to make three in a row") and a covering-eyes emoji. Memory match and the games menu render correctly.

New defects:

- Riddle island: a "Happy" pop-up with a riddle and four answer buttons stays on screen after leaving the games, on top of the Agents screen and even on top of the Samsung launcher (`specs/screens/riddle-island-over-launcher.png`, `specs/screens/island-over-header-agents.png`). It covers the status bar and the back button, and it did not go away on its own. It needs a timeout and must hide when the app goes to the background.
- Bubby's room: the mascot emoji is cut off at the top while the tiles drop after a match, and the level label disappears during the animation (`specs/screens/room-mascot-clipped-mid-match.png`).
- More screen: the "Stay awake: on" and "What Bubby did" rows have no card background, so their text sits on the trees of the Halloween background with low contrast, unlike the cards above them (`specs/screens/halloween-more-contrast.png`).
- Agents screen: "Claude 5 h, resets in 0 min, updated 8 h ago" shows stale data without any marker that it is stale.
- Back navigation: pressing Back on the Agents screen and then twice more left the app and returned to the launcher; there is no confirmation on the root screen.

Not done: Outfits, Music, AI connection and What Bubby did were not opened. Stickers ("tap to send") were not pressed because they send content to other people. The riddle answers and other outward actions were not pressed.

Defect found by accident: one tap on a sticker in "Games and stickers" opens the Android share sheet at once. The grid says "tap to send", but there is no preview or confirmation step inside Bubby, so a mis-tap leads straight to sending. The sheet was closed with Back and nothing was sent.

## Part 8: Remaining sub-screens (Halloween look)

Frame times (p50 / janky): AI connection 109 ms / 100%, Music 101 ms / 100%, Outfits ("Pet and outfits") 150 ms / 100% (the slowest screen in the app, it has the 3D tilt preview), What Bubby did 85 ms / 100%.

Defects:

- What Bubby did: the Word chain entry shows raw markup in the text: `[playful]` repeated inside the sentence ("Your turn![playful] Your word?[playful] ..."). The voice style tags leak into the stored and displayed text. Screenshot: `specs/screens/what-bubby-did-playful-tags.png`. The text should be cleaned before it is saved or shown (and before it is read aloud, if it is also spoken).
- What Bubby did: opening the Riddles game created a task entry "Let's play: Riddles" labelled "tool calling" with "1. game", so simply opening a game sends a request to the AI. This costs a call to the AI service on every game start and produced the riddle island pop-up (see Part 7).
- AI connection: three Groq connections that use the same key tail (`gsk....iaSI`) look identical except for the model name, and the red trash icon sits right next to "Check" with no confirmation text visible. Not pressed. Screenshot: `specs/screens/ai-connection.png`.
- Outfits: the preview card says "Glass (follows the look)" while the Halloween pumpkin is shown, so the name does not match the picture. The "More" button next to it only shuffles the outfit and its label does not say so. "Remove all" is a destructive action next to the preview; not pressed. Screenshot: `specs/screens/outfits-150ms.png`.
- Music: the heading "Which apps can be used" sits on the tree of the Halloween background with low contrast. Screenshot: `specs/screens/music.png`.

All screens in the app were now opened except the Quiz, Riddles, 20 questions and Word chain game rounds, which were only opened, not played to the end. No key was changed, nothing was deleted and nothing was sent.

## Part 9: Clean A/B, animated background ON against OFF (Halloween look)

Method note. The "Animated background" switch only takes effect after the app is restarted. An earlier A/B test run without a restart showed no difference and is not valid; ignore any number from it. All numbers below were taken after `am force-stop` and a fresh start, 6 to 8 s windows, `dumpsys gfxinfo`.

Animated background OFF:

- Tasks, More, Settings: 0 frames drawn in 8 s. The screen is fully idle, which is what a static screen should do.
- Calendar: 485 frames, p50 26 ms, 0.2% janky. Something on the Calendar tab animates all the time even with the background off. Needs its own look.

Animated background ON:

- Home: 108 frames, p50 150 ms, 100% janky (about 7 fps).
- Settings: p50 69 ms, 58.6% janky.
- Tasks: p50 44 ms, 8.2% janky.
- Calendar: p50 48 ms, 32.2% janky.
- More: p50 53 ms, 27.7% janky.

Conclusion: the animated background WebView is the main cost. It turns idle screens into continuous 20 to 7 fps rendering. The Home tab is the worst because the home WebView is drawn on top of the scene WebView.

Other looks, same method, Settings, Tasks, Calendar, More, Home (p50 / share of janky frames):

- Night: 77 to 89 ms, 92 to 99%.
- Glass: 73 to 77 ms, 90 to 95%.
- Green: 73 to 77 ms, 87 to 93%.

All three are far over the 16.6 ms budget on every tab, so the problem is not only the Halloween scene. Blue, Space, Rainy forest, Ocean, New Year and Glacier are being measured in a second pass and are added below when finished.

Method pitfall for the next person: on the Home tab the bottom tab bar does not exist (the radial menu is used), so a tap at the tab bar position does nothing there. Several early "tab" measurements were really the Home tab.

Not yet explained: frame times grew from 30 ms to about 70 ms during one long session in the earlier runs, but a fresh start is faster. Memory growth or WebView re-creation over time is a suspect and still has to be checked with `dumpsys meminfo` over a session.
