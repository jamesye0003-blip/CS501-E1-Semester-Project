package com.example.lattice.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lattice.data.TaskRepository
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository(app)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val uiState: StateFlow<List<Task>> = _tasks.asStateFlow()

    init {
        // 订阅 DataStore，热更新 UI
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
        _tasks.value = _tasks.value + Task(
            title = title,
            description = description,
            priority = priority,
            time = time,
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
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(
                title = title,
                description = description,
                priority = priority,
                time = time
            ) else it
        }
        saveNow() // 或 saveNow()
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
}

