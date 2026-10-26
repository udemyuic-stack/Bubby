# Bubby

Bubby is a voice-controlled floating mascot assistant for Android. It lives on top of your screen,
listens when you tap it or call its name, and controls the phone by voice: alarms and timers, calls,
messages, music and video, translation, search, tasks and calendar, screen tapping/typing, stickers and more.
The app speaks English, Uzbek and Russian.

Every user connects their **own free AI key** (Groq first; OpenRouter, Gemini, Anthropic or a custom/Ollama
server also work) and Bubby falls back automatically between connections. Keys stay on the phone.

## Layout
- `app/` – the Flutter app (UI) plus the native Kotlin overlay service (`android/app/src/main/kotlin/com/example/buddy/`).
- `agent-bridge/` – Node bridge that lets you approve Claude Code permission requests from the phone.
- `ollama-gateway/` – token-protected proxy for a local Ollama (optional).
- `app/qa-jvm/` – JVM tests for the offline command grammar and contact matching.
- `CONTRACT.md` – the interface contract used while the parts were built in parallel.
- `docs/GROQ-KEY-GUIDE.md` – user guide for getting a free Groq key (uz/ru).

## Build
```
export JAVA_HOME=<JDK 17 with jlink> ANDROID_HOME=<Android SDK>
cd app
flutter build apk --release --target-platform android-arm64   # 64-bit phones (default ABI)
flutter build apk --release --target-platform android-arm     # 32-bit phones
```
Each APK contains one ABI only. The `Build APK` GitHub Actions workflow builds both and uploads them as artifacts.
Tests: `flutter test` (widget matrix: 3 languages x light/dark x text scale x screen width) and `app/qa-jvm/run.sh`.

## Notes
- The character art is drawn in code; no third-party artwork is used. The agent-bridge protocol idea comes from the
  open-source Coucou project (MIT); none of its assets (character, icon, sounds) are used here.
- No license has been chosen yet.
