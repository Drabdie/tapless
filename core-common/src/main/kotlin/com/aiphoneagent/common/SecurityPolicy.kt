package com.aiphoneagent.common

object SecurityPolicy {
    private val denylist = listOf(
        "com.sberbank",
        "ru.sberbankmobile",
        "ru.alfabank",
        "com.idamob.tinkoff.android",
        "com.tinkoff",
        "ru.vtb24.mobilebanking.android",
        "ru.vtb",
        "com.vtb",
        "com.raiffeisen",
        "ru.raiffeisennews",
        "com.google.android.apps.wallet",
        "com.android.vending",
        "com.eg.android.alipaygphone",
    )

    private val packageHints = listOf("bank", "wallet", "sberbank", "tinkoff", "alfabank", "paypal")

    private val dangerWords = listOf(
        "оплатить", "оплата", "удалить", "send money", "купить", "buy", "pay",
        "delete", "перевод", "перевести", "purchase", "checkout", "списать",
    )

    private val cardRegex = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val otpExact = Regex("""^\d{4,8}$""")
    private val secretToken = Regex("""(sk-[A-Za-z0-9_\-]{8,}|AIza[0-9A-Za-z_\-]{10,})""")

    fun isPackageBlocked(pkg: String): Boolean {
        val value = pkg.lowercase()
        if (denylist.any { value == it || value.startsWith("$it.") || value.contains(it) }) return true
        return packageHints.any { value.contains(it) }
    }

    fun requiresConfirmation(action: AgentAction, screen: ScreenSnapshot?): Boolean {
        if (isIrreversible(action, screen)) return true
        return false
    }

    fun isIrreversible(action: AgentAction, screen: ScreenSnapshot?): Boolean {
        if (action is AgentAction.SetText && looksSensitive(action.text)) return true
        if (action is AgentAction.SetText && screen?.root?.flatten()?.any { it.password } == true) return true
        val target = actionTarget(action).lowercase()
        if (target.isNotBlank() && dangerWords.any { target.contains(it) }) return true
        return false
    }

    fun sanitizeForLlm(snapshot: ScreenSnapshot): ScreenSnapshot {
        return snapshot.copy(root = sanitizeNode(snapshot.root))
    }

    fun sanitizeText(raw: String): String {
        var value = secretToken.replace(raw) { "•••" }
        value = cardRegex.replace(value) { "•••" }
        value = Regex("""(?i)(password|otp|cvv|cvc|pin)\s*[:=]\s*\S+""")
            .replace(value) { "${it.groupValues[1]}=•••" }
        return value
    }

    private fun sanitizeNode(node: UiNode): UiNode {
        val maskedText = maskField(node.text, node)
        val maskedDesc = maskField(node.contentDescription, node)
        return node.copy(
            text = maskedText,
            contentDescription = maskedDesc,
            children = node.children.map { sanitizeNode(it) },
        )
    }

    private fun maskField(value: String?, node: UiNode): String? {
        if (value.isNullOrBlank()) return value
        if (node.password) return "•••"
        val className = node.className.orEmpty().lowercase()
        if (className.contains("password")) return "•••"
        if (otpExact.matches(value.trim())) return "•••"
        if (cardRegex.containsMatchIn(value)) return sanitizeText(cardRegex.replace(value) { "•••" })
        return sanitizeText(value)
    }

    private fun looksSensitive(value: String): Boolean {
        val trimmed = value.trim()
        return otpExact.matches(trimmed) || cardRegex.containsMatchIn(trimmed)
    }

    private fun actionTarget(action: AgentAction): String = when (action) {
        is AgentAction.Tap -> selectorText(action.selector)
        is AgentAction.LongPress -> selectorText(action.selector)
        is AgentAction.SetText -> action.text
        is AgentAction.LaunchApp -> action.query
        else -> ""
    }

    private fun selectorText(selector: ElementSelector): String = when (selector) {
        is ElementSelector.ByText -> selector.text
        is ElementSelector.ByContentDescription -> selector.desc
        is ElementSelector.ByAnyText -> selector.options.joinToString(" ")
        is ElementSelector.ByResourceId -> selector.id
        else -> ""
    }
}
