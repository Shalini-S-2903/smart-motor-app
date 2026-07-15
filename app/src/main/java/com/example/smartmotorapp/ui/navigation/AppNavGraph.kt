package com.example.smartmotorapp.ui.navigation

import DeviceDetailScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.smartmotorapp.MainActivity
import com.example.smartmotorapp.di.BackendProvider
import com.example.smartmotorapp.ui.activity.ActivityScreen
import com.example.smartmotorapp.ui.activity.ActivityViewModel
import com.example.smartmotorapp.ui.adddevice.AddDeviceScreen
import com.example.smartmotorapp.ui.adddevice.AddDeviceViewModel
import com.example.smartmotorapp.ui.detail.DeviceDetailViewModel
import com.example.smartmotorapp.ui.detail.DeviceDetailViewModelFactory
import com.example.smartmotorapp.ui.home.HomeScreen
import com.example.smartmotorapp.ui.home.HomeViewModel
import com.example.smartmotorapp.ui.login.LoginScreen
import com.example.smartmotorapp.ui.login.LoginViewModel
import com.example.smartmotorapp.ui.register.RegisterScreen
import com.example.smartmotorapp.ui.register.RegisterViewModel
import com.example.smartmotorapp.ui.roomdetail.RoomDetailScreen
import com.example.smartmotorapp.ui.roomdetail.RoomDetailViewModel
import com.example.smartmotorapp.ui.splash.SplashScreen
import com.example.smartmotorapp.ui.rooms.RoomCreateScreen
import com.example.smartmotorapp.ui.rooms.RoomCreateViewModel
import com.example.smartmotorapp.ui.schedules.ScheduleCreateScreen
import com.example.smartmotorapp.ui.schedules.ScheduleCreateViewModel
import com.example.smartmotorapp.ui.theme.AppBackground
import com.example.smartmotorapp.ui.theme.InActiveBorder
import com.example.smartmotorapp.ui.theme.InActiveText
import com.example.smartmotorapp.ui.theme.TextPrimary
import com.example.smartmotorapp.ui.theme.White
import com.example.smartmotorapp.ui.theme.cobaltBlue
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Activity : Screen("activity", "Activity", Icons.Default.History)
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    // Only show bottom bar on core pages
    val showBottomBar = currentDestination?.route in listOf(Screen.Home.route, Screen.Activity.route)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    // Important: Matches the overall premium dark background
                    containerColor = White,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(Screen.Home, Screen.Activity)
                    items.forEach { screen ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            // Active icon glows, inactive is muted gray
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) cobaltBlue else InActiveBorder
                                )
                            },
                            // Active text is white, inactive is muted
                            label = {
                                Text(
                                    text = screen.label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) cobaltBlue else InActiveBorder,
                                    fontSize = 12.sp // Ensure it fits the mobile look
                                )
                            },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = AppBackground // Subtle highlight box
                            ),
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
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen(
                    onSplashFinished = {
                        val savedToken = BackendProvider.tokenStore.getAccessToken()
                        val isUserLoggedIn = !savedToken.isNullOrBlank()

                        if (isUserLoggedIn) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo("splash") { inclusive = true }
                            }
                        } else {
                            navController.navigate("login") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable("login") {
                val viewModel: LoginViewModel = viewModel()
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate("register")
                    },
                )
            }

            composable("register") {
                val viewModel: RegisterViewModel = viewModel()
                RegisterScreen(
                    viewModel = viewModel,
                    onRegisterSuccess = {
                        navController.navigate("login") {
                            popUpTo("register") { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    },
                )
            }

            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = viewModel()
                HomeScreen(
                    onDeviceClick = { deviceId -> navController.navigate("deviceDetail/$deviceId") },
                    onRoomClick = { roomId, roomName ->
                        val encodedName = URLEncoder.encode(roomName, Charsets.UTF_8.name())
                        navController.navigate("room/$roomId/$encodedName")
                    },
                    onAddDevice = { navController.navigate("addDevice") },
                    onCreateRoom = { navController.navigate("createRoom") },
                    onScheduleDevice = { deviceId -> navController.navigate("scheduleDevice/$deviceId") },
                    onScheduleRoom = { roomId -> navController.navigate("scheduleRoom/$roomId") },
                    onLogout = {
                        BackendProvider.tokenStore.clear()
                        navController.navigate("login") {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    viewModel = viewModel,
                )

                // We are temporarily showing the StyleGuide instead of the real HomeScreen
                /*StyleGuideScreen(
                    onBack = {
                        // Optional: If you want a way to go back or logout from here
                        BackendProvider.tokenStore.clear()
                        navController.navigate("login") {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )*/
            }

            composable("createRoom") {
                val viewModel: RoomCreateViewModel = viewModel()
                RoomCreateScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel
                )
            }

            composable(Screen.Activity.route) {
                val viewModel: ActivityViewModel = viewModel()
                ActivityScreen(viewModel = viewModel)
            }

            composable(
                route = "scheduleDevice/{deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId")
                ScheduleCreateScreen(
                    deviceId = deviceId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "scheduleRoom/{roomId}",
                arguments = listOf(navArgument("roomId") { type = NavType.IntType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getInt("roomId")
                ScheduleCreateScreen(
                    roomId = roomId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("addDevice") {
                AddDeviceScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "deviceDetail/{deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
                val viewModel: DeviceDetailViewModel = viewModel(
                    factory = DeviceDetailViewModelFactory(deviceId),
                )
                DeviceDetailScreen(
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToScheduler = {},
                    onNavigateToInstantTimer = {}
                )
            }

            composable(
                route = "room/{roomId}/{roomName}",
                arguments = listOf(
                    navArgument("roomId") { type = NavType.IntType },
                    navArgument("roomName") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getInt("roomId") ?: return@composable
                val encodedName = backStackEntry.arguments?.getString("roomName") ?: return@composable
                val roomName = URLDecoder.decode(encodedName, Charsets.UTF_8.name())
                val viewModel: RoomDetailViewModel = viewModel()
                RoomDetailScreen(
                    roomId = roomId,
                    roomName = roomName,
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                )
            }
        }
    }
}
