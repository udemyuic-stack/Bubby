#!/usr/bin/env bash
# Runs inside the emulator: first the old launch check, then screenshots of the new Kotlin UI.
bash .github/smoke.sh
result=$?
bash .github/ui-shots.sh || true
exit $result
