# Архитектура

```text
app
 ├─ feature-chat          чат и история задач
 ├─ feature-agent         раннер UI, LLM-планировщик
 ├─ feature-onboarding    разрешения и API-ключ
 ├─ agent-accessibility   служба, жесты, живое дерево
 ├─ core-network          LLM-клиенты и EncryptedSharedPreferences
 ├─ core-database         Room: tasks, action_logs, scenarios
 ├─ core-ui               Material3 тема и общие компоненты
 └─ core-common           модели, политика, план, раннер, тесты
```

feature-модули не зависят друг от друга. `app` связывает их через Hilt и Navigation.

## Поток задачи

```text
ChatScreen
  -> TaskRepository.create
  -> AgentRunViewModel
  -> AgentRunner
       -> TaskParser
       -> HybridPlanner (LLM или RuleBasedPlanner)
       -> SecurityPolicy.requiresConfirmation
       -> ActionExecutor
            -> LiveNodeSource + GestureRunner
            -> ActionLogRepository
       -> Validator
       -> RecoveryEngine (другой селектор -> scroll -> back -> abort)
  -> RunState.Done | Failed | Cancelled
```

STOP вызывает `KillSwitch.kill()` и завершает текущую корутину.

## Данные

```text
tasks (id, prompt, status, createdAt, finishedAt, language)
  1 ── * action_logs (taskId, stepIndex, action, payloadJson, result, timestamp)
scenarios (id, name, prompt, paramsJson, createdAt)
```

## Безопасность

- В промпт модели уходит `SecurityPolicy.sanitizeForLlm`: пароли, OTP из 4–8 цифр, номера карт и ключи `sk-` / `AIza` маскируются.
- `SafeLogger` режет строку до 500 символов и вычищает те же секреты.
- Denylist пакетов: Сбер, Альфа, Тинькофф, ВТБ, Raiffeisen, Google Wallet, Play Store billing, Alipay, плюс эвристика по `bank` / `wallet`.
- Необратимые действия (оплатить, удалить, купить, send money) требуют нижний лист подтверждения и не повторяются RecoveryEngine.
