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

    /**
     * 更新任务的延期状态 / Update postponed status for tasks
     * 
     * 更新指定任务的延期状态，并确保其变为"脏"状态以便同步。
     * 
     * Update postponed status for specific tasks, and ensure it becomes "dirty" for sync.
     * 
     * @param ids 任务 ID 列表 / List of task IDs
     * @param isPostponed 是否延期 / Whether tasks are postponed
     */
    suspend fun updatePostponedStatus(ids: List<String>, isPostponed: Boolean)

    suspend fun syncNow(): Result<Unit>
}

