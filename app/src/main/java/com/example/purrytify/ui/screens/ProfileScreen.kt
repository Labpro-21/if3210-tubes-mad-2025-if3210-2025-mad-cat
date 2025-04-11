package com.example.purrytify.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.purrytify.ui.components.BottomNavBar
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModelFactory
import com.example.purrytify.ui.viewmodel.SongViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Global object to store listened songs across app sessions
object ListenedSongsTracker {
    val listenedSongs = mutableSetOf<String>()
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

    val connectivityObserver = remember { NetworkConnectivityObserver(context) }
    val networkViewModel: NetworkViewModel = viewModel(
        factory = NetworkViewModelFactory(connectivityObserver)
    )
    val status by networkViewModel.status.collectAsState()
    val isOnline = status == ConnectivityObserver.Status.Available

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var profileData by remember { mutableStateOf<ProfileResponse?>(null) }

    // Get all songs from the SongViewModel
    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    // Get liked songs from the SongViewModel
    val likedSongs = songViewModel.likedSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    // Calculate statistics based on actual data
    val songCount = allSongs.value.size
    val likedCount = likedSongs.value.size

    // State to display the listened count
    var listenedCount by remember { mutableStateOf(ListenedSongsTracker.listenedSongs.size) }

    // Keep track of the current song
    val currentSong by musicViewModel.currentSong.collectAsState()
    var previousSong by remember { mutableStateOf<Song?>(null) }

    // Update listened count when current song changes
    LaunchedEffect(currentSong) {
        if (currentSong != null && currentSong != previousSong) {
            // Only process when we have a new song
            val songKey = "${currentSong!!.title}_${currentSong!!.artist}"

            // Add to our tracked set and update the count
            if (ListenedSongsTracker.listenedSongs.add(songKey)) {
                listenedCount = ListenedSongsTracker.listenedSongs.size
            }

            // Update previous song reference
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
                    val response = RetrofitClient.apiService.getProfile("Bearer ${tokenManager.getToken()}")
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

    // Fetch profile data on first composition
    LaunchedEffect(key1 = true) {
        coroutineScope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response = RetrofitClient.apiService.getProfile("Bearer $token")
                    if (response.isSuccessful) {
                        profileData = response.body()
                        isLoading = false
                    } else {
                        errorMessage = "Error: ${response.message()}"
                        isLoading = false
                    }
                } else {
                    errorMessage = "Token not found. Please login again."
                    isLoading = false
                    // Navigate back to login if no token is found
                    navController.navigate("login") {
                        popUpTo("profile") { inclusive = true }
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.localizedMessage}"
                isLoading = false
                Log.e("ProfileScreen", "Error fetching profile", e)
            }
        }
    }

    // Create a gradient background for the profile page
    val gradientColors = listOf(
        Color(0xFF095256), // Dark teal top color
        Color(0xFF121212)  // Dark bottom color
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Settings button in top right corner
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
                    .padding(bottom = 56.dp) // Add padding to account for mini player + navbar
            ) {
                if (!isOnline) {
                    ErrorScreen(pageName = "Profile")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
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
                                // Profile Photo with edit button
                                Box(
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    // Profile Image
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
                                            .background(Color(0xFF6CCB64)), // Light green background for profile pic
                                        error = painterResource(id = R.drawable.default_profile),
                                        placeholder = painterResource(id = R.drawable.default_profile)
                                    )

                                    // Edit button
                                    IconButton(
                                        onClick = { /* Add edit profile functionality */ },
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
                                    onClick = { /* Add edit profile functionality */ },
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

                                // Stats Row - Now using actual values from database with fixed listened count
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // Songs Stat - Number of songs in library
                                    StatItem(
                                        count = songCount,
                                        label = "SONGS"
                                    )

                                    // Liked Stat - Number of liked songs
                                    StatItem(
                                        count = likedCount,
                                        label = "LIKED"
                                    )

                                    // Listened Stat - Tracks unique songs played
                                    StatItem(
                                        count = listenedCount,
                                        label = "LISTENED"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar with mini player
        BottomNavBar(
            navController = navController,
            musicViewModel = musicViewModel,
            currentRoute = "profile",
            songViewModel = songViewModel,
            onMiniPlayerClick = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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