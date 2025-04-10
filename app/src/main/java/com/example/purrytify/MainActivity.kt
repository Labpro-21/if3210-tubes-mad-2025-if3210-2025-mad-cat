package com.example.purrytify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.purrytify.data.network.ConnectivityObserver
import com.example.purrytify.data.network.NetworkConnectivityObserver
import com.example.purrytify.ui.components.NetworkPopup
import com.example.purrytify.ui.viewmodel.NetworkViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModelFactory
import com.example.purrytify.ui.screens.*
import com.example.purrytify.ui.theme.PurrytifyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val connectivityObserver = NetworkConnectivityObserver(applicationContext)
        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val networkViewModel: NetworkViewModel = viewModel(
                        factory = NetworkViewModelFactory(connectivityObserver)
                    )
                    val status = networkViewModel.status.collectAsState().value

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = "splash"
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
                                ProfileScreen(navController = navController, isConnected = status == ConnectivityObserver.Status.Available)
                            }

                            // Add the settings screen to the navigation
                            composable("settings") {
                                SettingsScreen(navController = navController)
                            }
                        }

                        if (status != ConnectivityObserver.Status.Available) {
                            NetworkPopup(
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}