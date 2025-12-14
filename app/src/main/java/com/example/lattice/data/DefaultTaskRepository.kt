package com.example.lattice.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.lattice.data.local.datastore.authDataStore
import com.example.lattice.data.local.room.db.AppDatabase
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
            // Using the REPLACE strategy, if the task already exists, update it; if it does not exist, insert it.
            val userId = currentUserIdFlow.first()
            if (userId.isNullOrBlank()) return@withContext

            val entities = TaskMapper.toEntityList(tasks, userId)
            taskDao.insertTasks(entities)
        }
    }

    /**
     * Delete single task (cascade is handled in ViewModel Layer).
     */
    suspend fun deleteTask(id: String) {
        withContext(Dispatchers.IO) {
            taskDao.deleteTaskById(id)
        }
    }

    /**
     * Delete multiple tasks (cascade handled in ViewModel Layer).
     * IMPORTANT: use a single SQL statement to delete by ids.
     */
    override suspend fun deleteTasks(ids: List<String>) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            taskDao.deleteTasksByIds(ids)
        }
    }
}
