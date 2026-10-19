#!/usr/bin/env bash
# Быстрая сборка APK одной командой.
#   ./build-fast.sh          -> оба debug APK (Main App + Companion)
#   ./build-fast.sh main     -> только Main App
#   ./build-fast.sh release  -> release APK (R8)
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
[ -f local.properties ] || printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

MODE="${1:-all}"
case "$MODE" in
  main)    TASKS=(":app:assembleDebug") ;;
  release) TASKS=(":app:assembleRelease" ":agent-companion:assembleRelease") ;;
  *)       TASKS=("apk") ;;
esac

echo ">> ./gradlew ${TASKS[*]}"
time ./gradlew --build-cache "${TASKS[@]}"

echo
echo "== Готовые APK =="
find app/build/outputs/apk agent-companion/build/outputs/apk build/apk \
     -name '*.apk' -printf '%p  (%s bytes)\n' 2>/dev/null | sort
