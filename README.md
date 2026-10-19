# AI Phone Agent — продвинутый агент управления смартфоном

**AI Phone Agent** — это Android-приложение на Kotlin + Jetpack Compose, которое превращает
естественную команду («Открой WhatsApp и напиши маме привет», «Найди видео котиков на YouTube»)
в пошаговый план и выполняет его прямо на экране телефона через Accessibility Service:
открывает приложения, нажимает, вводит текст, скроллит, возвращается назад.

Проект состоит из **двух приложений** и **общей кодовой базы**:

| Компонент | Пакет | Назначение |
|-----------|-------|------------|
| **Main App** | `com.aiphoneagent.app` | UI, чат, AI-планировщик, настройки, история, сценарии, голос, плавающий пузырь |
| **Companion Agent** | `com.aiphoneagent.companion` | Отдельный APK со своим Accessibility Service и локальным **MCP-сервером** — чтобы обойти ограничения Google Play и управлять телефоном из Claude Desktop / Cursor / OpenClaw |

> Companion-приложение вынесено отдельно намеренно: политика Google Play запрещает использовать
> Accessibility для «общей автоматизации» в потребительских приложениях. Разделив UI-приложение и
> агент-движок, вы можете распространять Main App любым каналом, а Companion ставить как инструмент
> разработчика/энтузиаста (sideload, F-Droid, свой сайт).

---

## Чем это лучше ZeroTap

| Возможность | ZeroTap | AI Phone Agent |
|-------------|:-------:|:--------------:|
| Понимание сложных экранов | только Accessibility-дерево | **гибрид**: дерево + скриншот через Vision-модель |
| Работа в WebView / canvas / играх | часто ломается | Vision достраивает то, что не видно в дереве |
| Модели | фиксированные | **любые**: OpenAI, Anthropic, Gemini, OpenRouter, Groq, локальный Ollama (BYOK) |
| Само-исправление | базовая | 3 ретрая → альтернативный селектор → скролл → back → abort |
| Сохранение сценариев | ограничено | **Room + отдельный экран** с параметрами и повторным запуском |
| Голос | нет | SpeechRecognizer, ввод без рук |
| Плавающая кнопка | нет | overlay-пузырь поверх всех окон с мгновенным STOP |
| Прозрачность | скрытая | живой план, текущий шаг, полный лог действий |
| Внешние агенты | нет | **MCP-сервер**: Claude Desktop / Cursor управляют телефоном |
| Приватность | — | ключи в EncryptedSharedPreferences, маскирование секретов, denylist банков |

---

## Содержание

