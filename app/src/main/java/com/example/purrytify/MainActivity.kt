package com.example.purrytify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.purrytify.ui.screens.*
import com.example.purrytify.ui.theme.PurrytifyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Create a NavController for navigation
                    val navController = rememberNavController()

                    // Set up the NavHost with the navigation graph
                    NavHost(
                        navController = navController,
                        startDestination = "splash" // Start with splash screen
                    ) {
                        composable("splash") {
                            SplashScreen(navController = navController)
                        }

                        composable("login") {
                            LoginScreen(navController = navController)
                        }

                        composable("home") {
                            HomeScreen(navController = navController)
                        }

                        composable("library") {
                            LibraryScreen(navController = navController)
                        }

                        composable("profile") {
                            ProfileScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}