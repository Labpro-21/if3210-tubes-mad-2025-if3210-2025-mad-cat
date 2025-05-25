package com.example.purrytify.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import com.example.purrytify.ui.components.BottomNavBar
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import com.example.purrytify.ui.viewmodel.HomeViewModel
import com.example.purrytify.ui.viewmodel.HomeViewModelFactory
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.data.preferences.UserProfileManager
import com.example.purrytify.data.PlayHistoryTracker
import com.example.purrytify.ui.dialogs.ShareSongDialog
import com.example.purrytify.ui.dialogs.SongOptionsDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

object MixStorageHelper {
    private const val PREF_NAME = "mix_playlist_pref"

    fun saveMixPlaylist(context: Context, mixName: String, songs: List<Song>) {
        val sharedPrefs = EncryptedSharedPreferences.create(
            PREF_NAME, MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val editor = sharedPrefs.edit()
        val json = Gson().toJson(songs)
        val today = LocalDate.now().toString()
        val currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        editor.putString("${mixName}_songs", json)
        editor.putString("${mixName}_lastUpdated", today)
        editor.putString("${mixName}_lastUpdatedTime", currentTime)
        editor.apply()
    }    fun loadMixPlaylist(context: Context, mixName: String): Pair<List<Song>?, String?> {
        val sharedPrefs = EncryptedSharedPreferences.create(
            PREF_NAME, MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val lastUpdated = sharedPrefs.getString("${mixName}_lastUpdated", null)
        val lastUpdatedTime = sharedPrefs.getString("${mixName}_lastUpdatedTime", null)
        val today = LocalDate.now().toString()
        
        val songs = if (lastUpdated == today) {
            val json = sharedPrefs.getString("${mixName}_songs", null)
            json?.let {
                val type = object : TypeToken<List<Song>>() {}.type
                Gson().fromJson<List<Song>>(it, type)
            }
        } else {
            null
        }
        
        return Pair(songs, lastUpdatedTime)
    }
}

@Composable
fun MixPlaylistScreen(
    navController: NavController,
    mixName: String,
    musicViewModel: MusicViewModel = viewModel(),
    songViewModel: SongViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    // Get the appropriate description based on mix name
    val mixDescription = when (mixName) {
        "Your Daily Mix" -> "Fresh tunes worth giving a chance to"
        "Favorites Mix" -> "Songs you love and might love next"
        else -> ""
    }

    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(context))
    val tokenManager = remember { TokenManager(context) }
    val userProfileManager = remember { UserProfileManager(context) }
    val userEmail = tokenManager.getEmail() ?: ""
    val userProfile = userProfileManager.getUserProfile(userEmail)
    val userCountryCode = userProfile?.country ?: "ID"
    val scope = rememberCoroutineScope()
    val savedMixData = remember {
        MixStorageHelper.loadMixPlaylist(context, mixName)
    }
    
    val savedMix = savedMixData.first
    val lastUpdatedTime = savedMixData.second

    val allSongs = songViewModel.allSongs.collectAsState(initial = emptyList())
    val likedSongs = songViewModel.likedSongs.collectAsState(initial = emptyList())
    val recentlyPlayedSongs = remember {
        PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager)
    }
    
    // Fetch global and country songs for Daily Mix
    val globalTopSongs = homeViewModel.globalTopSongs.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val countryTopSongs = homeViewModel.countryTopSongs.collectAsStateWithLifecycle(initialValue = emptyList()).value
    
    // Fetch the data when component mounts
    LaunchedEffect(Unit) {
        homeViewModel.fetchGlobalTopSongs()
        homeViewModel.fetchCountryTopSongs(userCountryCode)
    }
    
    // Convert online songs to local format
    val globalSongs = remember(globalTopSongs) {
        globalTopSongs.map { onlineSong ->
            Song(
                title = onlineSong.title,
                artist = onlineSong.artist,
                coverUri = onlineSong.artworkUrl,
                uri = onlineSong.audioUrl,
                duration = onlineSong.duration
            )
        }
    }
    
    val countrySongs = remember(countryTopSongs) {
        countryTopSongs.map { onlineSong ->
            Song(
                title = onlineSong.title,
                artist = onlineSong.artist,
                coverUri = onlineSong.artworkUrl,
                uri = onlineSong.audioUrl,
                duration = onlineSong.duration
            )
        }
    }

    // Create the mix songs based on the mix name
    val mixSongs = remember(savedMix, mixName, allSongs.value, likedSongs.value, recentlyPlayedSongs, globalSongs, countrySongs) {
        if (savedMix != null) {
            savedMix
        } else {
            val generatedMix = when (mixName) {
                "Your Daily Mix" -> {
                    val alreadyListenedTitles =
                        recentlyPlayedSongs.map { "${it.title}_${it.artist}" }.toSet()

                    val unheardGlobalSongs = globalSongs
                        .filter { song -> !alreadyListenedTitles.contains("${song.title}_${song.artist}") }
                        .take(8)

                    val unheardCountrySongs = countrySongs
                        .filter { song ->
                            !alreadyListenedTitles.contains("${song.title}_${song.artist}") &&
                                    !unheardGlobalSongs.any { it.title == song.title && it.artist == song.artist }
                        }
                        .take(7)

                    val combinedList = (unheardGlobalSongs + unheardCountrySongs).take(15)

                    if (combinedList.size < 15) {
                        val additionalSongs = allSongs.value
                            .filter { song ->
                                !alreadyListenedTitles.contains("${song.title}_${song.artist}") &&
                                        !combinedList.any { it.title == song.title && it.artist == song.artist }
                            }
                            .shuffled()
                            .take(15 - combinedList.size)

                        combinedList + additionalSongs
                    } else {
                        combinedList
                    }
                }
                "Favorites Mix" -> {
                    val favoritesMix = likedSongs.value.take(7).toMutableList()
                    if (favoritesMix.size < 7) {
                        val additionalSongs = recentlyPlayedSongs
                            .filter { song -> !favoritesMix.any { it.title == song.title && it.artist == song.artist } }
                            .take(7 - favoritesMix.size)
                        favoritesMix.addAll(additionalSongs)
                    }
                    favoritesMix.shuffled()
                }
                else -> emptyList()
            }

            MixStorageHelper.saveMixPlaylist(context, mixName, generatedMix)
            generatedMix
        }
    }


