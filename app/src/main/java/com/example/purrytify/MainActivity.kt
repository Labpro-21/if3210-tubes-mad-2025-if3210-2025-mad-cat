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
    private var deepLinkSongId: Int? = null
    private var deepLinkNavigationPending = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        handleIntent(intent)

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
        
        RetrofitClient.initialize(applicationContext)
        startTokenAutoRefreshWorker()
        runOneTimeTokenRefresh()

        val connectivityObserver = NetworkConnectivityObserver(applicationContext)
        enableEdgeToEdge()
        
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
                    var startDestination by remember { mutableStateOf("splash") }
                    var goToPlayerAfterSplash by remember { mutableStateOf(shouldNavigateToPlayer) }
                    val context = LocalContext.current
                    LaunchedEffect(navController) {
                        if (deepLinkNavigationPending && deepLinkSongId != null) {
                            Log.d(TAG, "Processing deep link navigation for song ID: $deepLinkSongId")
                            
                            try {
                                val trendingApi = RetrofitClient.getInstance(context).create(TrendingApiService::class.java)
                                val response = trendingApi.getSongById(deepLinkSongId!!)
                                
                                if (response.isSuccessful) {
                                    response.body()?.let { onlineSong ->
                                        Log.d(TAG, "Successfully fetched song: ${onlineSong.title}")
                                        
                                        val song = Song(
                                            title = onlineSong.title,
                                            artist = onlineSong.artist,
                                            coverUri = onlineSong.artworkUrl,
                                            uri = onlineSong.audioUrl,
                                            duration = onlineSong.duration
                                        )
                                        
                                        val deepLinkPlaylist = listOf(song)
                                        musicViewModel.setOnlinePlaylist(deepLinkPlaylist, "deeplink")
                                        delay(500)
                                        
                                        if (navController.currentBackStackEntry?.destination?.route != "player") {
                                            navController.navigate("player") {
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        
                                        delay(100)
                                        musicViewModel.loadSongWithoutPlaying(
                                            song, 
                                            context, 
                                            fromOnlinePlaylist = true, 
                                            onlineType = "deeplink",
                                            onlineSongId = deepLinkSongId
                                        )
                                        
                                        deepLinkNavigationPending = false
                                        deepLinkSongId = null
                                    }
                                } else {
                                    Log.e(TAG, "Failed to fetch song: ${response.code()}")
                                    deepLinkNavigationPending = false
                                    deepLinkSongId = null
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching song for deep link", e)
                                deepLinkNavigationPending = false
                                deepLinkSongId = null
                            }
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
                            
                            addTopChartsNavigation(
                                navController = navController,
                                musicViewModel = musicViewModel,
                                songViewModel = songViewModel,
                                onNavigateToPlayer = { navController.navigate("player") }
                            )
                            
                            composable("mix_playlist/{mixName}") { backStackEntry ->
                                val mixName = backStackEntry.arguments?.getString("mixName") ?: "Your Daily Mix"
                                MixPlaylistScreen(
                                    navController = navController,
                                    mixName = mixName,
                                    musicViewModel = musicViewModel,
                                    onNavigateToPlayer = { navController.navigate("player") }
                                )
                            }
                            
                            composable("top_songs") {
                                TopSongsScreen(
                                    navController = navController,
                                    musicViewModel = musicViewModel,
                                    songViewModel = songViewModel,
                                    onNavigateToPlayer = { navController.navigate("player") }
                                )
                            }
                            
                            composable("time_listened") {
                                TimeListenedScreen(
                                    navController = navController,
                                    musicViewModel = musicViewModel,
                                    songViewModel = songViewModel,
                                    onNavigateToPlayer = { navController.navigate("player") }
                                )
                            }
                            
                            composable("top_artists") {
                                TopArtistsScreen(
                                    navController = navController,
                                    musicViewModel = musicViewModel,
                                    songViewModel = songViewModel,
                                    onNavigateToPlayer = { navController.navigate("player") }
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
        
        try {
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
                data.scheme == "purrytify" && data.host == "song" -> {
                    songId = data.lastPathSegment?.toIntOrNull()
                }
                data.scheme == "https" && data.host == "purrytify.com" && data.path?.startsWith("/open/song") == true -> {
                    songId = data.lastPathSegment?.toIntOrNull()
                }
            }
            
            if (songId != null) {
                Log.d(TAG, "Handling deep link for song ID: $songId")
                deepLinkSongId = songId
                deepLinkNavigationPending = true
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        
        try {
            val intent = Intent(this, MediaPlaybackService::class.java)
            stopService(intent)
            Log.d(TAG, "Music service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping music service", e)
        }
    }

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
}