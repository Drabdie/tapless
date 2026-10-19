# Как собрать APK AI Phone Agent

## Самый простой способ — автоматическая сборка в GitHub

1. Распакуйте архив проекта.
2. Загрузите папку `ai-phone-agent` в новый GitHub-репозиторий (вместе с `.github/workflows/build-apk.yml`). Не загружайте `local.properties`, ключи API или keystore.
3. Откройте вкладку **Actions** в репозитории. Запустите workflow **Build Android APKs** через **Run workflow** (либо сделайте push в `main`/`master`/`DraD`).
4. После успешного завершения откройте запуск workflow и скачайте артефакты **ai-phone-agent-debug-apk** (`app-debug.apk`) и **ai-phone-agent-companion-debug-apk** (`agent-companion-debug.apk`) внизу страницы. Отдельная job **Unit tests** прогоняет 41 юнит-тест.

Workflow самостоятельно устанавливает JDK 17 и Android SDK Platform 35 / Build Tools 35.0.0. OpenAI API-ключ для компиляции не нужен; его следует ввести в приложении после установки.

## Сборка локально через Android Studio

1. Установите Android Studio и JDK 17.
2. В SDK Manager установите **Android SDK Platform 35**, **Android SDK Build-Tools 35.0.0** и **Android SDK Platform-Tools**.
3. Откройте папку `ai-phone-agent` как проект Gradle, дождитесь синхронизации.
4. Выберите задачу Gradle `:app:assembleDebug` (Main App) и/или `:agent-companion:assembleDebug` (Companion Agent) и запустите её.
5. Готовые APK: `app/build/outputs/apk/debug/app-debug.apk` и `agent-companion/build/outputs/apk/debug/agent-companion-debug.apk`.

## Сборка локально в Linux / macOS

Требуются JDK 17, Android SDK Platform 35 и доступ к `google()`/`mavenCentral()` для скачивания Gradle и зависимостей. Укажите путь к SDK в `ANDROID_HOME` или `ANDROID_SDK_ROOT`, затем из папки проекта выполните:

```bash
chmod +x gradlew build-apk.sh build-fast.sh
./build-fast.sh          # быстро: оба debug APK одной командой (рекомендуется)
./build-fast.sh main     # только Main App — самый частый кейс
./build-apk.sh           # полная сборка обоих APK (эквивалент)
```

`build-fast.sh` использует включённые в `gradle.properties` Gradle daemon, configuration cache, parallel и build cache: повторная сборка после правки кода — ~15–20 секунд, полностью из кэша — ~2 секунды. Готовые APK дополнительно складываются в `build/apk/`.

Если SDK находится в нестандартной папке, создайте `local.properties` вручную, например:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

## Установка на телефон

**Main App.** Передайте `app-debug.apk` на Android-телефон и откройте файл, разрешив установку из выбранного источника при запросе системы. После установки откройте приложение, добавьте собственный ключ провайдера LLM в настройках. Для управления интерфейсом других приложений отдельно включите службу **AI Phone Agent** в настройках специальных возможностей Android.

**Companion Agent (опционально).** Установите `agent-companion-debug.apk`, включите его службу специальных возможностей и запустите MCP-сервер (кнопка в приложении). Сервер слушает `http://127.0.0.1:8765/mcp` и нужен для управления телефоном из Claude Desktop, Cursor и других MCP-клиентов.

Это **debug APK** для установки и тестирования, они не подписаны release-ключом для публикации в Google Play.
