# QA matrix (test/qa_matrix_test.dart)

Every screen is pumped with a fake `buddy/native` channel across
3 languages (ru/uz/en) x light/dark x text scale 1.0/1.5 x width 320/411 dp = 24 combinations
per scenario, 20 scenarios (480 pumps): home (onboarding + running), tasks (0/1/30), calendar,
outfits, fun, music, model (idle + downloading), agents (paired/unpaired), AI setup (0 conns,
3 conns, 3 conns + failing test), more, permissions list, settings, setup.

Asserted per combination: no exceptions, no RenderFlex overflow (FlutterError.onError is captured
and the creator file:line is reported), no Cyrillic in Text/RichText for en/uz (allowlist: fake
user data and the trilingual "Язык / Language / Til" label), and every button / IconButton /
ListTile / standalone Switch is >= 44x44. A separate test switches `appLang` at runtime
(ru -> en -> uz) on Home/More/Tasks and checks texts change and stay translated.

Debug: `flutter test test/qa_matrix_test.dart --dart-define=QA_VERBOSE=true` prints each full error.

Known gaps
- Animations are disabled (MediaQuery.disableAnimations); mid-animation frames are not checked.
- Dialogs, bottom sheets and expanded setup steps (after taps) are not opened.
- Switches inside a ListTile are exempt (the whole tile is the target).
- Height is fixed at 800 dp; landscape and tablets are not covered.
- Open defects in files owned by another agent (matrix fails on them): setup_screen.dart:17
  (AppBar action "All permissions" overflows), setup_screen.dart:390 (permission card Row overflows),
  settings_screen.dart:445 (language DropdownButtonFormField overflows at 320 dp).
