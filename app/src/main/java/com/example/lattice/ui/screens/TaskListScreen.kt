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
import androidx.compose.material3.HorizontalDivider
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
import com.example.lattice.domain.sort.TaskSortOrder
import com.example.lattice.domain.sort.compareByTime
import com.example.lattice.domain.sort.compareTasks
import com.example.lattice.domain.sort.sortTasksByLayer
import com.example.lattice.domain.time.TaskFilter
import com.example.lattice.domain.time.filterTasksByDate
import com.example.lattice.domain.time.getDisplayName
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
    
    // Sync UI sortOrder with persisted preference from DataStore.
    // Update UI state when sortOrderState changes.
    LaunchedEffect(sortOrderState) {
        sortOrder = sortOrderState
    }
    
    // Persist sortOrder to DataStore when it changes.
    // Keeps user's sort preference across app restarts.
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

    // Dismiss Undo if tracked task is no longer completed.
    // Prevents stale Undo actions and ensures UI consistency.
    LaunchedEffect(filteredTasks, recentlyCompletedId) {
        recentlyCompletedId?.let { id ->
            val stillDone = filteredTasks.firstOrNull { it.id == id }?.done == true
            if (!stillDone) {
                showUndo = false
                recentlyCompletedId = null
            }
        }
    }

    // Auto-dismiss Undo snackbar after 5s if visible and task is tracked.
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
                            HorizontalDivider()
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
                        // Show TaskListCard for filtered to-do/root tasks if there are any incomplete root tasks
                        // (i.e., display the section for active/incomplete tasks if the filtered list is non-empty)
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
                        
                        // Show TaskListCard for filtered completed root tasks if there are any completed root tasks.
                        // (i.e., display the section for completed tasks if the filtered list contains any completed root-level tasks)
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