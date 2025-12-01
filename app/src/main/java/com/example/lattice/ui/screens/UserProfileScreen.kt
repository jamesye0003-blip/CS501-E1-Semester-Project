package com.example.lattice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    username: String,
    tasksState: StateFlow<List<Task>>,
    isDarkMode: Boolean,
    onToggleDark: () -> Unit,
    onPostponeTodayTasks: () -> Unit,
    onLogout: () -> Unit
) {
    val tasks by tasksState.collectAsState()
    
    var menuExpanded by remember { mutableStateOf(false) }
    var showDailyReview by remember { mutableStateOf(false) }
    
    // 过滤今天的任务
    val todayTasks = remember(tasks) {
        filterTodayTasks(tasks)
    }
    
    val completedTodayTasks = remember(todayTasks) {
        todayTasks.filter { it.done }
    }
    
    val todoTodayTasks = remember(todayTasks) {
        todayTasks.filter { !it.done }
    }
    
    val completedCount = remember(tasks) {
        tasks.count { it.done }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Dark mode") },
                            leadingIcon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onToggleDark()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onLogout()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 用户信息 Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Username: $username",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Completed Tasks: $completedCount",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            // 辅助功能列表
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDailyReview = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "Daily Review",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
        
        // Daily Review AlertDialog
        if (showDailyReview) {
            AlertDialog(
                onDismissRequest = { showDailyReview = false },
                title = { Text("Daily Review") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Completed Tasks 部分
                        Column {
                            Text(
                                text = "Completed Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (completedTodayTasks.isEmpty()) {
                                Text(
                                    text = "No completed tasks today",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                completedTodayTasks.forEach { task ->
                                    Text(
                                        text = "• ${task.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // To-do Tasks 部分
                        Column {
                            Text(
                                text = "To-do Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (todoTodayTasks.isEmpty()) {
                                Text(
                                    text = "No to-do tasks today",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                todoTodayTasks.forEach { task ->
                                    Text(
                                        text = "• ${task.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onPostponeTodayTasks()
                            showDailyReview = false
                        }
                    ) {
                        Text("Postpone")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDailyReview = false }
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}

private fun filterTodayTasks(tasks: List<Task>): List<Task> {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val today = now.toLocalDate()
    
    return tasks.filter { task ->
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
            taskDate == today
        } ?: false
    }
}

