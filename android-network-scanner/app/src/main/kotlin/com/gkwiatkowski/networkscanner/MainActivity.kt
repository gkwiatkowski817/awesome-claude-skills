package com.gkwiatkowski.networkscanner

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gkwiatkowski.networkscanner.ui.MainViewModel
import com.gkwiatkowski.networkscanner.ui.screens.DashboardScreen
import com.gkwiatkowski.networkscanner.ui.screens.DevicesScreen
import com.gkwiatkowski.networkscanner.ui.screens.HistoryScreen
import com.gkwiatkowski.networkscanner.ui.screens.NetworkMapScreen
import com.gkwiatkowski.networkscanner.ui.theme.NetworkScannerTheme

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Devices : Screen("devices", "Devices", Icons.Default.Devices)
    object Map : Screen("map", "Network Map", Icons.Default.AccountTree)
    object History : Screen("history", "History", Icons.Default.History)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Devices,
    Screen.Map,
    Screen.History
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled gracefully in scanners */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRequiredPermissions()

        setContent {
            NetworkScannerTheme {
                NetworkScannerApp(viewModel = viewModel)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            @Suppress("DEPRECATION")
            permissions.add(Manifest.permission.BLUETOOTH)
            @Suppress("DEPRECATION")
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun NetworkScannerApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        topBar = {
            val currentScreen = bottomNavItems.find {
                currentDestination?.hierarchy?.any { d -> d.route == it.route } == true
            }
            TopAppBar(
                title = { Text(currentScreen?.title ?: "Network Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(viewModel = viewModel)
                }
                composable(Screen.Devices.route) {
                    DevicesScreen(viewModel = viewModel)
                }
                composable(Screen.Map.route) {
                    NetworkMapScreen(viewModel = viewModel)
                }
                composable(Screen.History.route) {
                    HistoryScreen(viewModel = viewModel)
                }
            }
        }
    }
}
