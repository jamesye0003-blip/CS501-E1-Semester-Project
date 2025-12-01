package com.example.lattice.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import com.example.lattice.ui.components.TaskNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class TaskFilter {
    Today,
    Tomorrow,
    Next7Days,
    ThisMonth,
    All
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    state: StateFlow<List<Task>>,
    onAddRoot: () -> Unit,
    onAddSub: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val tasks by state.collectAsState()
    
    val drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedFilter by rememberSaveable { mutableStateOf(TaskFilter.Today) }

    var hideDescription by rememberSaveable { mutableStateOf(false) }
    var hideCompleted by rememberSaveable { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    
    // 根据筛选条件过滤任务
    val filteredTasks = remember(tasks, selectedFilter) {
        filterTasksByDate(tasks, selectedFilter)
    }

    val rootIncomplete = remember(filteredTasks) { filteredTasks.filter { it.parentId == null && !it.done } }
    val completedRoots = remember(filteredTasks, hideCompleted) {
        if (hideCompleted) emptyList()
        else filteredTasks.filter { task ->
            task.done && (task.parentId == null || filteredTasks.firstOrNull { it.id == task.parentId }?.done != true)
        }
    }
    val completedCount = remember(filteredTasks) { filteredTasks.count { it.done } }

    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var recentlyCompletedId by remember { mutableStateOf<String?>(null) }
    var showUndo by remember { mutableStateOf(false) }

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

    val handleToggle: (String) -> Unit = remember(filteredTasks) {
        { id ->
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
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                selectedFilter = selectedFilter,
                onFilterSelected = { filter ->
                    selectedFilter = filter
                    scope.launch {
                        drawerState.close()
                    }
                },
                onCloseDrawer = {
                    scope.launch {
                        drawerState.close()
                    }
                }
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("TaskList") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isClosed) {
                                    drawerState.open()
                                } else {
                                    drawerState.close()
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = settingsExpanded,
                            onDismissRequest = { settingsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Hide description") },
                                trailingIcon = {
                                    Switch(
                                        checked = hideDescription,
                                        onCheckedChange = { hideDescription = it }
                                    )
                                },
                                onClick = { hideDescription = !hideDescription }
                            )
                            DropdownMenuItem(
                                text = { Text("Hide completed") },
                                trailingIcon = {
                                    Switch(
                                        checked = hideCompleted,
                                        onCheckedChange = { hideCompleted = it }
                                    )
                                },
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
        ) {
            if (rootIncomplete.isEmpty() && completedRoots.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No tasks yet. Tap + to add a root task.")
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            tasks = filteredTasks,
                            showCompleted = false,
                            hideDescription = hideDescription,
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
                                    tasks = filteredTasks,
                                    showCompleted = true,
                                    hideDescription = hideDescription,
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

            AnimatedVisibility(
                visible = showUndo && recentlyCompletedId != null,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            ) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Undo")
                    }
                }
            }
        }
        }
    }
}

@Composable
fun AppDrawerContent(
    selectedFilter: TaskFilter,
    onFilterSelected: (TaskFilter) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier.fillMaxWidth(0.8f)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter Tasks", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Drawer")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            val filterItems = listOf(
                TaskFilter.Today to "Today",
                TaskFilter.Tomorrow to "Tomorrow",
                TaskFilter.Next7Days to "Next 7 Days",
                TaskFilter.ThisMonth to "This Month",
                TaskFilter.All to "All tasks"
            )

            filterItems.forEach { (filter, label) ->
                NavigationDrawerItem(
                    label = { Text(label) },
                    selected = selectedFilter == filter,
                    onClick = {
                        onFilterSelected(filter)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}

private fun filterTasksByDate(tasks: List<Task>, filter: TaskFilter): List<Task> {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val today = now.toLocalDate()
    
    return when (filter) {
        TaskFilter.Today -> {
            tasks.filter { task ->
                task.time?.let { timePoint ->
                    val taskDate = if (timePoint.time != null) {
                        // 如果有时间，需要转换时区
                        ZonedDateTime.of(timePoint.date, timePoint.time, timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                    } else {
                        // 如果只有日期，需要考虑时区
                        val taskInSystemZone = timePoint.date.atStartOfDay(timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                        taskInSystemZone
                    }
                    taskDate == today
                } ?: false // 没有时间的任务不显示在Today中
            }
        }
        TaskFilter.Tomorrow -> {
            val tomorrow = today.plusDays(1)
            tasks.filter { task ->
                task.time?.let { timePoint ->
                    val taskDate = if (timePoint.time != null) {
                        ZonedDateTime.of(timePoint.date, timePoint.time, timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                    } else {
                        timePoint.date.atStartOfDay(timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    taskDate == tomorrow
                } ?: false
            }
        }
        TaskFilter.Next7Days -> {
            val endDate = today.plusDays(7)
            tasks.filter { task ->
                task.time?.let { timePoint ->
                    val taskDate = if (timePoint.time != null) {
                        ZonedDateTime.of(timePoint.date, timePoint.time, timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                    } else {
                        timePoint.date.atStartOfDay(timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    taskDate >= today && taskDate <= endDate
                } ?: false
            }
        }
        TaskFilter.ThisMonth -> {
            val firstDayOfMonth = today.withDayOfMonth(1)
            val lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth())
            tasks.filter { task ->
                task.time?.let { timePoint ->
                    val taskDate = if (timePoint.time != null) {
                        ZonedDateTime.of(timePoint.date, timePoint.time, timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                    } else {
                        timePoint.date.atStartOfDay(timePoint.zoneId)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    taskDate >= firstDayOfMonth && taskDate <= lastDayOfMonth
                } ?: false
            }
        }
        TaskFilter.All -> tasks
    }
}
