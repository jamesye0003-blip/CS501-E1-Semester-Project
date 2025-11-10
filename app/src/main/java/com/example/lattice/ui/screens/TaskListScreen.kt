package com.example.lattice.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import com.example.lattice.ui.components.TaskNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TaskListScreen(
    state: StateFlow<List<Task>>,
    onAddRoot: () -> Unit,
    onAddSub: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val tasks by state.collectAsState()

    val rootIncomplete = remember(tasks) { tasks.filter { it.parentId == null && !it.done } }
    val completedRoots = remember(tasks) {
        tasks.filter { task ->
            task.done && (task.parentId == null || tasks.firstOrNull { it.id == task.parentId }?.done != true)
        }
    }
    val completedCount = remember(tasks) { tasks.count { it.done } }

    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var recentlyCompletedId by remember { mutableStateOf<String?>(null) }
    var showUndo by remember { mutableStateOf(false) }

    LaunchedEffect(tasks, recentlyCompletedId) {
        recentlyCompletedId?.let { id ->
            val stillDone = tasks.firstOrNull { it.id == id }?.done == true
            if (!stillDone) {
                showUndo = false
                recentlyCompletedId = null
            }
        }
    }

    LaunchedEffect(showUndo, recentlyCompletedId) {
        if (showUndo && recentlyCompletedId != null) {
            delay(5000)
            showUndo = false
            recentlyCompletedId = null
        }
    }

    val handleToggle: (String) -> Unit = remember(tasks) {
        { id ->
            val task = tasks.firstOrNull { it.id == id }
            if (task != null && !task.done) {
                // Moving to completed
                recentlyCompletedId = id
                showUndo = true
            } else if (task != null && task.done) {
                // Moving back to active, hide undo if referencing same task
                if (recentlyCompletedId == id) {
                    showUndo = false
                    recentlyCompletedId = null
                }
            }
            onToggleDone(id)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (rootIncomplete.isEmpty() && completedRoots.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No tasks yet. Tap + to add a root task.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    if (rootIncomplete.isEmpty()) {
                        Text(
                            text = "All tasks completed!",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                items(rootIncomplete, key = { it.id }) { task ->
                    TaskNode(
                        task = task,
                        tasks = tasks,
                        showCompleted = false,
                        onToggleDone = handleToggle,
                        onAddSub = onAddSub,
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }

                if (completedRoots.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp, bottom = 8.dp)
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Completed ($completedCount)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(onClick = { completedExpanded = !completedExpanded }) {
                                    Icon(
                                        imageVector = if (completedExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                        contentDescription = if (completedExpanded) "Collapse completed" else "Expand completed"
                                    )
                                }
                            }
                        }
                    }

                    if (completedExpanded) {
                        items(completedRoots, key = { it.id }) { task ->
                            TaskNode(
                                task = task,
                                tasks = tasks,
                                showCompleted = true,
                                onToggleDone = handleToggle,
                                onAddSub = onAddSub,
                                onEdit = onEdit,
                                onDelete = onDelete
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showUndo && recentlyCompletedId != null, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            FloatingActionButton(
                onClick = {
                    recentlyCompletedId?.let {
                        onToggleDone(it)
                    }
                    showUndo = false
                    recentlyCompletedId = null
                },
                containerColor = Color(0xFFFFD740),
                contentColor = Color.Black
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Undo")
                }
            }
        }
    }
}
