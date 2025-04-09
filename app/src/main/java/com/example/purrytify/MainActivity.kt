package com.example.purrytify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.purrytify.ui.screens.*
import com.example.purrytify.ui.theme.PurrytifyTheme
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Create a NavController for navigation
                    val navController = rememberNavController()

                    // Create shared ViewModels
                    val musicViewModel: MusicViewModel = viewModel()
                    val songViewModel: SongViewModel = viewModel()

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
                            HomeScreen(
                                navController = navController,
                                musicViewModel = musicViewModel,
                                onNavigateToPlayer = { navController.navigate("player") }
                            )
                        }

                        composable("library") {
                            LibraryScreen(
                                navController = navController,
                                musicViewModel = musicViewModel,
                                songViewModel = songViewModel,
                                onNavigateToPlayer = { navController.navigate("player") }
                            )
                        }

                        composable("profile") {
                            ProfileScreen(
                                navController = navController,
                                musicViewModel = musicViewModel,
                                onNavigateToPlayer = { navController.navigate("player") }
                            )
                        }

                        // Add the settings screen to the navigation
                        composable("settings") {
                            SettingsScreen(navController = navController)
                        }

                        // Add the music player screen
                        composable("player") {
                            MusicPlayerScreen(
                                musicViewModel = musicViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}