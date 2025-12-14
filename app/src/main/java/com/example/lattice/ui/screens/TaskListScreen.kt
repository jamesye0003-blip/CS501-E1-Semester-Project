package com.example.lattice.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.time.TaskFilter
import com.example.lattice.domain.time.filterTasksByDate
import com.example.lattice.ui.components.TaskNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    tasks: List<Task>,
    onAddRoot: () -> Unit,
    onAddSub: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedFilter by rememberSaveable { mutableStateOf(TaskFilter.Today) }
    var hideDescription by rememberSaveable { mutableStateOf(false) }
    var hideCompleted by rememberSaveable { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }

    val filteredTasks = remember(tasks, selectedFilter) { filterTasksByDate(tasks, selectedFilter) }
    val rootIncomplete = remember(filteredTasks) { filteredTasks.filter { it.parentId == null && !it.done } }
    val completedRoots = remember(filteredTasks, hideCompleted) {
        if (hideCompleted) emptyList()
        else filteredTasks.filter { task ->
            task.done && (task.parentId == null ||
                    filteredTasks.firstOrNull { it.id == task.parentId }?.done != true)
        }
    }
    val completedCount = remember(filteredTasks) { filteredTasks.count { it.done } }

    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var recentlyCompletedId by remember { mutableStateOf<String?>(null) }
    var showUndo by remember { mutableStateOf(false) }

    // 如果 Undo 追踪的任务被其他逻辑改回未完成，则自动关闭 Undo
    LaunchedEffect(filteredTasks, recentlyCompletedId) {
        recentlyCompletedId?.let { id ->
            val stillDone = filteredTasks.firstOrNull { it.id == id }?.done == true
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                selectedFilter = selectedFilter,
                onFilterSelected = { filter ->
                    selectedFilter = filter
                    scope.launch { drawerState.close() }
                },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(selectedFilter.getDisplayName()) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(expanded = settingsExpanded, onDismissRequest = { settingsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Hide description") },
                                trailingIcon = { Switch(checked = hideDescription, onCheckedChange = { hideDescription = it }) },
                                onClick = { hideDescription = !hideDescription }
                            )
                            DropdownMenuItem(
                                text = { Text("Hide completed") },
                                trailingIcon = { Switch(checked = hideCompleted, onCheckedChange = { checked -> hideCompleted = checked }) },
                                onClick = { hideCompleted = !hideCompleted }
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddRoot) {
                    Icon(Icons.Filled.Add, contentDescription = "Add task")
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Remove surfaceVariant, use background for clean look
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (rootIncomplete.isEmpty() && completedRoots.isEmpty()) {
                    // Better Empty State
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        Text(
                            text = "All Clear",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        // Add some padding to the list content so it breathes
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Increase spacing
                    ) {
                        items(rootIncomplete, key = { it.id }) { task ->
                            TaskNode(
                                task = task,
                                tasks = filteredTasks,
                                showCompleted = false,
                                hideDescription = hideDescription,
                                onToggleDone = { id ->
                                    val task = filteredTasks.firstOrNull { it.id == id }
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
                                },
                                onAddSub = onAddSub,
                                onEdit = onEdit,
                                onDelete = onDelete
                            )
                        }

                        if (completedRoots.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Completed (${completedCount})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(onClick = { completedExpanded = !completedExpanded }) {
                                        Icon(
                                            imageVector = if (completedExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                            contentDescription = if (completedExpanded) "Collapse completed" else "Expand completed"
                                        )
                                    }
                                }
                            }
                            if (completedExpanded) {
                                items(completedRoots, key = { it.id }) { task ->
                                    TaskNode(
                                        task = task,
                                        tasks = filteredTasks,
                                        showCompleted = true,
                                        hideDescription = hideDescription,
                                        onToggleDone = { id ->
                                            val task = filteredTasks.firstOrNull { it.id == id }
                                            if (task != null && task.done) {
                                                // Moving back to active, hide undo if referencing same task
                                                if (recentlyCompletedId == id) {
                                                    showUndo = false
                                                    recentlyCompletedId = null
                                                }
                                            }
                                            onToggleDone(id)
                                        },
                                        onAddSub = onAddSub,
                                        onEdit = onEdit,
                                        onDelete = onDelete
                                    )
                                }
                            }
                        }
                    }
                }

                // Undo Button (Restored logic)
                AnimatedVisibility(
                    visible = showUndo && recentlyCompletedId != null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    Snackbar(
                        action = {
                            TextButton(onClick = {
                                recentlyCompletedId?.let { id -> onToggleDone(id) }
                                showUndo = false
                                recentlyCompletedId = null
                            }) { Text("Undo") }
                        },
                        dismissAction = {
                            IconButton(onClick = {
                                showUndo = false
                                recentlyCompletedId = null
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                            }
                        }
                    ) { Text("Task completed") }
                }
            }
        }
    }
}

/**
 * Format the enum name of TaskFilter to user-friendly display text.
 */
private fun TaskFilter.getDisplayName(): String = when (this) {
    TaskFilter.Today -> "Today"
    TaskFilter.Tomorrow -> "Tomorrow"
    TaskFilter.Next7Days -> "Next 7 Days"
    TaskFilter.ThisMonth -> "This Month"
    TaskFilter.All -> "All tasks"
}

@Composable
private fun AppDrawerContent(
    selectedFilter: TaskFilter,
    onFilterSelected: (TaskFilter) -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Drawer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Tasks",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Filled.Close, contentDescription = "Close drawer")
                }
            }

            // Filter Options
            TaskFilter.values().forEach { filter ->
                NavigationDrawerItem(
                    label = { Text(filter.getDisplayName()) },
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}