package com.example.lattice.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.lattice.ui.screens.EditorScreen
import com.example.lattice.ui.screens.LoginScreen
import com.example.lattice.ui.screens.TaskListScreen
import com.example.lattice.ui.screens.UserProfileScreen
import com.example.lattice.domain.model.Priority
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
    // 统一在这一层收集来自 ViewModel 的 Flow
    val authState by authViewModel.uiState.collectAsState()
    val tasks by taskViewModel.tasks.collectAsState()

    // 根据认证状态确定起始路由
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
                onLoginSuccess = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        // Main app (requires authentication)
        navigation(
            startDestination = Route.Home.route,
            route = Route.Main.route
        ) {
            composable(Route.Home.route) {
                TaskListScreen(
                    tasks = tasks,
                    onAddRoot = { navController.navigate(buildEditorRoute(null)) },
                    onAddSub = { parentId -> navController.navigate(buildEditorRoute(parentId)) },
                    onToggleDone = { id -> taskViewModel.toggleDone(id) },
                    onEdit = { id -> navController.navigate(buildEditorRoute(editId = id)) },
                    onDelete = { id -> taskViewModel.deleteTaskCascade(id) }
                )
            }

            composable("editor?parent={parent}&editId={editId}") { backStackEntry ->
                val parentId = backStackEntry.arguments
                    ?.getString("parent")
                    ?.ifBlank { null }
                val editId = backStackEntry.arguments
                    ?.getString("editId")
                    ?.ifBlank { null }

                // 如果是编辑，从当前 tasks 中找到要编辑的任务做预填
                val editing = tasks.firstOrNull { it.id == editId }

                EditorScreen(
                    initialTitle = editing?.title ?: "",
                    initialDescription = editing?.description ?: "",
                    initialPriority = editing?.priority ?: Priority.None,
                    initialTime = editing?.time,
                    primaryLabel = if (editing != null) "Update" else "Save",
                    onBack = { navController.popBackStack() },
                    onSave = { title, description, priority, time ->
                        if (editing != null) {
                            taskViewModel.updateTask(
                                id = editing.id,
                                title = title,
                                description = description,
                                priority = priority,
                                time = time
                            )
                        } else {
                            taskViewModel.addTask(
                                title = title,
                                description = description,
                                priority = priority,
                                time = time,
                                parentId = parentId
                            )
                        }
                        navController.popBackStack()
                    }
                )
            }

            composable(Route.Profile.route) {
                UserProfileScreen(
                    username = authState.user?.username ?: "User",
                    tasks = tasks,
                    isDarkMode = isDarkMode,
                    onToggleDark = onToggleDark,
                    onPostponeTodayTasks = { taskViewModel.postponeTodayTasks() },
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

    // 认证状态变为未登录时，强制返回 Login
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
