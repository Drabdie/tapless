# Как собрать APK AI Phone Agent

## Самый простой способ — автоматическая сборка в GitHub

1. Распакуйте архив проекта.
2. Загрузите папку `ai-phone-agent` в новый GitHub-репозиторий (вместе с `.github/workflows/build-apk.yml`). Не загружайте `local.properties`, ключи API или keystore.
3. Откройте вкладку **Actions** в репозитории. Запустите workflow **Build Android APK** через **Run workflow** (либо сделайте push в `main`/`master`).
4. После успешного завершения откройте запуск workflow и скачайте артефакт **ai-phone-agent-debug-apk** внизу страницы. Внутри находится `app-debug.apk`.

Workflow самостоятельно устанавливает JDK 17 и Android SDK Platform 35 / Build Tools 35.0.0. OpenAI API-ключ для компиляции не нужен; его следует ввести в приложении после установки.

## Сборка локально через Android Studio

1. Установите Android Studio и JDK 17.
2. В SDK Manager установите **Android SDK Platform 35**, **Android SDK Build-Tools 35.0.0** и **Android SDK Platform-Tools**.
3. Откройте папку `ai-phone-agent` как проект Gradle, дождитесь синхронизации.
4. Выберите задачу Gradle `:app:assembleDebug` и запустите её.
5. Готовый APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Сборка локально в Linux / macOS

Требуются JDK 17, Android SDK Platform 35 и доступ к `google()`/`mavenCentral()` для скачивания Gradle и зависимостей. Укажите путь к SDK в `ANDROID_HOME` или `ANDROID_SDK_ROOT`, затем из папки проекта выполните:

```bash
chmod +x gradlew build-apk.sh
./build-apk.sh
```

Если SDK находится в нестандартной папке, создайте `local.properties` вручную, например:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

## Установка на телефон

Передайте `app-debug.apk` на Android-телефон и откройте файл, разрешив установку из выбранного источника при запросе системы. После установки откройте приложение, добавьте собственный ключ провайдера LLM в настройках. Для управления интерфейсом других приложений отдельно включите службу **AI Phone Agent** в настройках специальных возможностей Android.

Это **debug APK** для установки и тестирования, он не подписан release-ключом для публикации в Google Play.
