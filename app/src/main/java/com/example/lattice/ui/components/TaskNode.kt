package com.example.lattice.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task

@Composable
fun TaskNode(
    task: Task,
    childrenOf: (String?) -> List<Task>,
    onToggleDone: (String) -> Unit,
    onAddSub: (String) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.done, onCheckedChange = { onToggleDone(task.id) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold)
                    if (task.notes.isNotBlank()) {
                        Text(task.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { onAddSub(task.id) }) { Text("Add subtask") }
            }

            val children = childrenOf(task.id)
            if (children.isNotEmpty()) {
                Column(
                    Modifier.padding(start = 24.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    children.forEach { child ->
                        TaskNode(
                            task = child,
                            childrenOf = childrenOf,
                            onToggleDone = onToggleDone,
                            onAddSub = onAddSub
                        )
                    }
                }
            }
        }
    }
}
