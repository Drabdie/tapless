# Демо-сценарии (Definition of Done)

Ниже — три обязательных сценария MVP. Для каждого показано, что вводит
пользователь, какой план строит агент, как выглядит живой лог и какой
unit-тест это фиксирует. Тесты живут в
`core-common/src/test/kotlin/com/aiphoneagent/common/AgentLogicTest.kt` и
запускаются командой `./gradlew :core-common:test`.

Логика планирования не зависит от устройства, поэтому эти сценарии
проверяются на JVM без эмулятора. На реальном телефоне те же планы
исполняются через `ActionExecutor` поверх Accessibility Service.

---

## Сценарий 1. Открыть приложение

**Команда:** `Открой YouTube`

**План (`Plan`):**

1. `LaunchApp(query = "youtube")` — найти launcher-активность по подписи и
   запустить её.
2. `WaitFor(ByAnyText("youtube"))` — дождаться, пока окно появится
   (timeout + retry).

**Ожидаемый лог:**

```text
LaunchApp("youtube")  -> ok
WaitFor(ByAnyText("youtube")) -> ok
```

**Тест:** `plansOpenAndTap`, `plansWifiAndChromeSearch`.

---

## Сценарий 2. Найти элемент и нажать

**Команда:** `Открой YouTube и нажми "Войти"`

**План:**

1. `LaunchApp(query = "youtube")`
2. `WaitFor(ByText("Войти"))`
3. `Tap(ByText("Войти"))` — при промахе `RecoveryEngine` пробует другой
   селектор (`ByContentDescription`, `ByCoordinates`), затем scroll, затем
   back, и только потом abort.

**Ожидаемый лог:**

```text
LaunchApp("youtube")     -> ok
WaitFor(ByText("Войти")) -> ok
Tap(ByText("Войти"))     -> ok
```

**Тест:** `plansFindElement`, `plansOpenAndTap`,
`recoveryGoesThroughAltScrollBackThenAbort`.

---

## Сценарий 3. Ввести текст и отправить

**Команда:** `Напиши в WhatsApp маме "привет"`

**План:**

1. `LaunchApp(query = "whatsapp")`
2. `WaitFor(ByFocusedEditable)` — дождаться поля поиска.
3. `SetText(ByFocusedEditable, "мама")` — ввести имя контакта.
4. `Tap(ByText("мама"))` — открыть чат.
5. `SetText(ByFocusedEditable, "привет")` — ввести сообщение.
6. `ImeEnter` — отправить (или `Tap(ByContentDescription("Отправить"))`).

**Ожидаемый лог:**

```text
LaunchApp("whatsapp")                 -> ok
WaitFor(ByFocusedEditable)            -> ok
SetText(ByFocusedEditable, "мама")    -> ok
Tap(ByText("мама"))                   -> ok
SetText(ByFocusedEditable, "привет")  -> ok
ImeEnter                              -> ok
```

**Тест:** `plansTypeIntoFieldAndSend`, `plansWhatsAppMessage`.

---

## Поведение при сбоях и безопасности

| Требование | Реализация | Тест |
| --- | --- | --- |
| Повтор до 3 раз при ошибке | `AgentRunner.performWithRecovery` | `runnerSucceedsRecoversAndConfirms` |
| Альтернативный поиск элемента | `RecoveryEngine`: alt selector → scroll → back → abort | `recoveryGoesThroughAltScrollBackThenAbort` |
| Остановка по таймауту | `TaskStateMachine` budget + `ScreenObserver` timeout | `stateMachineTransitionsCancelAndTimeout`, `screenObserverTimesOutAndFindsElement` |
| STOP в любой момент | `KillSwitch.kill()` прерывает корутину | `killSwitchStopsRunner` |
| Подтверждение необратимых действий | `SecurityPolicy.requiresConfirmation` + нижний лист | `runnerSucceedsRecoversAndConfirms` |
| Платёж не повторяется | `RecoveryEngine` не ретраит irreversible | `recoveryDoesNotRetryPayment` |
| Секреты не уходят в LLM | `SecurityPolicy.sanitizeForLlm` | `sanitizeMasksPasswordOtpAndCard` |

## PRO-возможности (проверка вручную)

### Vision на сложном экране
1. Включите Vision в настройках и выберите мультимодальную модель (gpt-4o / claude-3.5 / gemini).
2. Откройте экран с WebView или кастомным canvas, где кнопки не видны в дереве.
3. Дайте команду нажать кнопку по видимому тексту. В логе `ScreenUnderstanding.mergedText`
   появятся блоки `ACCESSIBILITY TREE:` и `VISION ANALYSIS:`, а тап уйдёт по координатам из Vision.

### Сохранённые сценарии
1. Чат → иконка «Сценарии» → создайте сценарий («Открыть YouTube и найти котиков»).
2. Запустите его из списка — откроется экран выполнения с тем же планом.
3. Удалите сценарий — он исчезнет из списка (Room, `scenarios`).

### Плавающий пузырь
1. Настройки → включите пузырь, выдайте «Поверх других приложений».
2. Тап по пузырю открывает приложение; долгое нажатие — мгновенный STOP.

### Голосовой ввод
1. Разрешите микрофон. В чате нажмите иконку микрофона, продиктуйте команду.
2. Распознанный текст появится в поле ввода.

### MCP из внешнего агента
1. Установите Companion, включите его Accessibility Service, запустите MCP-сервер.
2. Проверьте `GET http://127.0.0.1:8765/health` → `{"status":"ok"}`.
3. Подключите Claude Desktop через `mcp-remote` (см. README) и вызовите `get_screen`, затем `tap`.

## Ручная проверка на устройстве

1. Установите `app-debug.apk`, включите Accessibility Service (см. README).
2. В чате введите команду из любого сценария выше.
3. На экране выполнения проверьте план, живой лог и нажмите красную **STOP**,
   чтобы убедиться, что выполнение прерывается мгновенно.
4. Для сценария с оплатой («Нажми Оплатить») убедитесь, что появляется лист
   подтверждения и действие не выполняется без вашего согласия.
