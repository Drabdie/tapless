package com.aiphoneagent.common

class RuleBasedPlanner : Planner {
    override suspend fun plan(task: ParsedTask, currentScreen: ScreenSnapshot?): AgentResult<Plan> {
        return AgentResult.Success(build(task))
    }

    fun build(task: ParsedTask): Plan {
        val steps = mutableListOf<PlanStep>()
        splitIntents(task.raw).forEach { part -> interpret(part, steps) }
        if (steps.isEmpty()) {
            steps += PlanStep(
                index = 0,
                description = "Нажать «${task.raw.take(80)}»",
                action = AgentAction.Tap(ElementSelector.ByText(task.raw, exact = false)),
            )
        }
        val indexed = steps.mapIndexed { index, step -> step.copy(index = index) }
        return Plan(
            steps = indexed,
            irreversibleIndexes = indexed.mapIndexedNotNull { index, step ->
                if (step.isIrreversible) index else null
            }.toSet(),
        )
    }

    private fun splitIntents(raw: String): List<String> {
        return raw.split(Regex("""\s+(?:и|and|затем|then|,)\s+""", RegexOption.IGNORE_CASE))
            .map { it.trim().trimEnd('.', '!', '?') }
            .filter { it.isNotBlank() }
    }

    private fun interpret(part: String, out: MutableList<PlanStep>) {
        val lower = part.lowercase()
        if (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("вай-фай") || lower.contains("вайфай")) {
            add(out, "Открыть настройки Wi-Fi", AgentAction.LaunchApp("wifi"))
            return
        }
        if (lower.contains("bluetooth") || lower.contains("блютуз")) {
            add(out, "Открыть настройки Bluetooth", AgentAction.LaunchApp("bluetooth"))
            return
        }

        messageInApp.find(part)?.let { match ->
            val app = match.groupValues[1]
            val contact = match.groupValues[2]
            val message = match.groupValues[3].trim().trim('"', '«', '»')
            add(out, "Открыть $app", AgentAction.LaunchApp(app))
            add(out, "Открыть поиск", searchTap())
            add(out, "Найти $contact", AgentAction.SetText(ElementSelector.ByFocusedEditable, contact))
            add(out, "Открыть чат $contact", AgentAction.Tap(ElementSelector.ByText(contact, exact = false)))
            add(out, "Ввести сообщение", AgentAction.SetText(ElementSelector.ByFocusedEditable, message))
            add(out, "Отправить", sendTap(), irreversible = false)
            return
        }

        openRe.find(part)?.let { match ->
            val target = match.groupValues[1]
                .trim()
                .trim('"', '«', '»')
                .replace(Regex("""^(?:приложение|app|application|the)\s+""", RegexOption.IGNORE_CASE), "")
            add(out, "Открыть $target", AgentAction.LaunchApp(target))
            return
        }

        findRe.find(part)?.let { match ->
            val query = match.groupValues[1].trim().trim('"', '«', '»')
            add(out, "Открыть поиск", searchTap())
            add(out, "Ввести «$query»", AgentAction.SetText(ElementSelector.ByFocusedEditable, query))
            add(out, "Подтвердить ввод", AgentAction.ImeEnter)
            return
        }

        typeFieldRe.find(part)?.let { match ->
            val text = match.groupValues[1].trim().trim('"', '«', '»')
            val field = match.groupValues[2].trim().trim('"', '«', '»')
            val selector = if (field.isBlank()) {
                ElementSelector.ByFocusedEditable
            } else {
                ElementSelector.ByText(field, exact = false)
            }
            add(out, "Ввести «$text»", AgentAction.SetText(selector, text))
            return
        }

        typeRe.find(part)?.let { match ->
            val text = match.groupValues[1].trim().trim('"', '«', '»')
            add(out, "Ввести «$text»", AgentAction.SetText(ElementSelector.ByFocusedEditable, text))
            return
        }

        tapRe.find(part)?.let { match ->
            val label = match.groupValues[1].trim().trim('"', '«', '»')
            val irreversible = SecurityPolicy.isIrreversible(
                AgentAction.Tap(ElementSelector.ByText(label)),
                null,
            )
            add(out, "Нажать «$label»", AgentAction.Tap(ElementSelector.ByText(label, exact = false)), irreversible)
            return
        }

        if (sendRe.matches(part)) {
            add(out, "Отправить", sendTap())
            return
        }
        if (backRe.matches(lower)) {
            add(out, "Назад", AgentAction.Back)
            return
        }
        if (homeRe.matches(lower)) {
            add(out, "Домой", AgentAction.Home)
            return
        }
        if (lower.contains("прокрут") || lower.startsWith("scroll")) {
            val forward = !lower.contains("вверх") && !lower.contains("up")
            add(out, "Прокрутить", AgentAction.Scroll(ElementSelector.ByAnyText(emptyList()), forward))
            return
        }

        add(out, "Нажать «$part»", AgentAction.Tap(ElementSelector.ByText(part, exact = false)))
    }

    private fun add(
        out: MutableList<PlanStep>,
        description: String,
        action: AgentAction,
        irreversible: Boolean = SecurityPolicy.isIrreversible(action, null),
    ) {
        out += PlanStep(out.size, description, action, irreversible)
    }

    private fun searchTap() = AgentAction.Tap(
        ElementSelector.ByAnyText(listOf("Search", "Поиск", "Искать", "Найти")),
    )

    private fun sendTap() = AgentAction.Tap(
        ElementSelector.ByAnyText(listOf("Send", "Отправить", "Отправить сообщение")),
    )

    private companion object {
        val openRe = Regex("""^(?:открой|открыть|запусти|запустить|open|launch)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val tapRe = Regex("""^(?:нажми|нажать|тапни|tap|press|click)\s+(?:на\s+|on\s+|кнопку\s+|button\s+)?[«"']?(.+?)[»"']?$""", RegexOption.IGNORE_CASE)
        val typeFieldRe = Regex("""^(?:введи|ввести|type|enter|input)\s+(?:текст\s+)?[«"]?(.+?)[»"]?\s+(?:в\s+поле|in\s+field|into)\s+[«"]?(.+?)[»"]?$""", RegexOption.IGNORE_CASE)
        val typeRe = Regex("""^(?:введи|ввести|напиши|набери|type|enter|input)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val findRe = Regex("""^(?:найди|найти|поиск|search|find)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val sendRe = Regex("""^(?:отправь|отправить|send)$""", RegexOption.IGNORE_CASE)
        val backRe = Regex("""^(?:назад|back)$""", RegexOption.IGNORE_CASE)
        val homeRe = Regex("""^(?:домой|home)$""", RegexOption.IGNORE_CASE)
        val messageInApp = Regex(
            """^(?:напиши|написать|write|message)\s+(?:в|in|to)\s+(\S+)\s+(\S+)\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
