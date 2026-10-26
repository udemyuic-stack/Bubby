# Moving the whole app to Kotlin

Goal: no Flutter. The settings UI becomes plain Android Views written in Kotlin, in the same module as the
always-on mascot service. Status is tracked here so the work survives interruptions.

## Decisions
- UI toolkit: framework Views built in code (like `BuddyService` already does). No Compose, no AndroidX: smallest APK.
- One Activity (`ui/UiActivity`) with a tab bar (Home, Tasks, Calendar, Settings, More) and a back stack of full-screen
  sub-screens (`ui/Navigator`).
- No MethodChannel: screens call the stores directly through `ui/Backend.kt` (extracted from `NativeChannel.kt`).
- Strings: `Tr.t(ctx, ru, en, uz)` (same three languages as the Dart `tr()` calls). Russian is the base text.
- Icons: vector drawables `res/drawable/ic_*.xml` (generated from Material path data).
- Colours: `ui/Palette.kt` mirrors `lib/theme.dart` (light and dark schemes, hour-of-day accent).
- The mascot in the app is the same `BuddyView` the overlay uses (no second copy of the drawing code).
- While Flutter still exists the new UI is a second launcher icon ("Bubby UI"). Switch-over removes `lib/`, `pubspec.*`,
  the Flutter Gradle plugin and `NativeChannel.kt`.

## Checking the work without a phone
- Kotlin type-check: compile all sources against `android-all` (Robolectric) with the Kotlin compiler.
- Visual check: the CI smoke job opens the new UI on an emulator, visits each tab, and pushes downscaled screenshots to
  the `ci-screenshots` branch.

## Screens (Dart file -> Kotlin file) and status
| Screen | Dart | Status |
|---|---|---|
| Shell, tab bar | main.dart | todo |
| Home | home_screen.dart | todo |
| Tasks | tasks_screen.dart | todo |
| Calendar | calendar_screen.dart | todo |
| Settings | settings_screen.dart | todo |
| More | more_screen.dart | todo |
| Fun | fun_screen.dart | todo |
| Agents | agents_screen.dart | todo |
| Music | music_screen.dart | todo |
| AI setup | ai_setup_screen.dart | todo |
| Pet and outfits | outfits_screen.dart | todo |
| Onboarding setup | setup_screen.dart | todo |
