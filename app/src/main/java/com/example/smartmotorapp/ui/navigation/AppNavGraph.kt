package com.example.smartmotorapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartmotorapp.ui.home.HomeScreen

/**
 * Navigation: backend-driven device list only (no Tuya SDK, no login).
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(
                onDeviceClick = { },
                onAddDevice = { },
            )
        }
    }
}
