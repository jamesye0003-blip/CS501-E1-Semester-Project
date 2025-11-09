package com.example.lattice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.lattice.ui.theme.LatticeTheme
import com.example.lattice.ui.navigation.AppNavHost
import com.example.lattice.ui.navigation.Route
import com.example.lattice.ui.navigation.buildEditorRoute
import com.example.lattice.viewModel.AuthViewModel
import com.example.lattice.viewModel.TaskViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var forcedDark: Boolean? by remember { mutableStateOf(null) }
            val systemIsDark = isSystemInDarkTheme()

            LatticeTheme(darkTheme = forcedDark ?: systemIsDark) {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                val taskViewModel: TaskViewModel = viewModel()
                
                AppNavHost(
                    navController = navController,
                    authViewModel = authViewModel,
                    taskViewModel = taskViewModel
                )
                
                // 只在已认证时显示MainScreen
                val authState by authViewModel.uiState.collectAsState()
                if (authState.isAuthenticated) {
                    MainScreen(
                        navController = navController,
                        taskViewModel = taskViewModel,
                        authViewModel = authViewModel,
                        onToggleDark = {
                            val current = forcedDark ?: systemIsDark
                            forcedDark = !current
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    taskViewModel: TaskViewModel,
    authViewModel: AuthViewModel,
    onToggleDark: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    
    // 同步导航状态和底部导航栏选中状态
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { controller, destination, arguments ->
            when (destination.route) {
                Route.Home.route -> selectedIndex = 0
                else -> {
                    if (destination.route?.startsWith("editor") == true) {
                        selectedIndex = 1
                    }
                }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lattice - Task Scheduler") },
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
                                authViewModel.logout()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                NavigationBarItem(
                    selected = selectedIndex == 0,
                    onClick = { 
                        selectedIndex = 0
                        if (navController.currentBackStackEntry?.destination?.route != Route.Home.route) {
                            navController.navigate(Route.Home.route) {
                                popUpTo(Route.Home.route) { inclusive = false }
                            }
                        }
                    },
                    icon = { Icon(Icons.Filled.Checklist, contentDescription = "Tasks") },
                    label = { Text("Tasks") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = { 
                        selectedIndex = 1
                        navController.navigate(buildEditorRoute(null))
                    },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = "Input") },
                    label = { Text("New") }
                )
            }
        },
        floatingActionButton = {
            if (navController.currentBackStackEntry?.destination?.route == Route.Home.route) {
                FloatingActionButton(
                    onClick = { navController.navigate(buildEditorRoute(null)) }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add task")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AppNavHost(
                navController = navController,
                authViewModel = authViewModel,
                taskViewModel = taskViewModel
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
            taskViewModel = taskViewModel
        )
    }
}