    val bgColorTop = when (mixName) {
        "Your Daily Mix" -> Color(0xFF1DB954)
        "Favorites Mix" -> Color(0xFF9C27B0)
        else -> Color(0xFF121212)
    }

    val bgColorBottom = Color(0xFF121212)

    val headerGradient = remember(bgColorTop) {
        Brush.verticalGradient(
            colors = listOf(bgColorTop, bgColorBottom),
            startY = 0f,
            endY = 900f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .background(headerGradient)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Back button
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Cover image
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = when (mixName) {
                                            "Your Daily Mix" -> listOf(Color(0xFF1DB954), Color(0xFF191414))
                                            "Favorites Mix" -> listOf(Color(0xFF9C27B0), Color(0xFF3F51B5))
                                            else -> listOf(Color(0xFF2A2A2A), Color(0xFF121212))
                                        }
                                    )
                                )
                                .align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Mix name and description
                        Text(
                            text = mixName,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Add description under the mix name
                        Text(
                            text = mixDescription,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))                        // Playlist description
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Made for you • ${mixSongs.size} songs" + 
                                      (lastUpdatedTime?.let { " • Updated at $it" } ?: ""),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Play button
                        Button(
                            onClick = {
                                scope.launch {
                                    if (mixSongs.isNotEmpty()) {                                        musicViewModel.setOnlinePlaylist(mixSongs, "mix_${mixName}")
                                        musicViewModel.playSong(
                                            mixSongs.first(), 
                                            context, 
                                            fromOnlinePlaylist = true,
                                            onlineType = "mix_${mixName}"
                                        )
                                        onNavigateToPlayer()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(40.dp)
                                .widthIn(min = 120.dp)
                        ) {
                            Text("Play", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(mixSongs.withIndex().toList()) { (index, song) ->
                SongListItem(
                    song = song,
                    index = index + 1,
                    onClick = {
                        scope.launch {
                            musicViewModel.setOnlinePlaylist(mixSongs, "mix_${mixName}")
                            musicViewModel.playSong(
                                song,
                                context,
                                fromOnlinePlaylist = true,
                                onlineType = "mix_${mixName}"
                            )
                            onNavigateToPlayer()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Empty state
            if (mixSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs in this mix",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }
        }
        BottomNavBar(
            navController = navController,
            musicViewModel = musicViewModel,
            songViewModel = songViewModel,
            currentRoute = "",
            onMiniPlayerClick = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun SongListItem(
    song: Song,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val musicViewModel: MusicViewModel = viewModel()

    var isDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(song) {
        isDownloaded = musicViewModel.isSongDownloaded(song, context)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isDownloaded) Color(0xFF1DB954).copy(alpha = 0.1f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index number
        Text(
            text = "$index",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp
            ),
            modifier = Modifier.padding(end = 8.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        val imageModel = when {
            song.coverUri.startsWith("http://") || song.coverUri.startsWith("https://") -> {
                song.coverUri
            }
            song.coverUri.isNotEmpty() && File(song.coverUri).exists() -> {
                File(song.coverUri)
            }
            else -> {
                null
            }
        }

        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
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
                    fontSize = 16.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text = formatDuration(song.duration),
            style = TextStyle(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Download button
        IconButton(
            onClick = {
                if (!isDownloaded && !isDownloading) {
                    isDownloading = true
                    musicViewModel.downloadSong(
                        song = song,
                        context = context,
                        onSuccess = {
                            isDownloaded = true
                            isDownloading = false
                            Toast.makeText(context, "Download completed: ${song.title}", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            isDownloading = false
                            if (error == "Song already downloaded") {
                                Toast.makeText(context, "Song already downloaded", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Download failed: $error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            },
            enabled = !isDownloaded && !isDownloading
        ) {
            when {
                isDownloaded -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Downloaded",
                    tint = Color(0xFF1DB954)
                )
                isDownloading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF1DB954),
                    strokeWidth = 2.dp
                )
                else -> Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = Color.White
                )
            }
        }

        // Options button (three dots)
        IconButton(
            onClick = { showOptionsDialog = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    // Show options dialog
    if (showOptionsDialog) {
        SongOptionsDialog(
            song = song,
            isOnlineSong = true,
            onShareClick = {
                showShareDialog = true
            },
            onEditClick = {
                // Not available for online songs
            },
            onDeleteClick = {
                // Not available for online songs
            },
            onDismiss = { showOptionsDialog = false }
        )
    }    
    
    // Show share dialog
    if (showShareDialog) {
        ShareSongDialog(
            songId = null,
            songTitle = song.title,
            songArtist = song.artist,
            songUrl = song.uri,
            onDismiss = { showShareDialog = false }
        )
    }
}

fun formatDuration(duration: String): String {
    if (duration.contains(":")) {
        return duration
    }

    return try {
        val durationMillis = duration.toLong()
        val minutes = (durationMillis / 1000) / 60
        val seconds = (durationMillis / 1000) % 60
        String.format("%02d:%02d", minutes, seconds)
    } catch (e: NumberFormatException) {
        duration
    }
}