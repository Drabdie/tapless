package com.aiphoneagent.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

object AppDispatchers {
    @Volatile var io: CoroutineDispatcher = Dispatchers.IO
    @Volatile var default: CoroutineDispatcher = Dispatchers.Default
    @Volatile var main: CoroutineDispatcher = Dispatchers.Default
}

interface KillSwitch {
    val isKilled: StateFlow<Boolean>
    fun kill()
    fun reset()
}

class DefaultKillSwitch : KillSwitch {
    private val killed = MutableStateFlow(false)
    override val isKilled: StateFlow<Boolean> = killed.asStateFlow()
    override fun kill() { killed.value = true }
    override fun reset() { killed.value = false }
}

object SafeLogger {
    private val banned = listOf("password", "otp", "cvv", "cvc", "card")
    private val secret = Regex("""(sk-[A-Za-z0-9_\-]{8,}|AIza[0-9A-Za-z_\-]{10,})""")

    fun redact(message: String): String {
        var value = secret.replace(message) { "•••" }
        if (banned.any { value.contains(it, ignoreCase = true) }) {
            value = value.replace(Regex("""\d{12,19}"""), "•••")
        }
        return value.take(500)
    }

    fun d(tag: String, message: String) {
        println("D/$tag: ${redact(message)}")
    }
}

fun <T> Flow<T>.throttleFirst(windowMs: Long): Flow<T> = flow {
    var last = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - last >= windowMs) {
            last = now
            emit(value)
        }
    }
}

suspend fun <T> withTimeoutResult(timeoutMs: Long, block: suspend () -> T): AgentResult<T> {
    return try {
        AgentResult.Success(withTimeout(timeoutMs) { block() })
    } catch (error: TimeoutCancellationException) {
        AgentResult.Failure(AgentError.Timeout, "Timeout ${timeoutMs}ms")
    }
}

data class ActionLogEntry(
    val id: Long = 0,
    val taskId: Long,
    val stepIndex: Int,
    val action: String,
    val payloadJson: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis(),
)

interface ActionLogRepository {
    suspend fun append(entry: ActionLogEntry)
    fun observe(taskId: Long): Flow<List<ActionLogEntry>>
}

class MemoryActionLogRepository : ActionLogRepository {
    private val items = MutableStateFlow<List<ActionLogEntry>>(emptyList())
    override suspend fun append(entry: ActionLogEntry) {
        items.value = items.value + entry.copy(id = items.value.size + 1L)
    }
    override fun observe(taskId: Long): Flow<List<ActionLogEntry>> = flow {
        items.collect { list -> emit(list.filter { it.taskId == taskId }) }
    }
}

data class TaskRecord(
    val id: Long,
    val prompt: String,
    val status: String,
    val createdAt: Long,
    val finishedAt: Long?,
    val language: String,
)

interface TaskRepository {
    suspend fun create(prompt: String, language: String): Long
    suspend fun get(id: Long): TaskRecord?
    fun observe(id: Long): Flow<TaskRecord?>
    fun observeAll(): Flow<List<TaskRecord>>
    suspend fun updateStatus(id: Long, status: String, finishedAt: Long? = null)
}

class TaskContextProvider {
    @Volatile var taskId: Long = -1
    @Volatile var stepIndex: Int = -1
}
