package com.example.lattice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import com.example.lattice.ui.components.TaskNode
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TaskListScreen(
    state: StateFlow<List<Task>>,
    onAddRoot: () -> Unit,
    onAddSub: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    childrenOf: (String?) -> List<Task>
) {
    val tasks by state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val roots = remember(tasks) { childrenOf(null) }
        if (roots.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tasks yet. Tap + to add a root task.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(roots, key = { it.id }) { t ->
                    TaskNode(
                        task = t,
                        childrenOf = childrenOf,
                        onToggleDone = onToggleDone,
                        onAddSub = onAddSub
                    )
                }
            }
        }
    }
}
