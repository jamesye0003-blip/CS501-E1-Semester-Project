package com.example.lattice.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.lattice.ui.screens.CalendarScreen
import com.example.lattice.ui.screens.EditorScreen
import com.example.lattice.ui.screens.LoginScreen
import com.example.lattice.ui.screens.RegisterScreen
import com.example.lattice.ui.screens.TaskListScreen
import com.example.lattice.ui.screens.UserProfileScreen
import com.example.lattice.domain.model.Attachment
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.toTimePoint
import com.example.lattice.viewModel.AuthViewModel
import com.example.lattice.viewModel.TaskViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    taskViewModel: TaskViewModel,
    isDarkMode: Boolean,
    onToggleDark: () -> Unit
) {
    // Collect flows once at this layer
    val authState by authViewModel.uiState.collectAsState()
    val tasks by taskViewModel.tasks.collectAsState()

    // Pick start destination by auth state
    val startDestination = if (authState.isAuthenticated) {
        Route.Main.route
    } else {
        Route.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Login screen
        composable(Route.Login.route) {
            LoginScreen(
                // When login succeed, navigate to the Main screen.
                onLoginSuccess = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
                onNavigateRegister = { navController.navigate(Route.Register.route) },
                viewModel = authViewModel
            )
        }

        // Register screen
        composable(Route.Register.route) {
            RegisterScreen(
                // When register succeed, navigate to the Main screen.
                onRegisterSuccess = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }

        // Main app (requires authentication)
        navigation(
            startDestination = Route.Home.route,
            route = Route.Main.route
        ) {
            // Home (Task List) screen
            composable(Route.Home.route) {
                val selectedFilter by taskViewModel.selectedFilter.collectAsState()
                TaskListScreen(
                    tasks = tasks,
                    selectedFilter = selectedFilter,
                    onFilterSelected = { filter -> taskViewModel.setSelectedFilter(filter) },
                    // EDITOR_ROUTE: Create a new task from the bottom navigation bar
                    onAddRoot = { navController.navigate(buildEditorRoute(null, null, true)) },
                    // EDITOR_ROUTE: Add a subtask from TaskListScreen 
                    onAddSub = { parentId -> navController.navigate(buildEditorRoute(parentId, null, false)) },
                    onToggleDone = { id -> taskViewModel.toggleDone(id) },
                    // EDITOR_ROUTE: Edit a task from TaskListScreen or CalendarScreen
                    onEdit = { id -> navController.navigate(buildEditorRoute(null, id, false)) },
                    onDelete = { id -> taskViewModel.deleteTaskCascade(id) }
                )
            }

            // Calendar screen
            composable(Route.Calendar.route) {
                CalendarScreen(
                    tasks = tasks,
                    // EDITOR_ROUTE: Edit a task from TaskListScreen or CalendarScreen
                    onEdit = { id -> navController.navigate(buildEditorRoute(null, id, false)) },
                    // EDITOR_ROUTE: Create a new task from CalendarScreen
                    onAddTask = { navController.navigate(buildEditorRoute(null, null, false)) }
                )
            }
            
            // Editor screen, composed depending on the specific navigation arguments
            composable("editor?parent={parent}&editId={editId}&fromBottomNav={fromBottomNav}") { backStackEntry ->
                val parentId = backStackEntry.arguments
                    ?.getString("parent")
                    ?.ifBlank { null }
                val editId = backStackEntry.arguments
                    ?.getString("editId")
                    ?.ifBlank { null }
                val fromBottomNav = backStackEntry.arguments
                    ?.getString("fromBottomNav")
                    ?.toBoolean() ?: false

                // Preload task when editing
                val editing = tasks.firstOrNull { it.id == editId }

                EditorScreen(
                    initialTitle = editing?.title ?: "",
                    initialDescription = editing?.description ?: "",
                    initialPriority = editing?.priority ?: Priority.None,
                    initialTime = editing?.toTimePoint(),
                    initialAttachments = editing?.attachments ?: emptyList(),
                    primaryLabel = if (editing != null) "Update" else "Save",
                    parentId = parentId,
                    fromBottomNav = fromBottomNav,
                    onBack = { navController.popBackStack() },
                    onSave = { title, description, priority, time, attachments ->
                        if (editing != null) {
                            taskViewModel.updateTask(
                                id = editing.id,
                                title = title,
                                description = description,
                                priority = priority,
                                time = time,
                                attachments = attachments
                            )
                        } else {
                            taskViewModel.addTask(
                                title = title,
                                description = description,
                                priority = priority,
                                time = time,
                                parentId = parentId,
                                attachments = attachments
                            )
                        }
                        navController.popBackStack()
                    }
                )
            }
            
            // User Profile screen
            composable(Route.Profile.route) {
                UserProfileScreen(
                    username = authState.user?.username ?: "User",
                    tasks = tasks,
                    isDarkMode = isDarkMode,
                    onToggleDark = onToggleDark,
                    onPostponeTodayTasks = { taskViewModel.postponeTodayTasks() },
                    onSyncNow = { taskViewModel.syncNow() },
                    // When logout, navigate to the Login screen.
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Route.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }

    // Listen whether user is authenticated. Force nav to login when unauthenticated
    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated &&
            navController.currentDestination?.route != Route.Login.route
        ) {
            navController.navigate(Route.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
}
