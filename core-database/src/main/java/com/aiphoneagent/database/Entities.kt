package com.aiphoneagent.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val status: String,
    val createdAt: Long,
    val finishedAt: Long?,
    val language: String,
)

@Entity(
    tableName = "action_logs",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val stepIndex: Int,
    val action: String,
    val payloadJson: String,
    val result: String,
    val timestamp: Long,
)

@Entity(tableName = "scenarios")
data class SavedScenarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prompt: String,
    val paramsJson: String,
    val createdAt: Long,
)

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET status = :status, finishedAt = :finishedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, finishedAt: Long?)
}

@Dao
interface ActionLogDao {
    @Insert
    suspend fun insert(entity: ActionLogEntity): Long

    @Query("SELECT * FROM action_logs WHERE taskId = :taskId ORDER BY timestamp ASC, id ASC")
    fun observe(taskId: Long): Flow<List<ActionLogEntity>>
}

@Dao
interface ScenarioDao {
    @Insert
    suspend fun insert(entity: SavedScenarioEntity): Long

    @Query("SELECT * FROM scenarios ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedScenarioEntity>>

    @Query("DELETE FROM scenarios WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [TaskEntity::class, ActionLogEntity::class, SavedScenarioEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun scenarioDao(): ScenarioDao
}
