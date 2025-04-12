import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.purrytify.ui.screens.Song
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import kotlinx.coroutines.launch
import java.io.File

// Object to store play history across recompositions
object PlayHistoryTracker {
    private val recentlyPlayedList = mutableListOf<Song>()
    private const val MAX_HISTORY_SIZE = 10

    fun addSongToHistory(song: Song) {
        // Remove the song if it's already in the list to avoid duplicates
        recentlyPlayedList.removeAll { it.title == song.title && it.artist == song.artist }

        // Add the song at the beginning of the list (most recent)
        recentlyPlayedList.add(0, song)

        // Keep only the most recent MAX_HISTORY_SIZE songs
        if (recentlyPlayedList.size > MAX_HISTORY_SIZE) {
            recentlyPlayedList.removeAt(recentlyPlayedList.size - 1)
        }
    }

    fun getRecentlyPlayedSongs(): List<Song> {
        return recentlyPlayedList.toList()
    }
}

@Composable
fun HomeScreen(
    navController: NavController,
    musicViewModel: MusicViewModel = viewModel(),
    songViewModel: SongViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect all songs from the SongViewModel
    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    // State to track recently played songs
    val currentSong by musicViewModel.currentSong.collectAsState()
    var recentlyPlayedSongs by remember { mutableStateOf<List<Song>>(PlayHistoryTracker.getRecentlyPlayedSongs()) }

    // State for loading
    var isLoading by remember { mutableStateOf(true) }

    // Track the previous song to detect changes
    var previousSong by remember { mutableStateOf<Song?>(null) }

    // Update recently played songs when current song changes
    LaunchedEffect(currentSong) {
        // Only update if there's a new song playing and it's different from the previous one
        if (currentSong != null && currentSong != previousSong) {
            // Add the song to the play history
            PlayHistoryTracker.addSongToHistory(currentSong!!)

            // Update the state to trigger recomposition
            recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs()

            // Update previous song reference
            previousSong = currentSong
        }

        // If we have no play history yet but have songs in the library, seed with some random songs
        if (recentlyPlayedSongs.isEmpty() && allSongs.value.isNotEmpty()) {
            val initialSongs = allSongs.value.shuffled().take(5)
            initialSongs.forEach { PlayHistoryTracker.addSongToHistory(it) }
            recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs()
        }

        isLoading = false
    }

    // Initial data loading
    LaunchedEffect(allSongs.value) {
        if (allSongs.value.isNotEmpty()) {
            isLoading = false
        }

        // Log the database contents after songs are fetched
        songViewModel.logDatabaseContents()
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // Bottom padding for player
            ) {
                // Add some space at the top
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    // Header
                    Text(
                        text = "Home",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }

                // New Songs Section
                item {
                    Text(
                        text = "New songs",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )
                }

                item {
                    if (allSongs.value.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No songs in your library yet",
                                style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                            )
                        }
                    } else {
                        // Using LazyRow for horizontal scrolling with newest songs first
                        val newSongs = allSongs.value.reversed() // Reverse to get newest first

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.height(180.dp)
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
                }

                // Spacer between sections
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Recently Played Section
                item {
                    Text(
                        text = "Recently played",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp)
                    )
                }

                if (recentlyPlayedSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recently played songs",
                                style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                            )
                        }
                    }
                } else {
                    items(recentlyPlayedSongs.size) { index ->
                        val song = recentlyPlayedSongs[index]
                        RecentlySongItem(
                            song = song,
                            onClick = {
                                scope.launch {
                                    musicViewModel.playSong(song, context)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // Bottom Navigation Bar with mini player
        BottomNavBar(
            navController = navController,
            musicViewModel = musicViewModel,
            songViewModel = songViewModel,
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
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        val imageModel = if (song.coverUri.isNotEmpty() && File(song.coverUri).exists()) {
            File(song.coverUri)
        } else {
            "https://example.com/placeholder.jpg"
        }

        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = song.title,
            style = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
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
fun RecentlySongItem(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModel = if (song.coverUri.isNotEmpty() && File(song.coverUri).exists()) {
            File(song.coverUri)
        } else {
            "https://example.com/placeholder.jpg"
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = song.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

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
}