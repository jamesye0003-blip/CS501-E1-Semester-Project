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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.time.filterTodayTasks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    username: String,
    tasks: List<Task>,
    isDarkMode: Boolean,
    onToggleDark: () -> Unit,
    onPostponeTodayTasks: () -> Unit,
    onLogout: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDailyReview by remember { mutableStateOf(false) }

    // 统一使用 util 中的“今天任务”过滤逻辑
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

    val totalCount = tasks.size

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Profile menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Dark mode") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.DarkMode,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleDark()
                            },
                            trailingIcon = {
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = {
                                        menuExpanded = false
                                        onToggleDark()
                                    }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null
                                )
                            },
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

            // 概览卡片：用户名 + 已完成任务数量
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Hello, $username",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Completed tasks: $completedCount / $totalCount",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // 今日任务概览
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Today's tasks",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Total: ${todayTasks.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Completed: ${completedTodayTasks.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "To-do: ${todoTodayTasks.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 辅助功能：每日复盘
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        if (showDailyReview) {
            AlertDialog(
                onDismissRequest = { showDailyReview = false },
                title = {
                    Text("Today's Tasks Review")
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Completed (${completedTodayTasks.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (completedTodayTasks.isEmpty()) {
                            Text(
                                text = "No tasks completed today.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                completedTodayTasks.forEach { task ->
                                    Text(
                                        text = "• ${task.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To-do (${todoTodayTasks.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (todoTodayTasks.isEmpty()) {
                            Text(
                                text = "No remaining tasks for today.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                todoTodayTasks.forEach { task ->
                                    Text(
                                        text = "• ${task.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp)
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
