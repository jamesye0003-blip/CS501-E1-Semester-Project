package com.example.lattice.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lattice.data.DefaultTaskRepository
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import com.example.lattice.domain.model.toTaskTimeFields
import com.example.lattice.domain.repository.TaskRepository
import com.example.lattice.domain.time.filterTodayTasks
import com.example.lattice.domain.time.TimeConverter
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: TaskRepository = DefaultTaskRepository(app)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())

    /** 推荐使用的新名字 */
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    /** 为了兼容旧代码，保留一个 uiState 的别名 */
    val uiState: StateFlow<List<Task>> get() = tasks

    init {
        // 订阅 Room 数据库，热更新 UI
        viewModelScope.launch {
            repo.tasksFlow.collectLatest { _tasks.value = it }
        }
    }

    private fun saveNow() {
        viewModelScope.launch {
            repo.saveTasks(_tasks.value)
        }
    }

    fun addTask(
        title: String,
        description: String,
        priority: Priority = Priority.None,
        time: TimePoint? = null,
        parentId: String? = null
    ) {
        val (dueAt, hasSpecificTime, sourceTimeZoneId) = time.toTaskTimeFields()
        _tasks.value = _tasks.value + Task(
            title = title,
            description = description,
            priority = priority,
            dueAt = dueAt,
            hasSpecificTime = hasSpecificTime,
            sourceTimeZoneId = sourceTimeZoneId,
            parentId = parentId
        )
        saveNow()
    }

    fun toggleDone(id: String) {
        _tasks.value = _tasks.value.map { if (it.id == id) it.copy(done = !it.done) else it }
        saveNow()
    }

    // —— 供 UI 递归渲染用
    fun childrenOf(parentId: String?): List<Task> =
        _tasks.value.filter { it.parentId == parentId }

    fun updateTask(
        id: String,
        title: String,
        description: String,
        priority: Priority,
        time: TimePoint?
    ) {
        val (dueAt, hasSpecificTime, sourceTimeZoneId) = time.toTaskTimeFields()
        _tasks.value = _tasks.value.map {
            if (it.id == id) {
                it.copy(
                    title = title,
                    description = description,
                    priority = priority,
                    dueAt = dueAt,
                    hasSpecificTime = hasSpecificTime,
                    sourceTimeZoneId = sourceTimeZoneId
                )
            } else {
                it
            }
        }
        saveNow()
    }

    fun deleteTaskCascade(rootId: String) {
        // 收集要删的所有 id（含子孙）
        val toDelete = mutableSetOf(rootId)
        var added: Boolean
        do {
            added = false
            _tasks.value.forEach { t ->
                if (t.parentId != null && t.parentId in toDelete && t.id !in toDelete) {
                    toDelete += t.id
                    added = true
                }
            }
        } while (added)

        _tasks.value = _tasks.value.filterNot { it.id in toDelete }
        saveNow()
    }

    fun postponeTodayTasks() {
        // 使用 TimePointUtils 中的统一"今天任务"逻辑
        val todayTasks = filterTodayTasks(_tasks.value)
        val todayIds = todayTasks.map { it.id }.toSet()
        val systemZone = ZoneId.systemDefault()

        _tasks.value = _tasks.value.map { task ->
            if (!task.done && task.id in todayIds && task.dueAt != null) {
                // 将UTC时刻转换为系统时区，加一天，再转回UTC
                val zonedDateTime = TimeConverter.toZonedDateTime(task.dueAt, systemZone)
                val newZonedDateTime = zonedDateTime.plusDays(1)
                val newDueAt = newZonedDateTime.toInstant()
                task.copy(dueAt = newDueAt)
            } else {
                task
            }
        }
        saveNow()
    }
}
