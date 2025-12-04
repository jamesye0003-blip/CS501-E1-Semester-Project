package com.example.lattice.domain.repository

import com.example.lattice.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * 领域层任务仓库契约。
 * 暴露任务流和保存接口，具体存储细节交给 data 层实现。
 */
interface TaskRepository {

    /** 所有任务列表的单一数据源（SSOT）。 */
    val tasksFlow: Flow<List<Task>>

    /** 覆盖式保存当前任务列表。 */
    suspend fun saveTasks(tasks: List<Task>)
}

