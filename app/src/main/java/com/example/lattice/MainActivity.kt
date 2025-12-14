package com.example.lattice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lattice.ui.navigation.AppNavHost
import com.example.lattice.ui.navigation.Route
import com.example.lattice.ui.navigation.buildEditorRoute
import com.example.lattice.ui.theme.LatticeTheme
import com.example.lattice.viewModel.AuthViewModel
import com.example.lattice.viewModel.TaskViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var forcedDark: Boolean? by remember { mutableStateOf(null) }
            val systemIsDark = isSystemInDarkTheme()

            val isDarkMode = forcedDark ?: systemIsDark
            LatticeTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                val taskViewModel: TaskViewModel = viewModel()

                MainRoot(
                    navController = navController,
                    authViewModel = authViewModel,
                    taskViewModel = taskViewModel,
                    isDarkMode = isDarkMode,
                    onToggleDark = {
                        val current = forcedDark ?: systemIsDark
                        forcedDark = !current
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoot(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    taskViewModel: TaskViewModel,
    isDarkMode: Boolean,
    onToggleDark: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // LoginScreen will not show the bottom navigation bar.
    val showBottomBar = currentRoute != Route.Login.route

    Scaffold(
        bottomBar = {
            if (showBottomBar && authState.isAuthenticated) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    // 检查 editor 路由的 fromBottomNav 参数
                    val isEditorFromBottomNav = navBackStackEntry?.arguments?.getString("fromBottomNav")
                        ?.toBoolean() ?: false

                    val selectedIndex = when {
                        currentRoute == "${Route.Main.route}/${Route.Home.route}" ||
                                currentRoute == Route.Home.route -> 0

                        currentRoute == "${Route.Main.route}/${Route.Calendar.route}" ||
                                currentRoute == Route.Calendar.route -> 1

                        currentRoute?.contains("editor") == true && isEditorFromBottomNav -> 2

                        currentRoute == "${Route.Main.route}/${Route.Profile.route}" ||
                                currentRoute == Route.Profile.route -> 3

                        else -> 0
                    }

                    // Tasks
                    NavigationBarItem(
                        selected = selectedIndex == 0,
                        onClick = {
                            val r = navController.currentBackStackEntry?.destination?.route
                            if (r != Route.Home.route && r != "${Route.Main.route}/${Route.Home.route}") {
                                navController.navigate(Route.Home.route) {
                                    popUpTo(Route.Main.route) { inclusive = false }
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Checklist, contentDescription = "Tasks") },
                        label = { Text("Tasks") }
                    )

                    // Calendar (NEW)
                    NavigationBarItem(
                        selected = selectedIndex == 1,
                        onClick = {
                            val r = navController.currentBackStackEntry?.destination?.route
                            if (r != Route.Calendar.route && r != "${Route.Main.route}/${Route.Calendar.route}") {
                                navController.navigate(Route.Calendar.route) {
                                    popUpTo(Route.Main.route) { inclusive = false }
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = "Calendar") },
                        label = { Text("Calendar") }
                    )

                    // New
                    NavigationBarItem(
                        selected = selectedIndex == 2,
                        onClick = {
                            navController.navigate(buildEditorRoute(null, null, true))
                        },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = "New") },
                        label = { Text("New") }
                    )

                    // Profile
                    NavigationBarItem(
                        selected = selectedIndex == 3,
                        onClick = {
                            val r = navController.currentBackStackEntry?.destination?.route
                            if (r != Route.Profile.route && r != "${Route.Main.route}/${Route.Profile.route}") {
                                navController.navigate(Route.Profile.route) {
                                    popUpTo(Route.Main.route) { inclusive = false }
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text("Profile") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AppNavHost(
                navController = navController,
                authViewModel = authViewModel,
                taskViewModel = taskViewModel,
                isDarkMode = isDarkMode,
                onToggleDark = onToggleDark
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LatticeTheme {
        val navController = rememberNavController()
        val authViewModel: AuthViewModel = viewModel()
        val taskViewModel: TaskViewModel = viewModel()
        AppNavHost(
            navController = navController,
            authViewModel = authViewModel,
            taskViewModel = taskViewModel,
            isDarkMode = isSystemInDarkTheme(),
            onToggleDark = {}
        )
    }
}
