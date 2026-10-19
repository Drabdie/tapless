# Архитектура

Проект — это **два Android-приложения** на общей кодовой базе из 14 Gradle-модулей.
Main App даёт пользовательский опыт, Companion Agent — движок для внешних агентов (MCP).

## Карта модулей

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                              app  (Main App)                              │
│  MainActivity · Navigation · Hilt                                         │
│   ├─ feature-chat        чат, история, голосовой ввод, кнопка «Сценарии»  │
│   ├─ feature-agent       экран выполнения: план, шаг, лог, STOP           │
│   ├─ feature-onboarding  разрешения + API-ключ                            │
│   ├─ feature-scenarios   список/создание/запуск сценариев (Room)          │
│   └─ feature-bubble      overlay-пузырь (SYSTEM_ALERT_WINDOW) + STOP      │
└──────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────┐
│                        agent-companion  (Companion Agent)                 │
│  MainActivity · CompanionServerService (foreground, MCP) · Hilt           │
│   ├─ CompanionAppCatalog   список установленных приложений                │
│   └─ CompanionTaskRunner   делегирование задач                            │
└──────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────┐
│                          Общие (shared) модули                            │
│  agent-accessibility  AccessibilityService, жесты, живое дерево, скриншот │
│  core-vision          VisionAnalyzer, ScreenUnderstandingBuilder          │
│  core-mcp             MCP JSON-RPC 2.0, MiniHttpServer, CompanionClient   │
│  core-network         LLM-клиенты (6 провайдеров) + EncryptedSharedPrefs  │
│  core-database        Room: tasks, action_logs, scenarios                 │
│  core-ui              Material3 тема (dark/light) и общие компоненты      │
│  core-common          модели, порты, политика, план, раннер, тесты        │
└──────────────────────────────────────────────────────────────────────────┘
```

Feature-модули не зависят друг от друга. `app` и `agent-companion` связывают их через Hilt и
Navigation. `core-mcp` — чистый JVM-модуль (без Android), поэтому легко тестируется.

## Поток выполнения задачи

```text
ChatScreen
  → TaskRepository.create(prompt)
  → AgentRunViewModel (StateFlow<RunState>)
  → AgentRunner
       → TaskParser                 NL → намерение + шаги
       → HybridPlanner              LLM (LlmClient) или RuleBasedPlanner
       → ScreenObserver             Accessibility-дерево (+ VisionAnalyzer)
       → SecurityPolicy             requiresConfirmation / sanitizeForLlm
       → ActionExecutor             LiveNodeSource + GestureRunner
       → Validator                  проверка результата шага
       → RecoveryEngine             другой селектор → scroll → back → abort
  → RunState.Done | Failed | Cancelled
```

STOP вызывает `KillSwitch.kill()` → завершает текущую корутину немедленно.

## Гибридное восприятие (Vision)

```text
ScreenSnapshot (Accessibility-дерево)
        │
        ├──────────────► UiTextRenderer ──► treeText
        │
ScreenshotProvider.capture() ──► Bitmap ──► VisionAnalyzer.analyze(frame, task)
        │                                        │
        │                                  VisionAnalysis (элементы, bbox, confidence)
        │                                        │
        └──────────► ScreenUnderstandingBuilder.build(tree, vision)
                                │
                        ScreenUnderstanding {
                          treeText, visionText, mergedText, elements
                        }
```

Когда дерево пустое/урезанное (WebView, canvas, кастомные вью), Vision-модель находит кнопки
по скриншоту, а `ScreenUnderstandingBuilder.findVisionElement` даёт координаты для тапа.

## Companion Agent и MCP

```text
Внешний агент (Claude Desktop / Cursor / OpenClaw)
        │  stdio (mcp-remote) или HTTP
        ▼
Companion Agent  ──  MiniHttpServer (127.0.0.1:8765)
        │                 │  POST /mcp  → McpSession (JSON-RPC 2.0)
        │                 │  GET  /health, /info
        │                 ▼
        │            McpToolRegistry
        │                 ├─ get_screen / tap / set_text / scroll
        │                 ├─ global_action / launch_app
        │                 └─ screenshot / list_apps / run_task
        ▼
AiAgentAccessibilityService (тот же движок, что и в Main App)
```

Companion **переобъявляет** сервис Accessibility через `tools:node="merge"` в своём манифесте и
подменяет `settingsActivity` на свой `MainActivity`, чтобы инструкция по включению вела в
Companion.

## Данные

```text
tasks (id, prompt, status, createdAt, finishedAt, language)
  1 ── * action_logs (taskId, stepIndex, action, payloadJson, result, timestamp)
scenarios (id, name, prompt, paramsJson, createdAt)
```

## Безопасность

- В промпт модели уходит `SecurityPolicy.sanitizeForLlm`: пароли, OTP из 4–8 цифр, номера карт и
  ключи `sk-` / `AIza` маскируются.
- `SafeLogger` режет строку до 500 символов и вычищает те же секреты.
- Denylist пакетов: Сбер, Альфа, Тинькофф, ВТБ, Raiffeisen, Google Wallet, Play Store billing,
  Alipay, плюс эвристика по `bank` / `wallet`.
- Необратимые действия (оплатить, удалить, купить, send money) требуют нижний лист подтверждения
  и не повторяются RecoveryEngine.
- API-ключ — в `EncryptedSharedPreferences` (Android Keystore), никогда не логируется.
- MCP-сервер слушает только loopback.

## Технологии

Kotlin 2.0.21 · Jetpack Compose (Material 3) · Hilt · Room · DataStore · Coroutines/Flow ·
OkHttp + org.json · kotlinx.serialization · Navigation Compose · Gradle 8.9 / AGP 8.7.3 ·
KSP 2.0.21-1.0.28 · JDK 17.
