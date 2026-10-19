# AI Phone Agent

Android-агент на Kotlin и Jetpack Compose. Вы пишете команду на русском или английском, приложение строит план и выполняет его через Accessibility Service: открыть приложение, нажать, ввести текст, прокрутить. Кнопка STOP останавливает выполнение в любой момент. Платежи и банковские приложения блокируются или требуют подтверждения.

Ключ языковой модели необязателен. Без ключа работает локальный разбор команд. С ключом план может построить OpenAI, Anthropic, Gemini, OpenRouter, Groq или локальный Ollama. Ключ хранится в EncryptedSharedPreferences и не пишется в логи.

## Требования

- JDK 17
- Android Studio Ladybug или новее
- Android 8.0+ (minSdk 26), targetSdk 35

## Сборка

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug
./gradlew test
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Он подписан debug-ключом и ставится как обычный sideload.

Release-сборка уже настроена в Gradle (`isMinifyEnabled = true`, R8 + shrinkResources, правила в `app/proguard-rules.pro`). Подпись подключается автоматически, если рядом с проектом лежит `keystore.properties`:

```bash
# 1) один раз сгенерировать ключ
keytool -genkeypair -v -keystore release.keystore -alias aiphoneagent \
  -keyalg RSA -keysize 2048 -validity 10000

# 2) скопировать шаблон и вписать пароли
cp keystore.properties.example keystore.properties
#   storeFile=release.keystore
#   storePassword=***
#   keyAlias=aiphoneagent
#   keyPassword=***

# 3) собрать
./gradlew assembleRelease
```

`keystore.properties` и `*.keystore` внесены в `.gitignore` — секреты не попадут в репозиторий. Если файла нет, `assembleRelease` подписывает APK debug-ключом, чтобы артефакт оставался устанавливаемым для локального тестирования. Release APK: `app/build/outputs/apk/release/app-release.apk`.

## Как включить Accessibility

1. Установите APK и откройте AI Phone Agent.
2. На втором шаге нажмите «Открыть настройки Accessibility».
3. В списке служб выберите AI Phone Agent и включите его.
4. Подтвердите системное предупреждение. Android предупреждает, потому что служба видит экран.
5. Вернитесь в приложение. Красный баннер в чате должен исчезнуть.

Дальше введите команду или выберите пример. На экране выполнения видны план, живой лог и красная кнопка STOP.

## API-ключ

На третьем шаге выберите провайдера, вставьте ключ и нажмите «Проверить». Ключи берутся у провайдера:

- OpenAI — platform.openai.com
- Anthropic — console.anthropic.com
- Gemini — aistudio.google.com
- OpenRouter — openrouter.ai
- Groq — console.groq.com
- Ollama — локально, `http://127.0.0.1:11434`, ключ не нужен

## Примеры

1. «Открой YouTube и найди видео котиков» — запускает YouTube, открывает поиск, вводит запрос.
2. «Напиши в WhatsApp маме привет» — открывает WhatsApp, ищет контакт, вводит текст, жмёт отправку.
3. «Открой настройки Wi-Fi» — системный экран Wi-Fi.
4. «Open Chrome and search Kotlin» — Chrome, поиск, ввод Kotlin.
5. «Нажми Оплатить» — шаг необратимый, агент ждёт подтверждения и не повторяет его при ошибке.

## Ограничения

См. [docs/LIMITATIONS.md](docs/LIMITATIONS.md). Коротко: системный локскрин, FLAG_SECURE, часть WebView и банковские приложения агенту недоступны. Google Play не принимает автоматизацию через Accessibility.

## Тесты

`./gradlew :core-common:test` покрывает парсер, планировщик, восстановление, автомат состояний, маскирование секретов, наблюдатель экрана, исполнитель и сценарии раннера. Три обязательных демо-сценария и их ожидаемые логи описаны в [docs/DEMO.md](docs/DEMO.md).

## Roadmap

Этот APK — MVP. Дальше отдельными фазами:

1. **Vision** — скриншот экрана в мультимодальную модель, когда дерево Accessibility пустое (WebView, игры, кастомные канвасы).
2. **Голос** — распознавание речи (SpeechRecognizer / on-device Whisper) и озвучка статусов, управление без рук.
3. **Сценарии** — сохранённые многошаговые сценарии в Room с параметрами, отдельный экран запуска и шаринг.
4. **Плавающий пузырь** — overlay-кнопка поверх всех окон (SYSTEM_ALERT_WINDOW) для запуска и STOP без выхода из целевого приложения.
5. **MCP** — сервер Model Context Protocol, чтобы внешние агенты (Claude Desktop и др.) вызывали действия на телефоне.
6. **Companion app** — связка телефон + десктоп: удалённый запуск задач, синхронизация истории и сценариев.

## Build APK

See [`BUILD-APK-RU.md`](BUILD-APK-RU.md) for local build steps and GitHub Actions instructions. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. No model API key is needed to compile the app.
