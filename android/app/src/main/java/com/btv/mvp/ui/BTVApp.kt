package com.btv.mvp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.btv.mvp.data.AuthManager
import com.btv.mvp.ui.screens.AdminScreen
import com.btv.mvp.ui.screens.HomeScreen
import com.btv.mvp.ui.screens.LoginScreen
import com.btv.mvp.ui.screens.PlayerScreen

@Composable
fun BTVApp() {
    val navController = rememberNavController()
    val startDest = if (AuthManager.isLoggedIn) "home" else "login"

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(navController = navController, startDestination = startDest, modifier = Modifier.padding(innerPadding)) {

            composable("login") {
                LoginScreen(onLoginSuccess = { isAdmin ->
                    val dest = if (isAdmin) "home_admin" else "home"
                    navController.navigate(dest) { popUpTo("login") { inclusive = true } }
                })
            }

            composable("home") {
                HomeScreen(
                    onNavigateToPlayer = { roomId, userId, isHost ->
                        navController.navigate("player/$roomId/$userId/$isHost")
                    },
                    onNavToAdmin = { navController.navigate("admin") },
                    onLogout = {
                        AuthManager.logout()
                        navController.navigate("login") { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composable("home_admin") {
                HomeScreen(
                    onNavigateToPlayer = { roomId, userId, isHost ->
                        navController.navigate("player/$roomId/$userId/$isHost")
                    },
                    onNavToAdmin = { navController.navigate("admin") },
                    onLogout = {
                        AuthManager.logout()
                        navController.navigate("login") { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composable("player/{roomId}/{userId}/{isHost}") { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                val isHost = backStackEntry.arguments?.getString("isHost")?.toBooleanStrictOrNull() ?: false
                PlayerScreen(
                    roomId = roomId,
                    userId = userId,
                    isHost = isHost,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("admin") {
                AdminScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
