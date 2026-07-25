package com.btv.mvp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.btv.mvp.ui.screens.HomeScreen
import com.btv.mvp.ui.screens.PlayerScreen

@Composable
fun BTVApp() {
    val navController = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToPlayer = { roomId, userId, isHost ->
                        navController.navigate(
                            "player/$roomId/$userId/$isHost"
                        ) {
                            popUpTo("home") { inclusive = false }
                        }
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
                    onBack = {
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }
        }
    }
}
