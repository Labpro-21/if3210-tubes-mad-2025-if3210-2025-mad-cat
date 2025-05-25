package com.example.purrytify.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.purrytify.R
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.models.ProfileResponse
import com.example.purrytify.data.network.ConnectivityObserver
import com.example.purrytify.data.network.NetworkConnectivityObserver
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.components.AdaptiveNavigation
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModelFactory
import com.example.purrytify.ui.viewmodel.SongViewModel
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.*
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.purrytify.utils.PdfExportUtil
import com.example.purrytify.utils.CsvExportUtil
import com.example.purrytify.ui.screens.ListeningAnalytics.MonthlyAnalytics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.purrytify.data.preferences.UserProfileManager
import com.example.purrytify.data.preferences.UserProfile
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.purrytify.ui.components.BottomNavBar
import java.io.File

object ListenedSongsTracker {
    private val userListenedSongsMap = mutableMapOf<String, MutableSet<String>>()

    fun getListenedCount(email: String): Int {
        return userListenedSongsMap[email]?.size ?: 0
    }

    fun addSong(email: String, songKey: String, context: android.content.Context): Boolean {
        val userSet = userListenedSongsMap.getOrPut(email) { mutableSetOf() }
        val isNew = userSet.add(songKey)
        if (isNew) {
            userListenedSongsMap[email] = userSet
            saveListenedSongs(email, context)
        }
        return isNew
    }

    fun loadListenedSongs(email: String, context: android.content.Context) {
        val tokenManager = TokenManager(context)
        val raw = tokenManager.getString("listened_songs_$email")
        raw?.let { rawString ->
            userListenedSongsMap[email] = rawString
                .split("|")
                .filter { it.isNotBlank() }
                .toMutableSet()
        }
    }

    private fun saveListenedSongs(email: String, context: android.content.Context) {
        val tokenManager = TokenManager(context)
        val joined = userListenedSongsMap[email]?.joinToString("|") ?: ""
        tokenManager.saveString("listened_songs_$email", joined)
    }
    
    fun getListenedSongs(email: String): List<Song> {
        return userListenedSongsMap[email]?.map { songKey ->
            val parts = songKey.split("_")
            val title = parts.getOrNull(0) ?: ""
            val artist = parts.getOrNull(1) ?: ""
            val coverUri = parts.getOrNull(2) ?: ""
            val uri = parts.getOrNull(3) ?: ""
            val duration = parts.getOrNull(4) ?: "0"
            
            Song(
                title = title,
                artist = artist,
                coverUri = coverUri,
                uri = uri,
                duration = duration
            )
        } ?: emptyList()
    }
}

