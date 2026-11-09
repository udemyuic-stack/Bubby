#!/bin/bash
# Compiles selected app sources + tests with kotlinc 2.4.0 (embeddable) and runs them.
set -e
export JAVA_HOME=${JAVA_HOME:-/Users/jamoliddin/dev-tools/jdk17/Contents/Home} ANDROID_HOME=${ANDROID_HOME:-/Users/jamoliddin/dev-tools/android-sdk}
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
  "$SRC/QuickIntent.kt" "$SRC/Contacts.kt" "$SRC/AppResolver.kt" "$SRC/Tr.kt" "$SRC/MathTools.kt" "$SRC/ReplyRules.kt" "$SRC/AutoAnswerRules.kt" "$SRC/CallProfiles.kt" "$SRC/Mail.kt" "$SRC/ui/GroqModels.kt" "$SRC/ui/MyWords.kt" "$SRC/ui/EnglishWords.kt" "$SRC/ui/vocab/Languages.kt" "$SRC/ui/vocab/VocabText.kt" "$SRC/ui/vocab/VocabTypes.kt" "$SRC/ui/vocab/EnrichPrompt.kt" "$SRC/ui/vocab/EnrichValidator.kt" "$SRC/ui/vocab/Enricher.kt" "$SRC/ui/vocab/LearnTypes.kt" "$SRC/ui/vocab/Scheduler.kt" "$SRC/ui/vocab/Planner.kt" "$SRC/ui/vocab/Exercises.kt" "$SRC/ui/vocab/Analytics.kt" "$SRC/ui/vocab/EnrichMerge.kt" "$SRC/ui/vocab/RetryPolicy.kt" "$SRC/ui/vocab/SourceTree.kt" "$SRC/ui/vocab/Topics.kt" "$SRC/ui/vocab/PronMatch.kt" "$SRC/ui/vocab/TutorPrompt.kt" "$SRC/ui/vocab/TutorValidator.kt" "$SRC/ui/vocab/OcrPrompt.kt" "$SRC/ui/vocab/OcrValidator.kt" "$SRC/PairProof.kt" "$SRC/AgentProto.kt" "$SRC/ToolRegistry.kt" "$SRC/ProviderFormat.kt" "$SRC/Safety.kt" "$SRC/ScreenDescriber.kt" "$SRC/AgentLoop.kt" "$HERE/TestMain.kt" "$HERE/AgentTest.kt" "$HERE/MyWordsTest.kt" "$HERE/VocabTest.kt" "$HERE/EnrichTest.kt" "$HERE/LearnTest.kt" "$HERE/ExerciseTest.kt" "$HERE/AnalyticsTest.kt" "$HERE/EnrichMergeTest.kt" "$HERE/RetryPolicyTest.kt" "$HERE/ContextTest.kt" "$HERE/SourceTest.kt" "$HERE/PronTest.kt" "$HERE/NewExerciseTest.kt" "$HERE/TutorTest.kt" "$HERE/OcrTest.kt" "$HERE/AutoAnswerTest.kt" "$HERE/CallProfilesTest.kt" "$HERE/probe.kt" 2>&1 | grep -v "^warning" || true
# the agent tests need a real org.json at run time (android.jar only has stubs): Robolectric's android-all or org.json:json
JSONJAR="${JSONJAR:-$(find $G ~/.m2 -name 'android-all*.jar' 2>/dev/null | grep -v sources | head -1)}"
[ -z "$JSONJAR" ] && JSONJAR="$(find $G ~/.m2 -name 'json-20*.jar' 2>/dev/null | grep -v sources | head -1)"
BUBBY_SRC="$SRC" java -cp "$OUT:$STD:$JSONJAR" com.example.buddy.TestMainKt | tee "$HERE/results.txt"
exit ${PIPESTATUS[0]}
