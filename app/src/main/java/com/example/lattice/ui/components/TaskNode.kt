package com.example.lattice.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import com.example.lattice.domain.model.Task

@Composable
fun TaskNode(
    task: Task,
    tasks: List<Task>,
    onToggleDone: (String) -> Unit,
    onAddSub: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.done, onCheckedChange = { onToggleDone(task.id) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold)
                    if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodySmall)
                }

                // ⋮ 溢出菜单
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Add subtask") },
                            onClick = { menuExpanded = false; onAddSub(task.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { menuExpanded = false; onEdit(task.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuExpanded = false; onDelete(task.id) },
                            // 可选：强调删除色
                            trailingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }

            val children = remember(tasks, task.id) { tasks.filter { it.parentId == task.id } }
            if (children.isNotEmpty()) {
                Column(Modifier.padding(start = 24.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    children.forEach { child ->
                        TaskNode(
                            task = child,
                            tasks = tasks,
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
