package com.aiphoneagent.common

import kotlinx.coroutines.flow.Flow

/**
 * A saved, re-runnable user scenario. The [prompt] is the natural-language command and
 * [paramsJson] holds optional named placeholders (e.g. contact names) that the user can
 * fill in before running the scenario again.
 */
data class SavedScenario(
    val id: Long,
    val name: String,
    val prompt: String,
    val paramsJson: String,
    val createdAt: Long,
)

interface ScenarioRepository {
    suspend fun create(name: String, prompt: String, paramsJson: String = "{}"): Long
    fun observeAll(): Flow<List<SavedScenario>>
    suspend fun delete(id: Long)
}
