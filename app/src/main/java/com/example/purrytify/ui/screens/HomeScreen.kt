package com.example.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.components.BottomNavBar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

data class Song(
    val title: String,
    val artist: String,
    val coverUri: String,
    val uri: String,
    val duration: String
)

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    // State for new songs and recently played songs
    var newSongs by remember { mutableStateOf(listOf<Song>()) }
    var recentlySongs by remember { mutableStateOf(listOf<Song>()) }

    // Fetch songs when the screen is first loaded
    LaunchedEffect(key1 = true) {
        try {
            val token = tokenManager.getToken()
            if (token != null) {
                // TODO: Implement actual API calls to fetch new and recently played songs
                // For now, we'll use dummy data
                newSongs = getDummyNewSongs()
                recentlySongs = getDummyRecentlySongs()
            } else {
                // Redirect to login if no token
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            }
        } catch (e: Exception) {
            // Handle error
            e.printStackTrace()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
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

            // New Songs Section
            Text(
                text = "New Songs",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(newSongs) { song ->
                    NewSongItem(song)
                }
            }

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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentlySongs) { song ->
                    RecentlySongItem(song)
                }
            }
        }

        // Bottom Navigation Bar
        BottomNavBar(
            navController = navController,
            currentRoute = "home",
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun NewSongItem(song: Song) {
    Column(
        modifier = Modifier
            .width(160.dp)
    ) {
        AsyncImage(
            model = song.coverUri,
            contentDescription = song.title,
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )
        Text(
            text = song.title,
            style = TextStyle(
                color = Color.White,
                fontSize = 14.sp
            ),
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1
        )
        Text(
            text = song.artist,
            style = TextStyle(
                color = Color.Gray,
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
fun RecentlySongItem(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        AsyncImage(
            model = song.coverUri,
            contentDescription = song.title,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = song.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                maxLines = 1
            )
            Text(
                text = song.artist,
                style = TextStyle(
                    color = Color.Gray,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )
        }
    }
}

// Dummy data functions
fun getDummyNewSongs(): List<Song> = listOf(
    Song("Starboy", "The Weeknd", "https://example.com/starboy.jpg", "https://example.com/audio/starboy.mp3", "3:50"),
    Song("Here Comes the Sun", "The Beatles", "https://example.com/beatles.jpg", "https://example.com/audio/sun.mp3", "3:05"),
    Song("Midnight Pretenders", "Tomoko Aran", "https://example.com/tomoko.jpg", "https://example.com/audio/midnight.mp3", "5:15"),
    Song("Violent Crimes", "Kanye West", "https://example.com/kanye.jpg", "https://example.com/audio/violent.mp3", "3:35")
)

fun getDummyRecentlySongs(): List<Song> = listOf(
    Song("Jazz is for Ordinary People", "berlioz", "https://example.com/berlioz.jpg", "https://example.com/audio/jazz.mp3", "4:10"),
    Song("Loose", "Daniel Caesar", "https://example.com/daniel.jpg", "https://example.com/audio/loose.mp3", "3:45"),
    Song("Nights", "Frank Ocean", "https://example.com/frank.jpg", "https://example.com/audio/nights.mp3", "5:08"),
    Song("Kiss of Life", "Sade", "https://example.com/sade.jpg", "https://example.com/audio/kiss.mp3", "4:18"),
    Song("BEST INTEREST", "Tyler, The Creator", "https://example.com/tyler.jpg", "https://example.com/audio/bestinterest.mp3", "2:57")
)