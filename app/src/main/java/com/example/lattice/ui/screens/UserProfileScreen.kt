package com.example.lattice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.time.filterTodayTasks
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.lattice.data.DefaultTaskRepository
import com.example.lattice.data.local.datastore.authDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    var showDailyReview by remember { mutableStateOf(false) }

    val todayTasks = remember(tasks) { filterTodayTasks(tasks) }
    val completedToday = remember(todayTasks) { todayTasks.filter { it.done } }
    val todoToday = remember(todayTasks) { todayTasks.filter { !it.done } }

    val totalCompletedLifetime = remember(tasks) { tasks.count { it.done } }
    val totalTasksLifetime = tasks.size

    // Get completed task statistics (on-time vs postponed)
    var onTimeCompletedCount by remember { mutableStateOf(0) }
    var postponedCompletedCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(tasks) {
        // Get current user ID from DataStore
        val userId = withContext(Dispatchers.IO) {
            context.authDataStore.data.first()[stringPreferencesKey("user_id")]
        }
        
        if (userId != null) {
            val repo = DefaultTaskRepository(context)
            val (onTime, postponed) = repo.getCompletedTaskStats(userId)
            onTimeCompletedCount = onTime
            postponedCompletedCount = postponed
        }
    }
    
    val totalCompleted = onTimeCompletedCount + postponedCompletedCount
    val onTimeRate = if (totalCompleted > 0) (onTimeCompletedCount * 100 / totalCompleted) else 0
    val postponeRate = if (totalCompleted > 0) (postponedCompletedCount * 100 / totalCompleted) else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background, // 与背景融合
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    // Clean logout icon
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. User Identity Card (Big & Bold)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = username.take(1).uppercase(),
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Productivity Enthusiast",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 2. Today's Overview Section
            item {
                Text(
                    "Today's Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Today Left
                    StatCard(
                        label = "To-Do",
                        value = todoToday.size.toString(),
                        icon = Icons.Default.Schedule,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    // Today Done
                    StatCard(
                        label = "Completed",
                        value = completedToday.size.toString(),
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF2E7D32), // Success Green
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Lifetime Stats Section
            item {
                Text(
                    "Lifetime Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Total Tasks",
                        value = totalTasksLifetime.toString(),
                        icon = Icons.Default.Assignment,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Completion Rate",
                        value = if (totalTasksLifetime > 0) "${(totalCompletedLifetime * 100 / totalTasksLifetime)}%" else "0%",
                        icon = Icons.Default.Person, // Or chart icon
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "On-time Rate",
                        value = "$onTimeRate%",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF2E7D32), // Success Green
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Postpone Rate",
                        value = "$postponeRate%",
                        icon = Icons.Default.Schedule,
                        color = Color(0xFFFF9800), // Orange
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Settings / Actions Section
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, // Subtle background
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Dark Mode") },
                            leadingContent = {
                                Icon(
                                    if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    null
                                )
                            },
                            trailingContent = {
                                Switch(checked = isDarkMode, onCheckedChange = { onToggleDark() })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        ListItem(
                            headlineContent = { Text("Daily Review") },
                            supportingContent = { Text("Postpone unfinished tasks to tomorrow") },
                            leadingContent = { Icon(Icons.Default.Assignment, null) },
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(),
                                    onClick = { showDailyReview = true }
                                ),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }

    // (AlertDialog code remains mostly the same, just keeping the logic)
    if (showDailyReview) {
        AlertDialog(
            onDismissRequest = { showDailyReview = false },
            title = { Text("Review Today") },
            text = {
                Text("Move ${todoToday.size} unfinished tasks to tomorrow?")
            },
            confirmButton = {
                TextButton(onClick = {
                    onPostponeTodayTasks()
                    showDailyReview = false
                }) { Text("Postpone All") }
            },
            dismissButton = {
                TextButton(onClick = { showDailyReview = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface, // Clean white/dark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}