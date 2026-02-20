package com.example.smartmotorapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartmotorapp.di.BackendProvider
import com.example.smartmotorapp.ui.adddevice.AddDeviceScreen
import com.example.smartmotorapp.ui.adddevice.AddDeviceViewModel
import com.example.smartmotorapp.ui.detail.DeviceDetailScreen
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
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = "splash",
    ) {
        composable("splash") {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
            )
        }

        composable("login") {
            val viewModel: LoginViewModel = viewModel()
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
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

        composable("home") {
            val viewModel: HomeViewModel = viewModel()
            HomeScreen(
                onDeviceClick = { deviceId -> navController.navigate("deviceDetail/$deviceId") },
                onRoomClick = { roomId, roomName ->
                    val encodedName = URLEncoder.encode(roomName, Charsets.UTF_8.name())
                    navController.navigate("room/$roomId/$encodedName")
                },
                onAddDevice = { navController.navigate("addDevice") },
                onLogout = {
                    BackendProvider.tokenStore.clear()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                viewModel = viewModel,
            )
        }

        composable("addDevice") {
            val viewModel: AddDeviceViewModel = viewModel()
            AddDeviceScreen(
                viewModel = viewModel,
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
