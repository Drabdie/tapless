package com.aiphoneagent.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentLogicTest {
    private val parser = TaskParser()
    private val planner = RuleBasedPlanner()

    @Test fun parsesRussianAndQuotedText() {
        val task = parser.parse("Напиши «привет» в чат")
        assertThat(task.language).isEqualTo(TaskLanguage.RU)
        assertThat(task.intent).isEqualTo("input")
        assertThat(task.entities["quoted"]).isEqualTo("привет")
    }

    @Test fun parsesEnglishUrl() {
        val task = parser.parse("Open https://example.com now")
        assertThat(task.language).isEqualTo(TaskLanguage.EN)
        assertThat(task.intent).isEqualTo("open")
        assertThat(task.entities["url"]).isEqualTo("https://example.com")
    }

    @Test fun parsesTapIntent() {
        val task = parser.parse("Нажми кнопку Готово")
        assertThat(task.intent).isEqualTo("tap")
    }

    @Test fun parsesSearchIntent() {
        val task = parser.parse("Find kotlin coroutines")
        assertThat(task.intent).isEqualTo("search")
        assertThat(task.language).isEqualTo(TaskLanguage.EN)
    }

    @Test fun plansOpenAndTap() {
        val plan = planner.build(parser.parse("Открой приложение YouTube и нажми кнопку Подписки"))
        assertThat(plan.steps.map { it.action }).containsExactly(
            AgentAction.LaunchApp("YouTube"),
            AgentAction.Tap(ElementSelector.ByText("Подписки", exact = false)),
        ).inOrder()
    }

    @Test fun plansFindElement() {
        val plan = planner.build(parser.parse("Найди элемент Настройки"))
        assertThat(plan.steps.first().action).isInstanceOf(AgentAction.Tap::class.java)
        assertThat(plan.steps[1].action).isInstanceOf(AgentAction.SetText::class.java)
    }

    @Test fun plansTypeIntoFieldAndSend() {
        val plan = planner.build(parser.parse("Введи текст T в поле F и отправь"))
        assertThat(plan.steps[0].action).isEqualTo(AgentAction.SetText(ElementSelector.ByText("F", exact = false), "T"))
        assertThat(plan.steps[1].action).isInstanceOf(AgentAction.Tap::class.java)
    }

    @Test fun plansWifiAndChromeSearch() {
        val wifi = planner.build(parser.parse("Открой настройки Wi-Fi"))
        assertThat(wifi.steps.single().action).isEqualTo(AgentAction.LaunchApp("wifi"))
        val chrome = planner.build(parser.parse("Open Chrome and search Kotlin"))
        assertThat(chrome.steps[0].action).isEqualTo(AgentAction.LaunchApp("Chrome"))
        assertThat((chrome.steps[2].action as AgentAction.SetText).text).isEqualTo("Kotlin")
    }

    @Test fun plansWhatsAppMessage() {
        val plan = planner.build(parser.parse("Напиши в WhatsApp маме привет"))
        assertThat(plan.steps.first().action).isEqualTo(AgentAction.LaunchApp("WhatsApp"))
        assertThat(plan.steps.map { it.action }.filterIsInstance<AgentAction.SetText>().map { it.text })
            .containsExactly("маме", "привет")
            .inOrder()
    }

    @Test fun recoveryGoesThroughAltScrollBackThenAbort() {
        val engine = RecoveryEngine()
        val action = AgentAction.Tap(ElementSelector.ByText("Сохранить", exact = true))
        val first = engine.recover(action, 1, null)
        assertThat(first).isInstanceOf(RecoveryDecision.Retry::class.java)
        assertThat((first as RecoveryDecision.Retry).action)
            .isEqualTo(AgentAction.Tap(ElementSelector.ByText("Сохранить", exact = false)))
        assertThat(engine.recover(action, 2, null)).isInstanceOf(RecoveryDecision.ScrollAndRetry::class.java)
        assertThat(engine.recover(action, 3, null)).isInstanceOf(RecoveryDecision.BackAndRetry::class.java)
        assertThat(engine.recover(action, 4, null)).isEqualTo(RecoveryDecision.Abort)
    }

    @Test fun recoveryDoesNotRetryPayment() {
        val action = AgentAction.Tap(ElementSelector.ByText("Оплатить"))
        assertThat(RecoveryEngine().recover(action, 1, null)).isEqualTo(RecoveryDecision.Abort)
    }

    @Test fun stateMachineTransitionsCancelAndTimeout() {
        val machine = TaskStateMachine(budgetMs = 120_000, now = { 1_000 })
        assertThat(machine.reduce(MachineEvent.Plan)).isEqualTo(MachineState.Planning)
        assertThat(machine.reduce(MachineEvent.Planned)).isEqualTo(MachineState.Executing)
        assertThat(machine.reduce(MachineEvent.NeedConfirm)).isEqualTo(MachineState.AwaitingConfirmation)
        assertThat(machine.reduce(MachineEvent.Confirmed)).isEqualTo(MachineState.Executing)
        assertThat(machine.reduce(MachineEvent.Succeed)).isEqualTo(MachineState.Done)

        val cancelled = TaskStateMachine()
        cancelled.reduce(MachineEvent.Plan)
        assertThat(cancelled.reduce(MachineEvent.Cancel)).isEqualTo(MachineState.Cancelled)

        var clock = 0L
        val expired = TaskStateMachine(budgetMs = 100, now = { clock })
        expired.reduce(MachineEvent.Plan)
        clock = 500
        assertThat(expired.reduce(MachineEvent.StartStep)).isEqualTo(MachineState.Failed)

        val verifying = TaskStateMachine()
        verifying.reduce(MachineEvent.Plan)
        verifying.reduce(MachineEvent.Planned)
        verifying.reduce(MachineEvent.Verify)
        assertThat(verifying.state).isEqualTo(MachineState.Verifying)
        assertThat(verifying.reduce(MachineEvent.Recover)).isEqualTo(MachineState.Recovering)
    }

    @Test fun sanitizeMasksPasswordOtpAndCard() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example",
            root = UiNode(
                text = "Оплата",
                children = listOf(
                    UiNode(text = "4276123412341234", password = false),
                    UiNode(text = "123456", password = false),
                    UiNode(text = "secret", password = true),
                    UiNode(text = "sk-live-abcdefghijklmnopqrstuvwxyz", className = "Text"),
                ),
            ),
            timestamp = 1,
        )
        val clean = SecurityPolicy.sanitizeForLlm(snapshot)
        val texts = clean.root.flatten().mapNotNull { it.text }
        assertThat(texts).doesNotContain("4276123412341234")
        assertThat(texts).doesNotContain("123456")
        assertThat(texts).doesNotContain("secret")
        assertThat(texts.joinToString(" ")).doesNotContain("sk-live")
        assertThat(SecurityPolicy.sanitizeText("password=hunter2 card 4111111111111111"))
            .doesNotContain("hunter2")
        assertThat(SecurityPolicy.isPackageBlocked("ru.sberbankmobile")).isTrue()
        assertThat(SecurityPolicy.isPackageBlocked("com.google.android.youtube")).isFalse()
    }

    @Test fun prunesInvisibleNodesAndHonorsLimit() {
        val leaf = UiNode(text = "x", bounds = UiRect(0, 0, 0, 0), visible = true)
        val hidden = UiNode(text = "hidden", visible = false, bounds = UiRect(0, 0, 10, 10))
        val root = UiNode(
            text = "root",
            bounds = UiRect(0, 0, 100, 100),
            children = listOf(leaf, hidden, UiNode(bounds = UiRect(0, 0, 20, 20), children = List(5) {
                UiNode(text = "n$it", bounds = UiRect(0, 0, 8, 8))
            })),
        )
        val pruned = UiTreeLimiter.pruneAndLimit(root, maxNodes = 3, maxDepth = 40)
        assertThat(pruned.flatten().size).isAtMost(3)
        assertThat(pruned.flatten().none { it.text == "hidden" || it.bounds.width == 0 }).isTrue()
    }

    @Test fun screenObserverTimesOutAndFindsElement() = runTest {
        val flow = MutableStateFlow<ScreenSnapshot?>(null)
        val observer = ScreenObserverImpl(flow)
        assertThat(observer.await({ true }, timeoutMs = 30, pollMs = 10)).isNull()
        val node = UiNode(text = "Готово", bounds = UiRect(0, 0, 20, 20))
        flow.value = ScreenSnapshot("com.app", node, 1)
        assertThat(observer.awaitElement(ElementSelector.ByText("Готово"), timeoutMs = 200)?.text).isEqualTo("Готово")
        assertThat(observer.awaitElement(ElementSelector.ByText("Нет"), timeoutMs = 30)).isNull()
    }

    @Test fun executorTapsLogsAndMisses() = runTest {
        val logs = MemoryActionLogRepository()
        val context = TaskContextProvider().apply { taskId = 7; stepIndex = 1 }
        val gestures = FakeGestures()
        val nodes = FakeNodes()
        val engine = ActionExecutorEngine(nodes, gestures, logs, context, DefaultKillSwitch(), ScreenObserverImpl(MutableStateFlow(null)))
        val ok = engine.execute(AgentAction.Tap(ElementSelector.ByText("OK")))
        assertThat(ok).isInstanceOf(AgentResult.Success::class.java)
        assertThat(gestures.taps).containsExactly(10 to 20)
        val missing = engine.execute(AgentAction.Tap(ElementSelector.ByText("Нет")))
        assertThat((missing as AgentResult.Failure).error).isEqualTo(AgentError.ElementNotFound)
        val launch = engine.execute(AgentAction.LaunchApp("YouTube"))
        assertThat(launch).isInstanceOf(AgentResult.Success::class.java)
        assertThat(gestures.launches).containsExactly("YouTube")
    }

    @Test fun killSwitchStopsRunner() = runTest {
        val kill = DefaultKillSwitch()
        val runner = runner(kill, object : ActionExecutor {
            override suspend fun execute(action: AgentAction): AgentResult<Unit> {
                kill.kill()
                return AgentResult.Success(Unit)
            }
        })
        val state = runner.run(3, "Открой YouTube и нажми Поиск")
        assertThat(state).isEqualTo(RunState.Cancelled(3))
    }

    @Test fun runnerSucceedsRecoversAndConfirms() = runTest {
        val kill = DefaultKillSwitch()
        val executor = FakeExecutor(failTaps = 1)
        val runner = runner(kill, executor)
        val open = launch { runner.run(1, "Открой приложение X и нажми кнопку Y") }
        open.join()
        assertThat(runner.state.value).isInstanceOf(RunState.Done::class.java)

        val payExecutor = FakeExecutor()
        val payRunner = runner(DefaultKillSwitch(), payExecutor)
        val job = launch { payRunner.run(2, "Нажми Оплатить") }
        while (payRunner.state.value !is RunState.AwaitingConfirmation) {
            kotlinx.coroutines.yield()
        }
        payRunner.confirm()
        job.join()
        assertThat(payRunner.state.value).isInstanceOf(RunState.Done::class.java)
        assertThat(payExecutor.actions.filterIsInstance<AgentAction.Tap>()).isNotEmpty()

        val cancelRunner = runner(DefaultKillSwitch(), FakeExecutor())
        val waiting = CompletableDeferred<Unit>()
        val cancelJob = launch {
            waiting.complete(Unit)
            cancelRunner.run(4, "Нажми Оплатить")
        }
        waiting.await()
        while (cancelRunner.state.value !is RunState.AwaitingConfirmation) {
            kotlinx.coroutines.yield()
        }
        cancelRunner.stop()
        cancelJob.join()
        assertThat(cancelRunner.state.value).isEqualTo(RunState.Cancelled(4))
    }

    private fun runner(kill: KillSwitch, executor: ActionExecutor): AgentRunner {
        return AgentRunner(
            parser = parser,
            planner = planner,
            executor = executor,
            observer = ScreenObserverImpl(MutableStateFlow(null)),
            validator = Validator(),
            recovery = RecoveryEngine(),
            killSwitch = kill,
            logs = MemoryActionLogRepository(),
            taskContext = TaskContextProvider(),
        )
    }
}

