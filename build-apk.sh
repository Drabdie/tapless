#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java 17 is required. Install JDK 17 and add java to PATH." >&2
  exit 2
fi
java_version="$(java -version 2>&1 | sed -n '1s/.*version \"\([0-9]*\).*/\1/p')"
if [[ "$java_version" != "17" ]]; then
  echo "ERROR: this project is configured for JDK 17; detected Java version: ${java_version:-unknown}." >&2
  echo "Select JDK 17 in Android Studio or set JAVA_HOME to a JDK 17 installation." >&2
  exit 2
fi

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" ]]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    if [[ -d "$candidate" ]]; then SDK_DIR="$candidate"; break; fi
  done
fi
if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "ERROR: Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 2
fi
if [[ ! -d "$SDK_DIR/platforms/android-35" ]]; then
  echo "ERROR: Android SDK platform 35 is missing. Install 'platforms;android-35' in SDK Manager." >&2
  exit 2
fi

if [[ ! -f local.properties ]]; then
  printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties
fi
chmod +x ./gradlew
./gradlew --no-daemon --stacktrace :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "ERROR: Gradle completed but APK was not found at $APK" >&2
  exit 3
fi
echo "BUILD_OK: $PWD/$APK"
sha256sum "$APK"
