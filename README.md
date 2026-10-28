# Bubby

Bubby is a voice-controlled floating mascot assistant for Android. It lives on top of your screen,
listens when you tap it or call its name, and controls the phone by voice: alarms and timers, calls,
messages, music and video, translation, search, tasks and calendar, screen tapping/typing, stickers and more.
The app speaks English, Uzbek and Russian.

Every user connects their **own free AI key** (Groq first; OpenRouter, Gemini, Anthropic or a custom/Ollama
server also work) and Bubby falls back automatically between connections. Keys stay on the phone.

## Layout
- `app/` – the Android app, plain Kotlin on the Android framework (no Flutter, no AndroidX, no third-party libraries).
  - `app/src/main/kotlin/com/example/buddy/` – the always-on overlay mascot (`BuddyService`, `BuddyView`), the AI harness
    (`Brain`, `Actions`, `QuickIntent`) and the stores.
  - `app/src/main/kotlin/com/example/buddy/ui/` – the app screens (`UiActivity` with tabs, `Screens` registry, one file per screen).
- `agent-bridge/` – Node bridge that lets you approve Claude Code permission requests from the phone.
- `ollama-gateway/` – token-protected proxy for a local Ollama (optional).
- `app/qa-jvm/` – JVM tests for the offline command grammar and contact matching.
- `CONTRACT.md` – the interface contract used while the parts were built in parallel (historical).
- `docs/GROQ-KEY-GUIDE.md` – user guide for getting a free Groq key (uz/ru).

## Build
```
export JAVA_HOME=<JDK 17> ANDROID_HOME=<Android SDK>
cd app
gradle assembleRelease        # Gradle 9.3.1; output: app/build/outputs/apk/release/app-release.apk
```
There is no native code, so the one APK runs on 32-bit ARM, 64-bit ARM and x86 phones alike.
The `Build APK` GitHub Actions workflow builds it (artifact `bubby`), installs it on an emulator, opens every screen and
pushes screenshots to the `ci-screenshots` branch. JVM tests: `app/qa-jvm/run.sh`.

## Notes
- The character art is drawn in code; no third-party artwork is used. The agent-bridge protocol idea comes from the
  open-source Coucou project (MIT); none of its assets (character, icon, sounds) are used here.
- No license has been chosen yet.

## NOTICE
- Phone side of the agents link v3 (capabilities, session details, answering questions, file diffs): the protocol ideas and
  message shapes come from Coucou's phone link (https://github.com/louis-cfm/coucou, MIT License, docs/ANDROID_LINK.md).
  The code is Bubby's own; Coucou's name, character, icons, sounds and media are not used.
