package com.example.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.components.BottomNavBar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

@Composable
fun HomeScreen(
    navController: NavController,
    musicViewModel: MusicViewModel = viewModel(),
    songViewModel: SongViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val userEmail = remember { "13522126@std.stei.itb.ac.id" }

    // Collect all songs from the SongViewModel
    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    // State to track recently played songs
    val currentSong by musicViewModel.currentSong.collectAsState()
    var recentlyPlayedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // State for loading
    var isLoading by remember { mutableStateOf(true) }

    // Get the recently played songs and new songs from database
    LaunchedEffect(key1 = allSongs.value, key2 = currentSong) {
        try {
            val songList = allSongs.value

            // Create a simulated recently played list that includes the current song first
            val playedList = mutableListOf<Song>()

            // Add current song as the first item if it exists
            currentSong?.let {
                playedList.add(it)

                // Randomly select 4 other songs that are different from current song
                // In a real app, you would track this in a database
                val otherSongs = songList.filter { it != currentSong }
                    .shuffled()
                    .take(4)

                playedList.addAll(otherSongs)
            } ?: run {
                // If no current song, just take 5 random songs
                val randomSongs = songList.shuffled().take(5)
                playedList.addAll(randomSongs)
            }

            recentlyPlayedSongs = playedList
            isLoading = false
        } catch (e: Exception) {
            // Handle error
            e.printStackTrace()
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF1DB954)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header
                Text(
                    text = "Home",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // New Songs Section - using LazyRow for horizontal scrolling
                Text(
                    text = "New Songs",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (allSongs.value.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs in your library yet",
                            style = TextStyle(color = Color.Gray)
                        )
                    }
                } else {
                    // Using LazyRow for horizontal scrolling with newest songs first
                    val newSongs = allSongs.value.reversed() // Reverse to get newest first

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                        modifier = Modifier.height(170.dp)
                    ) {
                        items(newSongs) { song ->
                            NewSongItem(
                                song = song,
                                onClick = {
                                    scope.launch {
                                        musicViewModel.playSong(song, context)
                                        onNavigateToPlayer()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Recently Played Section
                Text(
                    text = "Recently Played",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (recentlyPlayedSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recently played songs",
                            style = TextStyle(color = Color.Gray)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp), // Added padding for mini-player
                        modifier = Modifier.weight(1f)
                    ) {
                        items(recentlyPlayedSongs) { song ->
                            RecentlySongItem(
                                song = song,
                                onClick = {
                                    scope.launch {
                                        musicViewModel.playSong(song, context)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar with mini player
        BottomNavBar(
            navController = navController,
            musicViewModel = musicViewModel,
            currentRoute = "home",
            onMiniPlayerClick = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun NewSongItem(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        val imageModel = if (song.coverUri.isNotEmpty() && File(song.coverUri).exists()) {
            File(song.coverUri)
        } else {
            "https://example.com/placeholder.jpg"
        }

        AsyncImage(
            model = imageModel,
            contentDescription = song.title,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = song.title,
            style = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = TextStyle(
                color = Color.Gray,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RecentlySongItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModel = if (song.coverUri.isNotEmpty() && File(song.coverUri).exists()) {
            File(song.coverUri)
        } else {
            "https://example.com/placeholder.jpg"
        }

        AsyncImage(
            model = imageModel,
            contentDescription = song.title,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = song.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = TextStyle(
                    color = Color.Gray,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}