package com.example.lattice.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.lattice.data.local.datastore.authDataStore
import com.example.lattice.data.local.room.db.AppDatabase
import com.example.lattice.data.local.room.entity.TaskSyncStatus
import com.example.lattice.data.local.room.mapper.TaskMapper
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val USER_ID_KEY = stringPreferencesKey("user_id")

/**
 * 使用Room数据库的任务仓库实现。
 * 通过实现 domain.repository.TaskRepository，使 ViewModel 依赖接口而非具体实现。
 * 所有任务操作自动按当前用户过滤。
 *
 * Task repository backed by Room. ViewModels depend on the domain interface only.
 * All task operations are automatically scoped to the current user.
 */
class DefaultTaskRepository(private val context: Context) : TaskRepository {

    private val database = AppDatabase.getDatabase(context)
    private val taskDao = database.taskDao()

    private val currentUserIdFlow: Flow<String?> =
        context.authDataStore.data.map { prefs -> prefs[USER_ID_KEY] }

    override val tasksFlow: Flow<List<Task>> =
        currentUserIdFlow.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                taskDao.getTasksByUserId(userId).map { entities ->
                    TaskMapper.toDomainList(entities)
                }
            }
        }

    override suspend fun saveTasks(tasks: List<Task>) {
        withContext(Dispatchers.IO) {
            val userId = currentUserIdFlow.first()
            if (userId.isNullOrBlank()) return@withContext

            // Get existing tasks to determine if they are new or updates
            val existingTasks = taskDao.getTasksByUserId(userId).first()
            val existingTaskMap = existingTasks.associateBy { it.id }
            
            val now = System.currentTimeMillis()
            val entities = tasks.map { task ->
                val existing = existingTaskMap[task.id]
                if (existing != null) {
                    // Update existing task: preserve sync fields, update syncStatus if needed
                    val entity = TaskMapper.toEntity(task, userId, isNew = false)
                    entity.copy(
                        createdAt = existing.createdAt,
                        lastSyncedAt = existing.lastSyncedAt,
                        remoteId = existing.remoteId,
                        isPostponed = existing.isPostponed,
                        isCancelled = existing.isCancelled,
                        syncStatus = when (existing.syncStatus) {
                            TaskSyncStatus.SYNCED -> TaskSyncStatus.UPDATED
                            TaskSyncStatus.CREATED -> TaskSyncStatus.CREATED  // Keep CREATED if not yet synced
                            TaskSyncStatus.UPDATED -> TaskSyncStatus.UPDATED
                            TaskSyncStatus.DELETED -> TaskSyncStatus.DELETED  // Shouldn't happen, but preserve
                        },
                        updatedAt = now
                    )
                } else {
                    // New task
                    TaskMapper.toEntity(task, userId, isNew = true)
                }
            }
            
            taskDao.insertTasks(entities)
        }
    }

    /**
     * Delete single task (cascade is handled in ViewModel Layer).
     * Uses soft delete: marks task as deleted instead of physical deletion.
     */
    suspend fun deleteTask(id: String) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val syncStatus = TaskSyncStatus.DELETED.name
            taskDao.softDeleteTaskById(id, syncStatus, now)
        }
    }

    /**
     * Delete multiple tasks (cascade handled in ViewModel Layer).
     * Uses soft delete: marks tasks as deleted instead of physical deletion.
     */
    override suspend fun deleteTasks(ids: List<String>) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            val syncStatus = TaskSyncStatus.DELETED.name
            taskDao.softDeleteTasksByIds(ids, syncStatus, now)
        }
    }
}
