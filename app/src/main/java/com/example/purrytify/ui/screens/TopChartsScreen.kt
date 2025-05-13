package com.example.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.purrytify.data.model.OnlineSong
import com.example.purrytify.ui.viewmodel.HomeViewModel
import com.example.purrytify.ui.viewmodel.HomeViewModelFactory
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.components.BottomNavBar
import com.example.purrytify.ui.viewmodel.SongViewModel
import com.example.purrytify.ui.screens.Song
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopChartsScreen(
    navController: NavController,
    chartType: String, // "global" or country code
    countryName: String? = null,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(context))
    val scope = rememberCoroutineScope()
    
    val songs = if (chartType == "global") {
        homeViewModel.globalTopSongs.collectAsStateWithLifecycle()
    } else {
        homeViewModel.countryTopSongs.collectAsStateWithLifecycle()
    }
    
    val isLoading = if (chartType == "global") {
        homeViewModel.isLoadingGlobal.collectAsStateWithLifecycle()
    } else {
        homeViewModel.isLoadingCountry.collectAsStateWithLifecycle()
    }
    
    val errorMessage by homeViewModel.errorMessage.collectAsStateWithLifecycle()
    
    LaunchedEffect(chartType) {
        if (chartType == "global") {
            homeViewModel.fetchGlobalTopSongs()
        } else {
            homeViewModel.fetchCountryTopSongs(chartType)
        }
    }
    
    // Convert online songs to local Song format and set the playlist
    LaunchedEffect(songs.value) {
        if (songs.value.isNotEmpty()) {
            val mappedSongs = songs.value.map { onlineSong ->
                Song(
                    title = onlineSong.title,
                    artist = onlineSong.artist,
                    coverUri = onlineSong.artworkUrl,
                    uri = onlineSong.audioUrl,
                    duration = onlineSong.duration
                )
            }
            musicViewModel.setOnlinePlaylist(mappedSongs, chartType)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (chartType == "global") "Top 50 Global" else "Top 50 ${countryName ?: chartType}",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading.value -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF1DB954)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage ?: "An error occurred",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = {
                                if (chartType == "global") {
                                    homeViewModel.fetchGlobalTopSongs()
                                } else {
                                    homeViewModel.fetchCountryTopSongs(chartType)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954)
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
                songs.value.isEmpty() -> {
                    Text(
                        text = "No songs available",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        itemsIndexed(songs.value) { index, song ->
                            TrendingSongItem(
                                song = song,
                                rank = index + 1,
                                musicViewModel = musicViewModel,
                                context = context,
                                onClick = {
                                    scope.launch {
                                        // Convert OnlineSong to Song for playback
                                        val mappedSong = Song(
                                            title = song.title,
                                            artist = song.artist,
                                            coverUri = song.artworkUrl,
                                            uri = song.audioUrl,
                                            duration = song.duration // Keep the original mm:ss format
                                        )
                                        musicViewModel.playSong(
                                            mappedSong, 
                                            context,
                                            fromOnlinePlaylist = true,
                                            onlineType = chartType,
                                            onlineSongId = song.id
                                        )
                                        onNavigateToPlayer()
                                    }
                                }
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
}

@Composable
fun TrendingSongItem(
    song: OnlineSong,
    rank: Int,
    musicViewModel: MusicViewModel,
    context: android.content.Context,
    onClick: () -> Unit
) {
    // Check if the song is already downloaded
    var isDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    
    // Convert OnlineSong to Song for checking download status
    val localSong = remember(song) {
        Song(
            title = song.title,
            artist = song.artist,
            coverUri = song.artworkUrl,
            uri = song.audioUrl,
            duration = song.duration
        )
    }
    
    LaunchedEffect(song) {
        isDownloaded = musicViewModel.isSongDownloaded(localSong, context)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDownloaded) Color(0xFF1DB954).copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number with download indicator
        Box(
            modifier = Modifier.width(30.dp)
        ) {
            Text(
                text = rank.toString(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            if (isDownloaded) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Downloaded",
                    tint = Color(0xFF1DB954),
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Album artwork
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Song info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Duration
        Text(
            text = song.duration,
            color = Color.Gray,
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Download button
        IconButton(
            onClick = {
                if (!isDownloaded && !isDownloading) {
                    isDownloading = true
                    musicViewModel.downloadSong(
                        song = localSong,
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
    }
}