- [Возможности](#возможности)
- [Архитектура](#архитектура)
- [Требования](#требования)
- [Сборка](#сборка)
- [Установка и первичная настройка](#установка-и-первичная-настройка)
- [API-ключ и провайдеры моделей](#api-ключ-и-провайдеры-моделей)
- [Примеры команд](#примеры-команд)
- [MCP-сервер (Claude Desktop / Cursor)](#mcp-сервер-claude-desktop--cursor)
- [Безопасность и приватность](#безопасность-и-приватность)
- [Тесты](#тесты)
- [Ограничения](#ограничения)
- [Roadmap](#roadmap)

---

## Возможности

### Полный контроль устройства (Accessibility layer)
- Чтение UI-дерева: `text`, `contentDescription`, `className`, `resourceId`, `bounds`, `clickable`,
  `scrollable`, `enabled`, `editable`.
- Действия: tap, long press, swipe, scroll (вверх/вниз/влево/вправо), back, home, recents,
  ввод текста (`ACTION_SET_TEXT`), очистка поля.
- Поиск элемента по тексту, resourceId, contentDescription или координатам.
- `waitForElement` с таймаутом, ретраями и отменой.
- Работа с любыми приложениями: мессенджеры, соцсети, WebView, банки (с ограничениями, см. ниже).

### Умный AI-агент
- Команды на **русском и английском**.
- Многошаговое планирование: NL → атомарные шаги → выполнение.
- **Гибридное восприятие**: Accessibility-дерево + анализ скриншота Vision-моделью. Когда дерево
  пустое или урезанное (WebView, canvas, кастомные вью) — Vision находит кнопки по картинке.
- **Само-исправление**: до 3 попыток на шаг, затем альтернативный поиск элемента, скролл, возврат
  назад; при неудаче — понятная ошибка и остановка.
- Кнопка **STOP** в любой момент (kill switch завершает корутину немедленно).
- Подтверждение перед необратимыми действиями (оплата, удаление, покупка, отправка денег).

### UI
- Современный чат (Material 3, тёмная и светлая темы).
- Экран выполнения: план, текущий шаг, живой лог действий, кнопка STOP.
- **Голосовой ввод** (микрофон в чате).
- **Плавающий пузырь** поверх всех окон: тап — открыть приложение, долгое нажатие — STOP.
- История задач (Room) и **сохранённые сценарии** с параметрами и повторным запуском.
- Онбординг с пошаговой инструкцией включения Accessibility.

### Внешние агенты (MCP)
- Локальный HTTP-сервер (только `127.0.0.1`) с реализацией **Model Context Protocol** (JSON-RPC 2.0).
- Инструменты: `get_screen`, `tap`, `set_text`, `scroll`, `global_action`, `launch_app`,
  `screenshot`, `list_apps`, `run_task`.
- Подключение из Claude Desktop, Cursor, OpenClaw и любого MCP-клиента.

---

## Архитектура

Clean Architecture + MVVM, мультимодульный Gradle. Feature-модули не зависят друг от друга;
`app` и `agent-companion` связывают их через Hilt и Navigation.

```text
app (Main App)                         agent-companion (Companion Agent)
 ├─ feature-chat        чат+история     ├─ MainActivity (Compose UI настройки)
 ├─ feature-agent       раннер UI       ├─ CompanionServerService (foreground, MCP)
 ├─ feature-onboarding  разрешения      ├─ CompanionBindings (Hilt)
 ├─ feature-scenarios   сценарии        └─ CompanionTaskRunner / AppCatalog
 ├─ feature-bubble      overlay-пузырь
 ├─ agent-accessibility сервис, жесты, дерево, скриншот
 ├─ core-vision         VisionAnalyzer + ScreenUnderstandingBuilder
 ├─ core-mcp            MCP JSON-RPC + MiniHttpServer + CompanionClient
 ├─ core-network        LLM-клиенты (6 провайдеров) + EncryptedSharedPreferences
 ├─ core-database       Room: tasks, action_logs, scenarios
 ├─ core-ui             Material3 тема и компоненты
 └─ core-common         модели, политика, план, раннер, тесты
```

Подробнее — [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Поток задачи

```text
ChatScreen
  → TaskRepository.create
  → AgentRunViewModel
  → AgentRunner
       → TaskParser
       → HybridPlanner (LLM или RuleBasedPlanner)
       → ScreenObserver (+ VisionAnalyzer, если включён)
       → SecurityPolicy.requiresConfirmation
       → ActionExecutor (LiveNodeSource + GestureRunner)
       → Validator
       → RecoveryEngine (другой селектор → scroll → back → abort)
  → RunState.Done | Failed | Cancelled
```

STOP вызывает `KillSwitch.kill()` и завершает текущую корутину.

---

## Требования

- JDK 17
- Android Studio Ladybug или новее (либо только Gradle CLI)
- Android SDK: `compileSdk 35`, `targetSdk 35`, `minSdk 26` (Android 8.0+)
- Android 8.0+ на устройстве (для Vision-скриншота нужен Android 11+, API 30)

---

## Сборка

```bash
export ANDROID_HOME=/opt/android-sdk   # путь к вашему Android SDK
./gradlew assembleDebug                # соберёт оба приложения
./gradlew test                         # юнит-тесты
```

Артефакты:

| Файл | Описание |
|------|----------|
| `app/build/outputs/apk/debug/app-debug.apk` | Main App (debug, подписан debug-ключом) |
| `agent-companion/build/outputs/apk/debug/agent-companion-debug.apk` | Companion Agent (debug) |

Собрать по отдельности:

```bash
./gradlew :app:assembleDebug
./gradlew :agent-companion:assembleDebug
```

### Release-сборка (R8 + shrinkResources)

Release-конфигурация уже настроена в Gradle (`isMinifyEnabled = true`, `shrinkResources = true`,
правила в `app/proguard-rules.pro`). Подпись подключается автоматически, если рядом с проектом
лежит `keystore.properties`:

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

# 3) собрать оба приложения
./gradlew assembleRelease
```

`keystore.properties` и `*.keystore` внесены в `.gitignore` — секреты не попадут в репозиторий.
Если файла нет, `assembleRelease` подписывает APK debug-ключом, чтобы артефакт оставался
устанавливаемым для локального тестирования.

Release-артефакты:

- `app/build/outputs/apk/release/app-release.apk`
- `agent-companion/build/outputs/apk/release/agent-companion-release.apk`

---

## Установка и первичная настройка

### 1. Main App

1. Установите `app-debug.apk` (или release) и откройте приложение.
2. На втором шаге онбординга нажмите **«Открыть настройки Accessibility»**.
3. В списке служб выберите **AI Phone Agent** и включите его.
4. Подтвердите системное предупреждение (Android предупреждает, потому что служба видит экран).
5. Вернитесь в приложение — красный баннер в чате должен исчезнуть.
6. (Опционально) В настройках включите **плавающий пузырь** и выдайте разрешение
   «Поверх других приложений» (SYSTEM_ALERT_WINDOW).
7. (Опционально) Разрешите микрофон для голосового ввода.

### 2. Companion Agent (для MCP / внешних агентов)

1. Установите `agent-companion-debug.apk`.
2. Откройте Companion и включите его Accessibility Service (инструкция внутри).
3. Нажмите **«Запустить MCP-сервер»** — появится foreground-уведомление со кнопкой STOP.
4. Сервер слушает `http://127.0.0.1:8765/mcp` (только локально, наружу не выходит).

Main App умеет проверять, установлен ли Companion и жив ли его сервер (экран «Настройки» →
карточка «Приложение-компаньон»).

---

## API-ключ и провайдеры моделей

Ключ **не обязателен**: без него работает локальный `RuleBasedPlanner`. С ключом планирование и
Vision-анализ идут через выбранную модель.

Онбординг → шаг 3: выберите провайдера, вставьте ключ, нажмите «Проверить».

| Провайдер | Где взять ключ | Модель по умолчанию |
|-----------|----------------|---------------------|
| OpenAI | platform.openai.com | `gpt-4o-mini` |
| Anthropic | console.anthropic.com | `claude-3-5-haiku-20241022` |
| Gemini | aistudio.google.com | `gemini-2.0-flash` |
| OpenRouter | openrouter.ai | `openai/gpt-4o-mini` |
| Groq | console.groq.com | `llama-3.3-70b-versatile` |
| Ollama | локально, `http://127.0.0.1:11434` | `llama3.2` (ключ не нужен) |

Для Vision-анализа нужна мультимодальная модель (gpt-4o, claude-3.5+, gemini-1.5+/2.0).
Ключ хранится в `EncryptedSharedPreferences` (Android Keystore) и никогда не пишется в логи.

---

## Примеры команд

1. **«Открой YouTube и найди видео котиков»** — запускает YouTube, открывает поиск, вводит запрос.
2. **«Напиши в WhatsApp маме привет»** — открывает WhatsApp, ищет контакт, вводит текст, жмёт отправку.
3. **«Открой настройки Wi-Fi»** — системный экран Wi-Fi.
4. **«Open Chrome and search Kotlin»** — Chrome, поиск, ввод Kotlin.
5. **«Нажми Оплатить»** — шаг необратимый: агент ждёт подтверждения и не повторяет его при ошибке.

Три обязательных демо-сценария и ожидаемые логи — в [docs/DEMO.md](docs/DEMO.md).

---

## MCP-сервер (Claude Desktop / Cursor)

Companion Agent поднимает локальный MCP-сервер. Подключение через `mcp-remote`:

```jsonc
// claude_desktop_config.json
{
  "mcpServers": {
    "ai-phone-agent": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://127.0.0.1:8765/mcp"]
    }
  }
}
```

> `mcp-remote` нужен, потому что MCP-клиенты обычно говорят по stdio, а сервер — по HTTP.
> Если клиент умеет HTTP-транспорт напрямую, укажите `http://127.0.0.1:8765/mcp`.

Доступные инструменты:

| Инструмент | Что делает |
|------------|------------|
| `get_screen` | Возвращает текст UI-дерева текущего экрана |
| `tap` | Нажать по тексту / resourceId / координатам |
| `set_text` | Ввести текст в поле |
| `scroll` | Скролл вверх/вниз/влево/вправо |
| `global_action` | back / home / recents / notifications |
| `launch_app` | Запустить приложение по имени пакета |
| `screenshot` | Скриншот экрана (base64 JPEG) |
| `list_apps` | Список установленных приложений |
| `run_task` | Запустить высокоуровневую задачу (в Companion — делегирует в Main App) |

Проверка живости сервера: `GET http://127.0.0.1:8765/health`.

---

## Безопасность и приватность

- **Маскирование секретов** (`SecurityPolicy.sanitizeForLlm`): пароли, OTP из 4–8 цифр, номера карт,
  ключи `sk-` / `AIza` заменяются перед отправкой в модель.
- **SafeLogger** режет строки до 500 символов и вычищает те же секреты.
- **Denylist пакетов**: Сбер, Альфа, Тинькофф, ВТБ, Raiffeisen, Google Wallet, Play Store billing,
  Alipay, плюс эвристика по `bank` / `wallet`.
- **Необратимые действия** (оплатить, удалить, купить, send money) требуют нижнего листа
  подтверждения и не повторяются RecoveryEngine.
- **Локальный лог действий** в Room — ничего не уходит на серверы, кроме запросов к выбранной вами
  модели.
- **Kill switch** — STOP и долгое нажатие на пузырь мгновенно останавливают агента.
- MCP-сервер слушает только `127.0.0.1` и не выходит в сеть.

---

## Тесты

```bash
./gradlew :core-common:test            # 18 тестов: парсер, планировщик, recovery, автомат, политика
./gradlew :core-vision:testDebugUnitTest  # 7 тестов: разбор Vision JSON, merge дерева и vision
./gradlew :core-mcp:test               # 10 тестов: реестр инструментов, JSON-RPC
./gradlew :core-database:testDebugUnitTest # 6 тестов: сценарии (create/observe/delete)
```

Всего **41 юнит-тест**, все зелёные.

---

## Ограничения

Коротко: системный локскрин, `FLAG_SECURE`, часть WebView и банковские приложения агенту
недоступны; Google Play не принимает автоматизацию через Accessibility. Полный список —
[docs/LIMITATIONS.md](docs/LIMITATIONS.md).

---

## Roadmap

1. **On-device Vision** — локальная мультимодальная модель (без отправки скриншотов в облако).
2. **Голосовой вывод** — TTS-озвучка статусов, полностью безрукое управление.
3. **Синхронизация сценариев** — экспорт/импорт и шаринг между устройствами.
4. **Companion ↔ Main App** — двусторонний канал: удалённый запуск задач и синхронизация истории.
5. **Мультиязычный NLU** — расширенный локальный парсер без облака.

---

## Build APK

См. [`BUILD-APK-RU.md`](BUILD-APK-RU.md) для локальной сборки и инструкций GitHub Actions.
Debug APK пишется в `app/build/outputs/apk/debug/app-debug.apk`. Для компиляции API-ключ модели
не нужен.
