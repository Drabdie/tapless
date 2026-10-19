package com.aiphoneagent.common

sealed class AgentResult<out T> {
    data class Success<T>(val value: T) : AgentResult<T>()
    data class Failure(val error: AgentError, val message: String) : AgentResult<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.value
}

sealed class AgentError {
    data object ElementNotFound : AgentError()
    data object Timeout : AgentError()
    data object Blocked : AgentError()
    data object Cancelled : AgentError()
    data object LlmError : AgentError()
    data object Unknown : AgentError()
}
