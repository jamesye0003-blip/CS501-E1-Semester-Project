package com.example.lattice.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lattice.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.taskDataStore by preferencesDataStore(name = "tasks")
private val TASKS_KEY = stringPreferencesKey("tasks_payload")

class TaskRepository(private val context: Context) {

    // very-simple serialization: 每行一条任务，字段用 ␞ 分隔，内部对换行/分隔符做转义
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("␞", "\\u241e")
    private fun unesc(s: String) = s.replace("\\u241e", "␞").replace("\\n", "\n").replace("\\\\", "\\")

    private fun serialize(list: List<Task>): String =
        list.joinToString("\n") {
            listOf(
                it.id, esc(it.title), esc(it.notes),
                it.done.toString(), it.parentId ?: ""
            ).joinToString("␞")
        }

    private fun deserialize(payload: String): List<Task> =
        if (payload.isBlank()) emptyList()
        else payload.lines().mapNotNull { line ->
            val parts = line.split("␞")
            if (parts.size < 5) return@mapNotNull null
            Task(
                id = parts[0],
                title = unesc(parts[1]),
                notes = unesc(parts[2]),
                done = parts[3].toBooleanStrictOrNull() ?: false,
                parentId = parts[4].ifBlank { null }
            )
        }

    val tasksFlow: Flow<List<Task>> =
        context.taskDataStore.data.map { prefs ->
            val raw = prefs[TASKS_KEY] ?: ""
            runCatching { deserialize(raw) }.getOrElse { emptyList() }
        }

    suspend fun saveTasks(tasks: List<Task>) {
        val raw = serialize(tasks)
        context.taskDataStore.edit { it[TASKS_KEY] = raw }
    }
}
