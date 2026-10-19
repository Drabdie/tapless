# AI Handoff: Build AI Phone Agent APK

You are given a complete Android multi-module project. Your **only goal** is to produce a debug APK that installs on a real device.

## Project summary

- **Name:** AI Phone Agent (MVP)
- **Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room, Coroutines, OkHttp
- **Min SDK:** 26 · **Compile/Target SDK:** 35 · **JDK:** 17
- **Modules:** `app`, `core-common`, `core-ui`, `core-network`, `core-database`, `agent-accessibility`, `feature-chat`, `feature-agent`, `feature-onboarding`
- **LLM:** BYOK — OpenAI, Anthropic, Gemini, OpenRouter, Groq, Ollama
- **Key feature:** Accessibility Service drives UI actions from natural-language plans

## Success criteria

1. `./gradlew assembleDebug` exits 0
2. File exists: `app/build/outputs/apk/debug/app-debug.apk`
3. APK is installable (`adb install -r …` or sideload)

Do **not** rewrite the app. Fix only what blocks the build.

---

## Environment requirements

| Requirement | Value |
|-------------|--------|
| OS | Linux / macOS / Windows (with Android SDK) |
| JDK | **17** (not 21-only; AGP expects 17). Set `JAVA_HOME` |
| Android SDK | Platform **android-35**, Build-Tools **35.x**, Platform-Tools |
| RAM | Prefer ≥ 4 GB free for Gradle |
| Network | Needed once to resolve Maven deps |

### Install SDK (Linux example)

```bash
# JDK 17
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk unzip wget
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Command-line tools
mkdir -p $HOME/android-sdk/cmdline-tools
cd /tmp
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip -d $HOME/android-sdk/cmdline-tools
mv $HOME/android-sdk/cmdline-tools/cmdline-tools $HOME/android-sdk/cmdline-tools/latest

export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

yes | sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

### macOS

- Install Android Studio or sdkmanager
- Typical: `sdk.dir=$HOME/Library/Android/sdk`
- `JAVA_HOME=$(/usr/libexec/java_home -v 17)`

### Windows

- Install Android Studio
- Typical: `sdk.dir=C:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk`
- Use JDK 17 from Android Studio or Temurin 17

---

## Build steps (execute in order)

```bash
# 1. Unpack
unzip AIPhoneAgent-for-AI-build.zip
cd ai-phone-agent

# 2. Point to SDK (adjust path!)
echo "sdk.dir=$ANDROID_HOME" > local.properties
# or absolute path, e.g.:
# echo "sdk.dir=/home/user/android-sdk" > local.properties

# 3. Optional: more heap if OOM
# Append to gradle.properties:
# org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
# org.gradle.daemon=false

# 4. Build
chmod +x gradlew
./gradlew assembleDebug --no-daemon --stacktrace

# 5. Locate APK
ls -la app/build/outputs/apk/debug/app-debug.apk
```

**Output artifact to deliver:** `app/build/outputs/apk/debug/app-debug.apk`

---

## Common failures and fixes

### 1. `SDK location not found`
Create `local.properties` with correct `sdk.dir=...` (forward slashes even on Windows in this file).

### 2. `Invalid Gradle JDK` / Java 21 issues
Force JDK 17:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebug -Dorg.gradle.java.home=$JAVA_HOME
```

### 3. Daemon disappeared / OutOfMemoryError
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.daemon=false
org.gradle.parallel=false
kotlin.daemon.jvmargs=-Xmx768m
```

### 4. Missing `android-35` / build-tools
```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

### 5. KSP / Hilt / Compose version mismatch
Do not upgrade plugins unless compile fails. Versions in root `build.gradle.kts` and module files are intentional.

### 6. `compileSdk 35` not installed
Install platform 35; or as last resort temporarily set `compileSdk = 34` and `build-tools;34.0.0` in all modules (only if 35 unavailable).

### 7. Network / dependency resolve
Ensure access to `google()` and `mavenCentral()`. Corporate proxies may need `gradle.properties` proxy settings.

---

## After APK is built

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On device:
1. Settings → Accessibility → enable **AI Phone Agent**
2. Open app → Settings → paste LLM API key (Gemini / OpenAI / etc.)
3. Run a test command e.g. `Open Settings`

---

## Project layout (do not flatten)

```
ai-phone-agent/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew / gradlew.bat
  gradle/wrapper/
  app/
  core-common/
  core-ui/
  core-network/      # LlmClient: OpenAI, Anthropic, Gemini, OpenRouter, Groq, Ollama
  core-database/
  agent-accessibility/
  feature-chat/
  feature-agent/
  feature-onboarding/
  docs/
  README.md
```

## Constraints for the building AI

- **Do not** remove Accessibility Service or change applicationId unless required for install.
- **Do not** commit real API keys.
- Prefer fixing environment/config over rewriting business logic.
- If a single module fails to compile, fix that module’s error; avoid mass refactors.
- Deliver the APK path and a short build log summary when done.

## Minimal verification

```bash
./gradlew :app:assembleDebug
test -f app/build/outputs/apk/debug/app-debug.apk && echo BUILD_OK
```

---

## Prompt you can paste to another AI

```
Unpack the attached ai-phone-agent project. Install JDK 17 and Android SDK
platform 35 + build-tools 35. Create local.properties with sdk.dir.
Run ./gradlew assembleDebug --no-daemon. Fix only environment/config/compile
errors blocking the build. Deliver app/build/outputs/apk/debug/app-debug.apk.
Do not redesign the app. Follow AI_BUILD_INSTRUCTIONS.md in the archive.
```
