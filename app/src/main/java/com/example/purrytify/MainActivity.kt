package com.example.purrytify

import HomeScreen
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
import androidx.work.*
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.network.ConnectivityObserver
import com.example.purrytify.data.network.NetworkConnectivityObserver
import com.example.purrytify.ui.components.NetworkPopup
import com.example.purrytify.ui.screens.*
import com.example.purrytify.ui.theme.PurrytifyTheme
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModelFactory
import com.example.purrytify.ui.viewmodel.SongViewModel
import com.example.purrytify.data.worker.TokenAutoRefreshWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize RetrofitClient with AuthInterceptor
        RetrofitClient.initialize(applicationContext)

        // Run token refresh worker
        startTokenAutoRefreshWorker()

        // Run one immediate token refresh when app starts
        runOneTimeTokenRefresh()

        val connectivityObserver = NetworkConnectivityObserver(applicationContext)
        enableEdgeToEdge()
        setContent {
            PurrytifyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val musicViewModel: MusicViewModel = viewModel()
                    val songViewModel: SongViewModel = viewModel()

                    val networkViewModel: NetworkViewModel = viewModel(
                        factory = NetworkViewModelFactory(connectivityObserver)
                    )
                    val status = networkViewModel.status.collectAsState().value

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = "splash"
                        ) {
                            composable("splash") { SplashScreen(navController = navController) }
                            composable("login") { LoginScreen(navController = navController) }
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
                            composable("settings") {
                                SettingsScreen(navController = navController)
                            }
                            composable("player") {
                                MusicPlayerScreen(
                                    musicViewModel = musicViewModel,
                                    onBackClick = { navController.popBackStack() }
                                )
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

    // Worker dipanggil secara terjadwal
    private fun startTokenAutoRefreshWorker() {
        // Run worker every 3 minutes to refresh before 5 min token expiry
        val workRequest = PeriodicWorkRequestBuilder<TokenAutoRefreshWorker>(
            4, TimeUnit.MINUTES
        ).setInitialDelay(30, TimeUnit.SECONDS) // Start after a short delay
         .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "token_auto_refresh_worker",
            ExistingPeriodicWorkPolicy.UPDATE, // Use UPDATE to replace any existing worker
            workRequest
        )
    }

    // Method to manually extend session
    fun extendSession() {
        val oneTimeRequest = OneTimeWorkRequestBuilder<TokenAutoRefreshWorker>()
            .build()
        
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "extend_session_worker",
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }

    // Method to run one immediate token refresh
    private fun runOneTimeTokenRefresh() {
        val oneTimeRequest = OneTimeWorkRequestBuilder<TokenAutoRefreshWorker>()
            .build()
        
        WorkManager.getInstance(applicationContext).enqueue(oneTimeRequest)
    }
}
