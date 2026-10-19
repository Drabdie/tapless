package com.aiphoneagent.common

class TaskParser {
    fun parse(userInput: String): ParsedTask {
        val text = userInput.trim()
        val cyrillic = text.count { it in 'А'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val language = when {
            cyrillic > latin -> TaskLanguage.RU
            latin > cyrillic -> TaskLanguage.EN
            else -> TaskLanguage.AUTO
        }
        val quoted = Regex("""[«"]([^»"]+)[»"]""").findAll(text).map { it.groupValues[1] }.toList()
        val url = Regex("""https?://\S+""").find(text)?.value
        val entities = buildMap {
            if (quoted.isNotEmpty()) put("quoted", quoted.joinToString(" | "))
            if (url != null) put("url", url)
        }
        val intent = when {
            text.contains(Regex("открой|открыть|запусти|open|launch", RegexOption.IGNORE_CASE)) -> "open"
            text.contains(Regex("введи|ввести|напиши|type|enter", RegexOption.IGNORE_CASE)) -> "input"
            text.contains(Regex("нажми|нажать|tap|press|click", RegexOption.IGNORE_CASE)) -> "tap"
            text.contains(Regex("найди|search|find", RegexOption.IGNORE_CASE)) -> "search"
            else -> "generic"
        }
        return ParsedTask(text, intent, language, entities)
    }
}
