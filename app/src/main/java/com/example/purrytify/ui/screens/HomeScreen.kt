import android.util.Log
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

object PlayHistoryTracker {
    private val userHistories = mutableMapOf<String, MutableList<Song>>()
    private const val MAX_HISTORY_SIZE = 10

    fun addSongToHistory(email: String, song: Song, tokenManager: TokenManager) {
        val history = tokenManager.getRecentlyPlayed(email).toMutableList()

        history.removeAll { it.title == song.title && it.artist == song.artist }
        history.add(0, song)

        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }

        tokenManager.saveRecentlyPlayed(email, history)
    }

    fun getRecentlyPlayedSongs(email: String, tokenManager: TokenManager): List<Song> {
        return tokenManager.getRecentlyPlayed(email)
    }

    fun clearHistory(email: String, tokenManager: TokenManager) {
        tokenManager.saveRecentlyPlayed(email, emptyList())
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
    val tokenManager = remember { TokenManager(context) }
    val userEmail = tokenManager.getEmail() ?: ""

    val scope = rememberCoroutineScope()

    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    val currentSong by musicViewModel.currentSong.collectAsState()
    var recentlyPlayedSongs by remember {
        mutableStateOf<List<Song>>(PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager))
    }

    var isLoading by remember { mutableStateOf(true) }

    var previousSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(userEmail) {
        Log.d("HomeScreen", "Fetching recently played songs for: $userEmail")
        recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager)
        Log.d("HomeScreen", "Recently played songs loaded: ${recentlyPlayedSongs.map { it.title }}")
    }

    LaunchedEffect(currentSong, userEmail) {
        if (currentSong != null && currentSong != previousSong) {
            PlayHistoryTracker.addSongToHistory(userEmail, currentSong!!, tokenManager)
            recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager)
            previousSong = currentSong
        }

        if (recentlyPlayedSongs.isEmpty() && allSongs.value.isNotEmpty()) {
            val initialSongs = allSongs.value.shuffled().take(5)
            initialSongs.forEach {
                PlayHistoryTracker.addSongToHistory(userEmail, it, tokenManager)
            }
            recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager)
        }

        isLoading = false
    }

    LaunchedEffect(allSongs.value) {
        if (allSongs.value.isNotEmpty()) {
            isLoading = false
        }

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
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
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
                        val newSongs = allSongs.value.reversed()

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

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

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