private class FakeNodes : NodeSource {
    override fun packageName(): String = "com.example"
    override fun centerOf(selector: ElementSelector): FoundTarget? {
        val text = (selector as? ElementSelector.ByText)?.text
        return if (text == "Нет") null else FoundTarget(UiRect(0, 0, 20, 40), text)
    }
    override fun setText(selector: ElementSelector, text: String) = true
    override fun scroll(selector: ElementSelector, forward: Boolean) = true
    override fun imeEnter() = true
}

private class FakeGestures : GestureRunner {
    val taps = mutableListOf<Pair<Int, Int>>()
    val launches = mutableListOf<String>()
    override suspend fun tap(x: Int, y: Int, durationMs: Long): Boolean {
        taps += x to y
        return true
    }
    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long) = true
    override suspend fun global(which: GlobalGesture) = true
    override suspend fun launch(query: String): AgentResult<Unit> {
        launches += query
        return AgentResult.Success(Unit)
    }
    override fun screenSize() = 1080 to 2400
}

private class FakeExecutor(private val failTaps: Int = 0) : ActionExecutor {
    val actions = mutableListOf<AgentAction>()
    private var failed = 0
    override suspend fun execute(action: AgentAction): AgentResult<Unit> {
        actions += action
        if (action is AgentAction.Tap && failed < failTaps) {
            failed += 1
            return AgentResult.Failure(AgentError.ElementNotFound, "miss")
        }
        return AgentResult.Success(Unit)
    }
}
