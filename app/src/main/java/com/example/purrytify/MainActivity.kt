package com.example.purrytify

import HomeScreen
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import kotlinx.coroutines.delay
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.models.ProfileResponse
import com.example.purrytify.data.network.ConnectivityObserver
import com.example.purrytify.data.network.NetworkConnectivityObserver
import com.example.purrytify.service.MediaPlaybackService
import com.example.purrytify.ui.components.NetworkPopup
import com.example.purrytify.navigation.addTopChartsNavigation
import com.example.purrytify.ui.screens.*
import com.example.purrytify.ui.theme.PurrytifyTheme
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModelFactory
import com.example.purrytify.ui.viewmodel.SongViewModel
import com.example.purrytify.data.worker.TokenAutoRefreshWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.purrytify.data.api.TrendingApiService
import com.example.purrytify.ui.screens.Song

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val musicViewModel by viewModels<MusicViewModel>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        // Handle deep links
        handleIntent(intent)

        // Request post notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
        
        // Initialize RetrofitClient with AuthInterceptor
        RetrofitClient.initialize(applicationContext)

        // Run token refresh worker
        startTokenAutoRefreshWorker()

        // Run one immediate token refresh when app starts
        runOneTimeTokenRefresh()

        val connectivityObserver = NetworkConnectivityObserver(applicationContext)
        enableEdgeToEdge()
        
        // Check if we're being launched from a notification
        val shouldNavigateToPlayer = intent?.action == "OPEN_PLAYER_SCREEN"
        Log.d(TAG, "Should navigate to player: $shouldNavigateToPlayer")
        
        setContent {
            PurrytifyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val songViewModel: SongViewModel = viewModel()

                    val networkViewModel: NetworkViewModel = viewModel(
                        factory = NetworkViewModelFactory(connectivityObserver)
                    )
                    val status = networkViewModel.status.collectAsState().value
                    
                    // Provide a way to navigate to player when needed
                    var startDestination by remember { mutableStateOf("splash") }
                    
                    // If coming from notification, go straight to player after splash
                    var goToPlayerAfterSplash by remember { mutableStateOf(shouldNavigateToPlayer) }
                    
                    val context = LocalContext.current
                    
                    // Check for deep link
                    LaunchedEffect(navController) {
                        if (deepLinkPending && deepLinkSong != null) {
                            Log.d(TAG, "Processing deep link in composition")
                            
                            // First ensure we have a playlist context
                            val dummyPlaylist = listOf(deepLinkSong!!)
                            musicViewModel.setOnlinePlaylist(dummyPlaylist, "deeplink")
                            
                            // Load song without playing - just display it in the player screen
                            musicViewModel.loadSongWithoutPlaying(
                                deepLinkSong!!, 
                                context, 
                                fromOnlinePlaylist = true, 
                                onlineType = "deeplink",
                                onlineSongId = deepLinkSongId
                            )
                            
                            // Navigate to player after a small delay to ensure NavHost is ready
                            delay(200)
                            navController.navigate("player")
                            
                            deepLinkPending = false
                            deepLinkSong = null
                            deepLinkSongId = null
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable("splash") { 
                                SplashScreen(
                                    navController = navController,
                                    onNavigationComplete = {
                                        if (goToPlayerAfterSplash) {
                                            Log.d(TAG, "Navigating to player after splash")
                                            navController.navigate("player")
                                            goToPlayerAfterSplash = false
                                        }
                                    }
                                ) 
                            }
                            composable("login") { LoginScreen(navController = navController, songViewModel = songViewModel) }
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
                                SettingsScreen(navController = navController, musicViewModel = musicViewModel)
                            }
                            composable("player") {
                                MusicPlayerScreen(
                                    musicViewModel = musicViewModel,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            composable("edit_profile") {
                                val profileData = navController.previousBackStackEntry?.savedStateHandle?.get<ProfileResponse>("profileData")
                                EditProfileScreen(
                                    navController = navController,
                                    profileData = profileData
                                )
                            }
                            
                            composable("qr_scanner") {
                                val context = LocalContext.current
                                QRScannerScreen(
                                    onSongScanned = { songId ->
                                        // Handle the scanned song
                                        lifecycleScope.launch {
                                            try {
                                                val trendingApi = RetrofitClient.getInstance(context).create(TrendingApiService::class.java)
                                                val response = trendingApi.getSongById(songId)
                                                if (response.isSuccessful) {
                                                    response.body()?.let { onlineSong ->
                                                        val song = Song(
                                                            title = onlineSong.title,
                                                            artist = onlineSong.artist,
                                                            coverUri = onlineSong.artworkUrl,
                                                            uri = onlineSong.audioUrl,
                                                            duration = onlineSong.duration
                                                        )
                                                        musicViewModel.playSong(song, context, fromOnlinePlaylist = true, onlineType = "", onlineSongId = onlineSong.id)
                                                        navController.navigate("player")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error fetching song from QR scan", e)
                                            }
                                        }
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            
                            // Add top charts navigation routes
                            addTopChartsNavigation(
                                navController = navController,
                                musicViewModel = musicViewModel,
                                songViewModel = songViewModel,
                                onNavigateToPlayer = { navController.navigate("player") }
                            )
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
        
        try {
            // Initialize music playback controller with more error handling
            val songViewModel: SongViewModel = SongViewModel(application)
            musicViewModel.initializePlaybackControls(songViewModel, this)
            Log.d(TAG, "Initialized music controller")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing music controller", e)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")
        setIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null) {
            var songId: Int? = null
            
            when {
                // Handle purrytify://song/{id}
                data.scheme == "purrytify" && data.host == "song" -> {
                    songId = data.lastPathSegment?.toIntOrNull()
                }
                // Handle https://purrytify.com/open/song/{id}
                data.scheme == "https" && data.host == "purrytify.com" && data.path?.startsWith("/open/song") == true -> {
                    songId = data.lastPathSegment?.toIntOrNull()
                }
            }
            
            if (songId != null) {
                Log.d(TAG, "Handling deep link for song ID: $songId")
                // Clear any previous deep link data
                deepLinkSong = null
                deepLinkPending = false
                deepLinkSongId = null
                
                // Load the song and navigate to player
                lifecycleScope.launch {
                    try {
                        val trendingApi = RetrofitClient.getInstance(applicationContext).create(TrendingApiService::class.java)
                        val response = trendingApi.getSongById(songId)
                        if (response.isSuccessful) {
                            response.body()?.let { onlineSong ->
                                Log.d(TAG, "Successfully fetched song: ${onlineSong.title}")
                                
                                // Convert OnlineSong to Song
                                val song = Song(
                                    title = onlineSong.title,
                                    artist = onlineSong.artist,
                                    coverUri = onlineSong.artworkUrl,
                                    uri = onlineSong.audioUrl,
                                    duration = onlineSong.duration
                                )
                                
                                // Store deep link data
                                deepLinkSong = song
                                deepLinkPending = true
                                deepLinkSongId = songId
                            }
                        } else {
                            Log.e(TAG, "Failed to fetch song: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching song for deep link", e)
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        
        // Stop the music service when app is closed
        try {
            val intent = Intent(this, MediaPlaybackService::class.java)
            stopService(intent)
            Log.d(TAG, "Music service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping music service", e)
        }
    }

    // Worker dipanggil secara terjadwal
    private fun startTokenAutoRefreshWorker() {
        val workRequest = PeriodicWorkRequestBuilder<TokenAutoRefreshWorker>(
            4, TimeUnit.MINUTES
        ).setInitialDelay(30, TimeUnit.SECONDS)
         .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "token_auto_refresh_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun extendSession() {
        val oneTimeRequest = OneTimeWorkRequestBuilder<TokenAutoRefreshWorker>()
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "extend_session_worker",
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }

    private fun runOneTimeTokenRefresh() {
        val oneTimeRequest = OneTimeWorkRequestBuilder<TokenAutoRefreshWorker>()
            .build()

        WorkManager.getInstance(applicationContext).enqueue(oneTimeRequest)
    }
    
    companion object {
        var deepLinkSong: Song? = null
        var deepLinkPending: Boolean = false
        var deepLinkSongId: Int? = null
    }
}