package com.example.lattice.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lattice.data.DefaultTaskRepository
import com.example.lattice.domain.model.Attachment
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import com.example.lattice.domain.model.toTaskTimeFields
import com.example.lattice.domain.repository.TaskRepository
import com.example.lattice.domain.time.TaskFilter
import com.example.lattice.domain.time.TimeConverter
import com.example.lattice.domain.time.filterTodayTasks
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Task related state & actions.
 */
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: TaskRepository = DefaultTaskRepository(application.applicationContext)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _selectedFilter = MutableStateFlow<TaskFilter>(TaskFilter.Today)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    init {
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
        parentId: String? = null,
        attachments: List<Attachment> = emptyList()
    ) {
        val (dueAt, hasSpecificTime, sourceTimeZoneId) = time.toTaskTimeFields()

        _tasks.value = _tasks.value + Task(
            title = title,
            description = description,
            priority = priority,
            dueAt = dueAt,
            hasSpecificTime = hasSpecificTime,
            sourceTimeZoneId = sourceTimeZoneId,
            parentId = parentId,
            attachments = attachments
        )
        saveNow()
    }

    fun toggleDone(id: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) task.copy(done = !task.done) else task
        }
        saveNow()
    }

    fun childrenOf(parentId: String?): List<Task> =
        _tasks.value.filter { it.parentId == parentId }

    fun updateTask(
        id: String,
        title: String,
        description: String,
        priority: Priority = Priority.None,
        time: TimePoint? = null,
        attachments: List<Attachment> = emptyList()
    ) {
        val (dueAt, hasSpecificTime, sourceTimeZoneId) = time.toTaskTimeFields()

        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) {
                task.copy(
                    title = title,
                    description = description,
                    priority = priority,
                    dueAt = dueAt,
                    hasSpecificTime = hasSpecificTime,
                    sourceTimeZoneId = sourceTimeZoneId,
                    attachments = attachments
                )
            } else {
                task
            }
        }
        saveNow()
    }

    /**
     * Cascade delete:
     * 1) collect all descendant ids
     * 2) optimistic remove from UI
     * 3) delete from Room (source of truth)
     */
    fun deleteTaskCascade(rootId: String) {
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

        // Optimistic UI update
        _tasks.value = _tasks.value.filterNot { it.id in toDelete }

        // Real delete in DB (critical fix)
        viewModelScope.launch {
            repo.deleteTasks(toDelete.toList())
            // No saveNow(): Room will emit new list via tasksFlow
        }
    }

    fun postponeTodayTasks() {
        // 使用 TimePointUtils 中的统一"今天任务"逻辑
        val todayTasks = filterTodayTasks(_tasks.value)
        val todayIds = todayTasks.map { it.id }.toSet()

        val systemZone = ZoneId.systemDefault()
        val postponedIds = mutableListOf<String>()

        _tasks.value = _tasks.value.map { task ->
            if (!task.done && task.id in todayIds && task.dueAt != null) {
                // 将UTC时刻转换为系统时区，加一天，再转回UTC
                val zonedDateTime = TimeConverter.toZonedDateTime(task.dueAt, systemZone)
                val newZonedDateTime = zonedDateTime.plusDays(1)
                val newDueAt = newZonedDateTime.toInstant()
                postponedIds.add(task.id)
                task.copy(dueAt = newDueAt)
            } else {
                task
            }
        }
        saveNow()
        
        // Update isPostponed status in database
        if (postponedIds.isNotEmpty()) {
            viewModelScope.launch {
                val defaultRepo = repo as? com.example.lattice.data.DefaultTaskRepository
                defaultRepo?.updatePostponedStatus(postponedIds, isPostponed = true)
            }
        }
    }

    fun setSelectedFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }
    fun syncNow() {
        viewModelScope.launch {
            repo.syncNow()
        }
    }
}
