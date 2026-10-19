package com.aiphoneagent.database

import android.content.Context
import androidx.room.Room
import com.aiphoneagent.common.ActionLogEntry
import com.aiphoneagent.common.ActionLogRepository
import com.aiphoneagent.common.TaskRecord
import com.aiphoneagent.common.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(private val dao: TaskDao) : TaskRepository {
    override suspend fun create(prompt: String, language: String): Long {
        return dao.insert(
            TaskEntity(
                prompt = prompt,
                status = "CREATED",
                createdAt = System.currentTimeMillis(),
                finishedAt = null,
                language = language,
            ),
        )
    }

    override suspend fun get(id: Long): TaskRecord? = dao.get(id)?.toRecord()

    override fun observe(id: Long): Flow<TaskRecord?> = dao.observe(id).map { it?.toRecord() }

    override fun observeAll(): Flow<List<TaskRecord>> = dao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun updateStatus(id: Long, status: String, finishedAt: Long?) {
        dao.updateStatus(id, status, finishedAt)
    }
}

class ActionLogRepositoryImpl(private val dao: ActionLogDao) : ActionLogRepository {
    override suspend fun append(entry: ActionLogEntry) {
        dao.insert(
            ActionLogEntity(
                taskId = entry.taskId,
                stepIndex = entry.stepIndex,
                action = entry.action,
                payloadJson = entry.payloadJson,
                result = entry.result,
                timestamp = entry.timestamp,
            ),
        )
    }

    override fun observe(taskId: Long): Flow<List<ActionLogEntry>> {
        return dao.observe(taskId).map { list ->
            list.map {
                ActionLogEntry(it.id, it.taskId, it.stepIndex, it.action, it.payloadJson, it.result, it.timestamp)
            }
        }
    }
}

private fun TaskEntity.toRecord() = TaskRecord(id, prompt, status, createdAt, finishedAt, language)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "ai-phone-agent.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun taskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun actionLogDao(db: AppDatabase): ActionLogDao = db.actionLogDao()
    @Provides fun scenarioDao(db: AppDatabase): ScenarioDao = db.scenarioDao()

    @Provides
    @Singleton
    fun tasks(dao: TaskDao): TaskRepository = TaskRepositoryImpl(dao)

    @Provides
    @Singleton
    fun logs(dao: ActionLogDao): ActionLogRepository = ActionLogRepositoryImpl(dao)
}