@Composable
fun ProfileScreen(
    navController: NavController,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val userProfileManager = remember { UserProfileManager(context) }

    val connectivityObserver = remember { NetworkConnectivityObserver(context) }
    val networkViewModel: NetworkViewModel = viewModel(
        factory = NetworkViewModelFactory(connectivityObserver)
    )
    val status by networkViewModel.status.collectAsState()
    val isOnline = status == ConnectivityObserver.Status.Available

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var profileData by remember { mutableStateOf<ProfileResponse?>(null) }
    
    // Track navigation state to refresh profile when returning from EditProfileScreen
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Get all songs from the SongViewModel
    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    // Get liked songs from the SongViewModel
    val likedSongs = songViewModel.likedSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    // Calculate statistics based on actual data
    val songCount = allSongs.value.size
    val likedCount = likedSongs.value.size

    // State to display the listened count
    val userEmail = tokenManager.getEmail() ?: ""
    var listenedCount by remember { mutableStateOf(0) }

    // Keep track of the current song
    val currentSong by musicViewModel.currentSong.collectAsState()
    var previousSong by remember { mutableStateOf<Song?>(null) }
    
    // Analytics state values
    val formattedTimeListened = ListeningAnalytics.formatTimeListened()
    val topSong by ListeningAnalytics.topSong.collectAsStateWithLifecycle()
    val topArtist by ListeningAnalytics.topArtist.collectAsStateWithLifecycle()
    val streakSong by ListeningAnalytics.streakSong.collectAsStateWithLifecycle()

    // Control whether to show analytics section
    var showAnalytics by remember { mutableStateOf(false) }

    // State to show reset confirmation dialog
    var showResetConfirmation by remember { mutableStateOf(false) }

    // Update listened count when current song changes
    LaunchedEffect(currentSong) {
        if (currentSong != null && currentSong != previousSong) {
            val songKey = "${currentSong!!.title}_${currentSong!!.artist}"
            if (ListenedSongsTracker.addSong(userEmail, songKey, context)) {
                listenedCount = ListenedSongsTracker.getListenedCount(userEmail)
            }
            previousSong = currentSong
        }
    }

    // Observe network status changes
    LaunchedEffect(isOnline) {
        if (isOnline && profileData == null) {
            isLoading = true
            errorMessage = ""
            coroutineScope.launch {
                try {
                    val response = RetrofitClient.apiService.getProfile()
                    profileData = response.body()
                } catch (e: Exception) {
                    errorMessage = "Error: ${e.message}"
                    Log.e("ProfileScreen", "Fetch error", e)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Fetch profile data on first composition and when returning from edit screen
    LaunchedEffect(currentRoute) {
        if (currentRoute == "profile") {
            ListenedSongsTracker.loadListenedSongs(userEmail, context)
            listenedCount = ListenedSongsTracker.getListenedCount(userEmail)

            coroutineScope.launch {
                try {
                    isLoading = true
                    val response = RetrofitClient.apiService.getProfile()
                    if (response.isSuccessful) {
                        profileData = response.body()
                        
                        // Update local cache with fresh data from server
                        profileData?.let { profile ->
                            val userProfile = UserProfile(
                                email = profile.email,
                                name = profile.username ?: "",
                                age = 0, // Default values as these aren't in ProfileResponse
                                gender = "", // Default values as these aren't in ProfileResponse
                                country = profile.location ?: "ID",
                                profileImageUrl = profile.profilePhoto
                            )
                            userProfileManager.saveUserProfile(userProfile)
                            Log.d("ProfileScreen", "Updated local profile cache with country: ${profile.location}")
                        }
                    } else {
                        errorMessage = "Error: ${response.message()}"
                        Log.e("ProfileScreen", "Error response: ${response.code()}")
                    }
                } catch (e: Exception) {
                    errorMessage = "Error: ${e.localizedMessage}"
                    Log.e("ProfileScreen", "Error fetching profile", e)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Load analytics data when screen initializes
    LaunchedEffect(userEmail) {
        if (userEmail.isNotEmpty()) {
            ListeningAnalytics.loadFromPreferences(context, userEmail)
        }
    }
    
    // Track current song playback for analytics
    LaunchedEffect(currentSong, musicViewModel.isPlaying.collectAsState().value) {
        val isPlaying = musicViewModel.isPlaying.value
        currentSong?.let { song ->
            if (isPlaying) {
                // Song started playing or is continuing to play
                ListeningAnalytics.startPlayback(song, musicViewModel = musicViewModel, context = context, email = userEmail)
            } else {
                // Song paused or stopped
                ListeningAnalytics.pausePlayback()
            }
        }
    }
    
    // Save analytics data
    DisposableEffect(key1 = userEmail) {
        onDispose {
            if (userEmail.isNotEmpty()) {
                ListeningAnalytics.saveToPreferences(context, userEmail)
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF095256),
                        0.25f to Color(0xFF121212),
                        1.0f to Color.Black
                    )
                )
            )
    )
    val gradientColors = listOf(
        Color(0xFF095256),
        Color(0xFF121212),
        Color(0xFF000000)
    )

    AdaptiveNavigation(
        navController = navController,
        musicViewModel = musicViewModel,
        songViewModel = songViewModel,
        currentRoute = "profile",
        onMiniPlayerClick = onNavigateToPlayer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF095256),
                            0.25f to Color(0xFF121212),
                            1.0f to Color.Black
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Main content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 54.dp)
            ) {
                if (!isOnline) {
                    ErrorScreen(pageName = "Profile")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(
                                rememberScrollState(),
                                enabled = true,
                                reverseScrolling = false,
                                flingBehavior = null
                            )
                            .padding(bottom = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White)
                        } else if (errorMessage.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            profileData?.let { profile ->
                                Box(
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("http://34.101.226.132:3000/uploads/profile-picture/${profile.profilePhoto}")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Profile Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF6CCB64)),
                                        error = painterResource(id = R.drawable.default_profile),
                                        placeholder = painterResource(id = R.drawable.default_profile)
                                    )

                                    // Edit button
                                    IconButton(
                                        onClick = { 
                                        navController.currentBackStackEntry?.savedStateHandle?.set(
                                            "profileData",
                                            profile
                                        )
                                        navController.navigate("edit_profile")
                                    },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile",
                                            tint = Color.Black,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Username or ID
                                Text(
                                    text = profile.username ?: "13522140",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    textAlign = TextAlign.Center
                                )

                                // Location or ID label
                                Text(
                                    text = profile.location ?: "ID",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 16.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Edit Profile Button
                                Button(
                                    onClick = { 
                                        navController.currentBackStackEntry?.savedStateHandle?.set(
                                            "profileData",
                                            profile
                                        )
                                        navController.navigate("edit_profile")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = "Edit Profile",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(48.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatItem(
                                        count = songCount,
                                        label = "SONGS"
                                    )

                                    StatItem(
                                        count = likedCount,
                                        label = "LIKED"
                                    )

                                    StatItem(
                                        count = listenedCount,
                                        label = "LISTENED"
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Analytics section
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Your Sound Capsule",
                                            style = TextStyle(
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        
                                        // Export Button
                                        IconButton(
                                            onClick = {
                                                // Show format and action options
                                                val formatOptions = arrayOf(
                                                    "PDF - Download", "PDF - Share", "PDF - Both",
                                                    "CSV - Download", "CSV - Share", "CSV - Both"
                                                )
                                                
                                                // Create and show the dialog
                                                val dialog = android.app.AlertDialog.Builder(context)
                                                    .setTitle("Export Analytics")
                                                    .setItems(formatOptions) { _, which ->
                                                        coroutineScope.launch {
                                                            val topSongs = ListeningAnalytics.getAllSongPlayData()
                                                                .take(10)
                                                                .map { Triple(it.first, it.second.toString(), (it.third / 60).toInt()) }

                                                            val topArtists = ListeningAnalytics.getAllArtistsData()
                                                                .take(10)
                                                            
                                                            val success = when (which) {
                                                                0 -> { // PDF - Download
                                                                    PdfExportUtil.exportAnalyticsToPdf(
                                                                        context = context,
                                                                        username = profileData?.username ?: userEmail,
                                                                        timeListened = formattedTimeListened,
                                                                        topSongs = topSongs,
                                                                        topArtists = topArtists,
                                                                        shouldShare = false,
                                                                        shouldDownload = true
                                                                    )
                                                                }
                                                                1 -> { // PDF - Share
                                                                    PdfExportUtil.exportAnalyticsToPdf(
                                                                        context = context,
                                                                        username = profileData?.username ?: userEmail,
                                                                        timeListened = formattedTimeListened,
                                                                        topSongs = topSongs,
                                                                        topArtists = topArtists,
                                                                        shouldShare = true,
                                                                        shouldDownload = false
                                                                    )
                                                                }
                                                                2 -> { // PDF - Both
                                                                    PdfExportUtil.exportAnalyticsToPdf(
                                                                        context = context,
                                                                        username = profileData?.username ?: userEmail,
                                                                        timeListened = formattedTimeListened,
                                                                        topSongs = topSongs,
                                                                        topArtists = topArtists,
                                                                        shouldShare = true,
                                                                        shouldDownload = true
                                                                    )
                                                                }
                                                                3 -> { // CSV - Download
                                                                    CsvExportUtil.exportAnalyticsToCsv(
                                                                        context = context,
                                                                        username = profileData?.username ?: userEmail,
                                                                        timeListened = formattedTimeListened,
                                                                        topSongs = topSongs,
                                                                        topArtists = topArtists,
                                                                        shouldShare = false,
                                                                        shouldDownload = true
                                                                    )
                                                                }
                                                                4 -> { // CSV - Share
                                                                    CsvExportUtil.exportAnalyticsToCsv(
                                                                        context = context,
                                                                        username = profileData?.username ?: userEmail,
                                                                        timeListened = formattedTimeListened,
                                                                        topSongs = topSongs,
                                                                        topArtists = topArtists,
                                                                        shouldShare = true,
                                                                        shouldDownload = false
                                                                    )
                                                                }
                                                                5 -> { // CSV - Both
                                                                    CsvExportUtil.exportAnalyticsToCsv(
                                                                        context = context,
                                                                        username = profileData?.username ?: userEmail,
                                                                        timeListened = formattedTimeListened,
                                                                        topSongs = topSongs,
                                                                        topArtists = topArtists,
                                                                        shouldShare = true,
                                                                        shouldDownload = true
                                                                    )
                                                                }
                                                                else -> false
                                                            }

                                                            if (!success) {
                                                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                    .setNegativeButton("Cancel") { dialog, _ ->
                                                        dialog.dismiss()
                                                    }
                                                    .create()
                                                
                                                dialog.show()
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF6CCB64).copy(alpha = 0.7f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Export Analytics",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                      // Get all monthly analytics data
                                    val monthlyData = ListeningAnalytics.getMonthlyAnalytics()
                                      MonthlyAnalytics(
                                        analyticsData = monthlyData,
                                        navController = navController  // Pass the parent navController
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { showResetConfirmation = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reset Data",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BottomNavBar(
            navController = navController,
            musicViewModel = musicViewModel,
            currentRoute = "profile",
            songViewModel = songViewModel,
            onMiniPlayerClick = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Add confirmation dialog
        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { showResetConfirmation = false },
                title = {
                    Text(
                        text = "Reset Data",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to reset all listening statistics? This action cannot be undone.",
                        color = Color.White
                    )
                },

                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Reset analytics data
                                ListeningAnalytics.resetAllData(context, userEmail)

                                val tokenManager = TokenManager(context)
                                tokenManager.saveString("listened_songs_$userEmail", "")
                                ListenedSongsTracker.loadListenedSongs(userEmail, context)
                                listenedCount = ListenedSongsTracker.getListenedCount(userEmail)

                                showAnalytics = false
                                delay(100)
                                showAnalytics = true

                                Toast.makeText(context, "Listening statistics reset", Toast.LENGTH_SHORT).show()
                            }
                            showResetConfirmation = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        )
                    ) {
                        Text("Reset")
                    }

                },
                dismissButton = {
                    Button(
                        onClick = { showResetConfirmation = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242)
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(16.dp)
                )
            }
        // Add confirmation dialog
        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { showResetConfirmation = false },
                title = {
                    Text(
                        text = "Reset Data",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to reset all listening statistics? This action cannot be undone.",
                        color = Color.White
                    )
                },

                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Reset analytics data
                                ListeningAnalytics.resetAllData(context, userEmail)

                                val tokenManager = TokenManager(context)
                                tokenManager.saveString("listened_songs_$userEmail", "")
                                ListenedSongsTracker.loadListenedSongs(userEmail, context)
                                listenedCount = ListenedSongsTracker.getListenedCount(userEmail)

                                showAnalytics = false
                                delay(100)
                                showAnalytics = true

                                Toast.makeText(context, "Listening statistics reset", Toast.LENGTH_SHORT).show()
                            }
                            showResetConfirmation = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        )
                    ) {
                        Text("Reset")
                    }

                },
                dismissButton = {
                    Button(
                        onClick = { showResetConfirmation = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242)
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }

@Composable
fun StatItem(count: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = TextStyle(
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = label,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        )
    }
}

@Composable
fun MonthlyAnalytics(
    analyticsData: Map<String, MonthlyAnalytics>,
    navController: NavController
) {
    // Current month and year
    val now = LocalDate.now()
    val currentMonth = now.month.name.lowercase().replaceFirstChar { it.uppercase() }
    val currentYear = now.year
    val currentMonthValue = now.monthValue

    val monthValues = mapOf(
        "January" to 1, "February" to 2, "March" to 3, "April" to 4,
        "May" to 5, "June" to 6, "July" to 7, "August" to 8,
        "September" to 9, "October" to 10, "November" to 11, "December" to 12
    )

    val availableMonths = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    ).filter { month ->
        monthValues[month]!! <= currentMonthValue
    }.reversed()

    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(currentMonth) {
        expandedStates[currentMonth] = true
    }

    var selectedMonth by remember { mutableStateOf(currentMonth) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        availableMonths.forEach { month ->
            val isCurrentMonth = month == currentMonth
            val isSelected = month == selectedMonth

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isCurrentMonth) {
                            expandedStates[month] = !(expandedStates[month] ?: false)
                            
                            if (!isSelected) {
                                selectedMonth = month
                            } else {
                                selectedMonth = currentMonth
                            }
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = month,
                                color = Color.White,
                                fontSize = if (isSelected || isCurrentMonth) 18.sp else 16.sp,
                                fontWeight = FontWeight.Normal
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = currentYear.toString(),
                                color = Color.LightGray,
                                fontSize = if (isSelected || isCurrentMonth) 18.sp else 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        if (!isCurrentMonth) {
                            Icon(
                                imageVector = if (expandedStates[month] == true)
                                    Icons.Default.KeyboardArrowUp
                                else
                                    Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color.White
                            )
                        }
                    }

                    if (isCurrentMonth || expandedStates[month] == true) {
                        val data = analyticsData[month]

                        if (data?.timeListened == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Data Available",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Time listened card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("time_listened") },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Time listened", color = Color.Gray, fontSize = 14.sp)
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Text(
                                        text = data.timeListened,
                                        color = Color(0xFF00FF7F),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Top artist and song row
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Top artist card
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp)
                                        .clickable { navController.navigate("top_artists") },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(
                                            0xFF2A2A2A
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Top artist", color = Color.Gray, fontSize = 14.sp)
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = data.topArtist ?: "None yet",
                                            color = Color(0xFF669BEC),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(CircleShape)
                                                .background(Color.DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (data.topArtistCoverUrl != null) {
                                                val imageModel = when {
                                                    data.topArtistCoverUrl.startsWith("http") -> {
                                                        // For online covers
                                                        Log.d("ProfileScreen", "Using online artist cover: ${data.topArtistCoverUrl}")
                                                        data.topArtistCoverUrl
                                                    }
                                                    data.topArtistCoverUrl.isNotEmpty() && File(data.topArtistCoverUrl).exists() -> {
                                                        // For local covers
                                                        Log.d("ProfileScreen", "Using local artist cover: ${data.topArtistCoverUrl}")
                                                        File(data.topArtistCoverUrl)
                                                    }
                                                    else -> null
                                                }
                                                
                                                if (imageModel != null) {
                                                    AsyncImage(
                                                        model = imageModel,
                                                        contentDescription = "Artist cover",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    // Fallback icon when URL is invalid
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }
                                            } else {
                                                // Fallback icon when no cover URL
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Top song card
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                        .clickable { navController.navigate("top_songs") },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(
                                            0xFF2A2A2A
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Top song", color = Color.Gray, fontSize = 14.sp)
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = data.topSong ?: "None yet",
                                            color = Color(0xFFF8E747),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        // Song cover image (if available)
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(CircleShape)
                                                .background(Color.DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (data.topSongCoverUrl != null) {
                                                val imageModel = when {
                                                    data.topSongCoverUrl.startsWith("http") -> {
                                                        // For online covers
                                                        Log.d("ProfileScreen", "Using online song cover: ${data.topSongCoverUrl}")
                                                        data.topSongCoverUrl
                                                    }
                                                    data.topSongCoverUrl.isNotEmpty() && File(data.topSongCoverUrl).exists() -> {
                                                        // For local covers
                                                        Log.d("ProfileScreen", "Using local song cover: ${data.topSongCoverUrl}")
                                                        File(data.topSongCoverUrl)
                                                    }
                                                    else -> null
                                                }
                                                
                                                if (imageModel != null) {
                                                    AsyncImage(
                                                        model = imageModel,
                                                        contentDescription = "Song cover",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    // Fallback icon when URL is invalid
                                                    Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }
                                            } else {
                                                // Fallback icon when no cover URL
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }                            
                            if (data.streak != null && data.streak > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(
                                            0xFF2A2A2A
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {                                        Text(
                                            if (data.streakSong != null) 
                                                "You had a ${data.streak}-day streak with \"${data.streakSong}\""
                                            else 
                                                "You had a ${data.streak}-day streak",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val streakImageModel = when {
                                            // First priority: use the specific streak song cover if available
                                            data.streakSongCoverUrl != null -> {
                                                Log.d("ProfileScreen", "Using streak song cover: ${data.streakSongCoverUrl}")
                                                if (data.streakSongCoverUrl.startsWith("http")) data.streakSongCoverUrl
                                                else if (File(data.streakSongCoverUrl).exists()) File(data.streakSongCoverUrl)
                                                else null
                                            }
                                            // Second priority: top song cover
                                            data.topSongCoverUrl != null -> {
                                                Log.d("ProfileScreen", "Using top song cover for streak: ${data.topSongCoverUrl}")
                                                if (data.topSongCoverUrl.startsWith("http")) data.topSongCoverUrl
                                                else if (File(data.topSongCoverUrl).exists()) File(data.topSongCoverUrl)
                                                else null
                                            }
                                            // Third priority: top artist cover
                                            data.topArtistCoverUrl != null -> {
                                                Log.d("ProfileScreen", "Using top artist cover for streak: ${data.topArtistCoverUrl}")
                                                if (data.topArtistCoverUrl.startsWith("http")) data.topArtistCoverUrl
                                                else if (File(data.topArtistCoverUrl).exists()) File(data.topArtistCoverUrl)
                                                else null
                                            }
                                            // Last resort: try to get a cover for the streak song or top song
                                            else -> ListeningAnalytics.getSongCoverUrl(
                                                data.streakSong ?: data.topSong ?: "", data.topArtist ?: ""
                                            )
                                        }
                                        
                                        AsyncImage(
                                            model = streakImageModel,
                                            contentDescription = "Streak Song Cover",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "You played consistently during this month. Keep it up!",
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}