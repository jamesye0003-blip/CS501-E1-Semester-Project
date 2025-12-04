package com.example.lattice.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import com.example.lattice.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Context.taskDataStore by preferencesDataStore(name = "tasks")
private val TASKS_KEY = stringPreferencesKey("tasks_payload")

/**
 * 使用 Preferences DataStore + 自定义字符串序列化的任务仓库默认实现。
 * 通过实现 domain.repository.TaskRepository，使 ViewModel 依赖接口而非具体实现。
 */
class DefaultTaskRepository(private val context: Context) : TaskRepository {

    // very-simple serialization: 每行一条任务，字段用 ␞ 分隔，内部对换行/分隔符做转义
    private fun esc(s: String) =
        s.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("␞", "\\u241e")

    private fun unesc(s: String) =
        s.replace("\\u241e", "␞")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")

    private fun serialize(list: List<Task>): String =
        list.joinToString("\n") {
            listOf(
                it.id,
                esc(it.title),
                esc(it.description),
                it.done.toString(),
                it.parentId ?: "",
                it.priority.name,
                esc(it.time?.toPayload() ?: "")
            ).joinToString("␞")
        }

    private fun deserialize(payload: String): List<Task> =
        if (payload.isBlank()) emptyList()
        else payload.lines().mapNotNull { line ->
            val parts = line.split("␞")
            if (parts.size < 5) return@mapNotNull null
            val rawDescription = parts.getOrNull(2) ?: ""
            val parentId = parts.getOrNull(4)?.ifBlank { null }
            val rawPriority = parts.getOrNull(5)
            val priority = rawPriority
                ?.let { runCatching { Priority.valueOf(it) }.getOrElse { Priority.None } }
                ?: Priority.None
            val rawTime = parts.getOrNull(6)?.let { unesc(it) }.orEmpty()
            Task(
                id = parts[0],
                title = unesc(parts[1]),
                description = unesc(rawDescription),
                priority = priority,
                time = TimePoint.fromPayload(rawTime),
                done = parts[3].toBooleanStrictOrNull() ?: false,
                parentId = parentId
            )
        }

    override val tasksFlow: Flow<List<Task>> =
        context.taskDataStore.data.map { prefs ->
            val raw = prefs[TASKS_KEY] ?: ""
            runCatching { deserialize(raw) }.getOrElse { emptyList() }
        }

    override suspend fun saveTasks(tasks: List<Task>) {
        withContext(Dispatchers.IO) {
            val raw = serialize(tasks)
            context.taskDataStore.edit { it[TASKS_KEY] = raw }
        }
    }
}
