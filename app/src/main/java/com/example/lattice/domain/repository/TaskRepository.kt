package com.example.lattice.domain.repository

import com.example.lattice.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * 领域层任务仓库契约。
 * Domain contract for task repository; storage details are data-layer concerns.
 */
interface TaskRepository {

    /** Single source of truth for all tasks. */
    val tasksFlow: Flow<List<Task>>

    /** Replace/save the current task list. */
    suspend fun saveTasks(tasks: List<Task>)

    /**
     * Delete tasks by ids (cascade handled by caller / ViewModel).
     */
    suspend fun deleteTasks(ids: List<String>)

    suspend fun syncNow(): Result<Unit>
}

