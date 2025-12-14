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
import androidx.compose.material3.Divider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.lattice.data.local.datastore.settingsDataStore
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.toTimePoint
import com.example.lattice.domain.time.TaskFilter
import com.example.lattice.domain.time.TaskSortOrder
import com.example.lattice.domain.time.filterTasksByDate
import com.example.lattice.ui.components.TaskListCard
import com.example.lattice.ui.components.TaskNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    tasks: List<Task>,
    selectedFilter: TaskFilter,
    onFilterSelected: (TaskFilter) -> Unit,
    onAddRoot: () -> Unit,
    onAddSub: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var hideDescription by rememberSaveable { mutableStateOf(false) }
    var hideCompleted by rememberSaveable { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    
    // Load sort order from DataStore
    val sortOrderKey = stringPreferencesKey("task_sort_order")
    val sortOrderFlow = remember {
        context.settingsDataStore.data.map { prefs ->
            prefs[sortOrderKey]?.let { 
                try {
                    TaskSortOrder.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    TaskSortOrder.Title
                }
            } ?: TaskSortOrder.Title
        }
    }
    val sortOrderState by sortOrderFlow.collectAsState(initial = TaskSortOrder.Title)
    var sortOrder by remember(sortOrderState) { mutableStateOf(sortOrderState) }
    
    LaunchedEffect(sortOrderState) {
        sortOrder = sortOrderState
    }
    
    LaunchedEffect(sortOrder) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[sortOrderKey] = sortOrder.name
            }
        }
    }

    val filteredTasks = remember(tasks, selectedFilter) { filterTasksByDate(tasks, selectedFilter) }
    val rootIncomplete = remember(filteredTasks, sortOrder) { 
        sortTasksByLayer(filteredTasks.filter { it.parentId == null && !it.done }, filteredTasks, sortOrder)
    }
    val completedRoots = remember(filteredTasks, hideCompleted, sortOrder) {
        if (hideCompleted) emptyList()
        else sortTasksByLayer(
            filteredTasks.filter { task ->
                task.done && (task.parentId == null ||
                        filteredTasks.firstOrNull { it.id == task.parentId }?.done != true)
            },
            filteredTasks,
            sortOrder
        )
    }
    val completedCount = remember(filteredTasks) { filteredTasks.count { it.done } }
    val incompleteCount = remember(rootIncomplete) { rootIncomplete.size }

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
                    onFilterSelected(filter)
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
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Sort by Title") },
                                leadingIcon = { RadioButton(selected = sortOrder == TaskSortOrder.Title, onClick = null) },
                                onClick = { sortOrder = TaskSortOrder.Title }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Priority") },
                                leadingIcon = { RadioButton(selected = sortOrder == TaskSortOrder.Priority, onClick = null) },
                                onClick = { sortOrder = TaskSortOrder.Priority }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Time") },
                                leadingIcon = { RadioButton(selected = sortOrder == TaskSortOrder.Time, onClick = null) },
                                onClick = { sortOrder = TaskSortOrder.Time }
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
                        if (rootIncomplete.isNotEmpty()) {
                            item {
                                TaskListCard(
                                    title = "${selectedFilter.getDisplayName()} ($incompleteCount)",
                                    tasks = rootIncomplete,
                                    filteredTasks = filteredTasks,
                                    showCompleted = false,
                                    hideDescription = hideDescription,
                                    sortOrder = sortOrder,
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
                        }

                        if (completedRoots.isNotEmpty()) {
                            item {
                                TaskListCard(
                                    title = "Completed ($completedCount)",
                                    tasks = completedRoots,
                                    filteredTasks = filteredTasks,
                                    showCompleted = true,
                                    hideDescription = hideDescription,
                                    sortOrder = sortOrder,
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

/**
 * Sort tasks by layer recursively according to the specified sort order.
 * Tasks at the same layer are sorted together, and their children are sorted recursively.
 */
private fun sortTasksByLayer(
    tasks: List<Task>,
    allTasks: List<Task>,
    sortOrder: TaskSortOrder
): List<Task> {
    // allTasks is kept for potential future use in sorting logic
    if (tasks.isEmpty()) return emptyList()
    
    // Sort tasks at current layer
    return tasks.sortedWith { t1, t2 ->
        compareTasks(t1, t2, sortOrder)
    }
}

/**
 * Compare two tasks according to the specified sort order.
 * Returns negative if t1 < t2, positive if t1 > t2, zero if equal.
 */
private fun compareTasks(
    t1: Task,
    t2: Task,
    sortOrder: TaskSortOrder
): Int {
    return when (sortOrder) {
        TaskSortOrder.Title -> {
            // Sort by title (dictionary order, A before Z)
            val titleCompare = t1.title.compareTo(t2.title, ignoreCase = true)
            if (titleCompare != 0) titleCompare
            else 0 // If titles are equal, maintain original order
        }
        TaskSortOrder.Priority -> {
            // Sort by priority: High > Medium > Low > None
            val priorityOrder = mapOf(
                Priority.High to 0,
                Priority.Medium to 1,
                Priority.Low to 2,
                Priority.None to 3
            )
            val priorityCompare = (priorityOrder[t1.priority] ?: 3).compareTo(priorityOrder[t2.priority] ?: 3)
            if (priorityCompare != 0) priorityCompare
            else {
                // Same priority, compare by time
                compareByTime(t1, t2)
            }
        }
        TaskSortOrder.Time -> {
            // Sort by time: tasks with time before tasks without time
            // Same time, compare by priority
            // Same time and priority, compare by title
            val timeCompare = compareByTime(t1, t2)
            if (timeCompare != 0) timeCompare
            else {
                // Same time, compare by priority
                val priorityOrder = mapOf(
                    Priority.High to 0,
                    Priority.Medium to 1,
                    Priority.Low to 2,
                    Priority.None to 3
                )
                val priorityCompare = (priorityOrder[t1.priority] ?: 3).compareTo(priorityOrder[t2.priority] ?: 3)
                if (priorityCompare != 0) priorityCompare
                else {
                    // Same time and priority, compare by title
                    t1.title.compareTo(t2.title, ignoreCase = true)
                }
            }
        }
    }
}

/**
 * Compare two tasks by time.
 * Tasks with time come before tasks without time.
 * Returns negative if t1 < t2, positive if t1 > t2, zero if equal.
 */
private fun compareByTime(t1: Task, t2: Task): Int {
    val time1 = t1.toTimePoint()
    val time2 = t2.toTimePoint()
    
    // Tasks without time come after tasks with time
    if (time1 == null && time2 == null) return 0
    if (time1 == null) return 1 // t1 has no time, t2 has time -> t1 > t2
    if (time2 == null) return -1 // t1 has time, t2 has no time -> t1 < t2
    
    // Both have time, compare by date first
    val dateCompare = time1.date.compareTo(time2.date)
    if (dateCompare != 0) return dateCompare
    
    // Same date, compare by time if both have specific time
    if (time1.time != null && time2.time != null) {
        return time1.time.compareTo(time2.time)
    }
    
    // One or both don't have specific time, but same date
    if (time1.time != null) return -1 // t1 has time, t2 doesn't -> t1 < t2
    if (time2.time != null) return 1 // t1 doesn't have time, t2 has -> t1 > t2
    
    return 0 // Both have same date but no specific time
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