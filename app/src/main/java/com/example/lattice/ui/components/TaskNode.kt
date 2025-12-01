package com.example.lattice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TaskNode(
    task: Task,
    tasks: List<Task>,
    showCompleted: Boolean,
    hideDescription: Boolean = false,
    onToggleDone: (String) -> Unit,
    onAddSub: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .alpha(if (showCompleted) 0.6f else 1f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val checkboxColor = priorityColor(task.priority)
                Checkbox(
                    checked = task.done,
                    onCheckedChange = { onToggleDone(task.id) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = checkboxColor,
                        uncheckedColor = checkboxColor,
                        checkmarkColor = Color.White,
                        disabledCheckedColor = checkboxColor.copy(alpha = 0.4f),
                        disabledUncheckedColor = checkboxColor.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    val titleColor = if (showCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                    val secondaryColor = if (showCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

                    Text(task.title, fontWeight = FontWeight.SemiBold, color = titleColor)
                    if (!hideDescription && task.description.isNotBlank()) {
                        Text(task.description, style = MaterialTheme.typography.bodySmall, color = secondaryColor)
                    }
                    val timeText = remember(task.time) { task.time?.let { formatTimePointForList(it) } }
                    if (!timeText.isNullOrBlank()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (showCompleted) secondaryColor else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!showCompleted) {
                            DropdownMenuItem(
                                text = { Text("Add subtask") },
                                onClick = { menuExpanded = false; onAddSub(task.id) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { menuExpanded = false; onEdit(task.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuExpanded = false; onDelete(task.id) },
                            trailingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }

            val children = remember(tasks, task.id, showCompleted) {
                tasks.filter { it.parentId == task.id && it.done == showCompleted }
            }
            if (children.isNotEmpty()) {
                Column(
                    Modifier.padding(start = 24.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    children.forEach { child ->
                        TaskNode(
                            task = child,
                            tasks = tasks,
                            showCompleted = showCompleted,
                            hideDescription = hideDescription,
                            onToggleDone = onToggleDone,
                            onAddSub = onAddSub,
                            onEdit = onEdit,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.High -> Color(0xFFE53935)
    Priority.Medium -> Color(0xFFFFB300)
    Priority.Low -> Color(0xFF1E88E5)
    Priority.None -> MaterialTheme.colorScheme.outline
}

private val LOCAL_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun formatTimePointForList(timePoint: TimePoint): String {
    val systemZone: ZoneId = ZoneId.systemDefault()
    val zoneLabel = timePoint.zoneId.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val zoneSuffix = if (timePoint.zoneId == systemZone) null else zoneLabel

    if (timePoint.time == null) {
        val nowInStored = ZonedDateTime.now(timePoint.zoneId)
        val label = when (timePoint.date) {
            nowInStored.toLocalDate() -> "Today"
            nowInStored.plusDays(1).toLocalDate() -> "Tomorrow"
            else -> timePoint.date.toString()
        }
        return listOfNotNull(label, zoneSuffix).joinToString(" ")
    }

    val eventInStoredZone = ZonedDateTime.of(timePoint.date, timePoint.time, timePoint.zoneId)
    val eventInLocalZone = eventInStoredZone.withZoneSameInstant(systemZone)

    val nowLocal = ZonedDateTime.now(systemZone)
    val label = when (eventInLocalZone.toLocalDate()) {
        nowLocal.toLocalDate() -> "Today"
        nowLocal.plusDays(1).toLocalDate() -> "Tomorrow"
        else -> eventInLocalZone.toLocalDate().toString()
    }

    val timePart = eventInLocalZone.toLocalTime().format(LOCAL_TIME_FORMATTER)

    return listOfNotNull(label, timePart, zoneSuffix).joinToString(" ")
}
