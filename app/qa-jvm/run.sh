#!/bin/bash
# Compiles selected app sources + tests with kotlinc 2.4.0 (embeddable) and runs them.
set -e
export JAVA_HOME=/Users/jamoliddin/dev-tools/jdk17/Contents/Home ANDROID_HOME=/Users/jamoliddin/dev-tools/android-sdk
export PATH=$JAVA_HOME/bin:$PATH
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../src/main/kotlin/com/example/buddy"
G=~/.gradle/caches/modules-2/files-2.1
j() { find $G -name "$1" | grep -v sources | head -1; }
CP="$(j kotlin-compiler-embeddable-2.4.0.jar):$(j kotlin-stdlib-2.4.0.jar):$(j kotlin-script-runtime-2.4.0.jar):$(j kotlin-reflect-1.6.10.jar):$(j kotlin-daemon-embeddable-2.4.0.jar):$(j trove4j-1.0.20200330.jar):$(j annotations-13.0.jar):$(j kotlinx-coroutines-core-jvm-1.8.0.jar)"
STD="$(j kotlin-stdlib-2.4.0.jar)"
OUT="$HERE/out"; rm -rf "$OUT"; mkdir -p "$OUT"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
  -classpath "$ANDROID_HOME/platforms/android-36/android.jar:$STD" -d "$OUT" \
  "$SRC/QuickIntent.kt" "$SRC/Contacts.kt" "$SRC/AppResolver.kt" "$SRC/Tr.kt" "$SRC/MathTools.kt" "$SRC/ReplyRules.kt" "$SRC/Mail.kt" "$SRC/ui/GroqModels.kt" "$HERE/TestMain.kt" "$HERE/probe.kt" 2>&1 | grep -v "^warning" || true
java -cp "$OUT:$STD" com.example.buddy.TestMainKt | tee "$HERE/results.txt"
exit ${PIPESTATUS[0]}
