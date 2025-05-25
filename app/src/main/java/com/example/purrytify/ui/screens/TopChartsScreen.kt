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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.purrytify.R
import com.example.purrytify.data.model.OnlineSong
import com.example.purrytify.ui.viewmodel.HomeViewModel
import com.example.purrytify.ui.viewmodel.HomeViewModelFactory
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.components.AdaptiveNavigation
import com.example.purrytify.ui.dialogs.SongOptionsDialog
import com.example.purrytify.ui.dialogs.ShareSongDialog
import com.example.purrytify.ui.viewmodel.SongViewModel
import com.example.purrytify.ui.screens.Song
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.foundation.Image

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
    
    val chartTitle = if (chartType == "global") "Top 50 Global" else "Top 10 ${countryName ?: chartType}"
    val coverDrawableId = if (chartType == "global") R.drawable.top50global else R.drawable.top50indonesia
    
    val headerGradient = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1DB954), Color(0xFF0E5C27), Color(0xFF121212)),
            startY = 0f,
            endY = 1200f
        )
    }
    
    LaunchedEffect(chartType) {
        if (chartType == "global") {
            homeViewModel.fetchGlobalTopSongs()
        } else {
            homeViewModel.fetchCountryTopSongs(chartType)
        }
    }
    
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
    
    AdaptiveNavigation(
        navController = navController,
        musicViewModel = musicViewModel,
        songViewModel = songViewModel,
        currentRoute = "home",
        onMiniPlayerClick = onNavigateToPlayer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
            
            when {
                isLoading.value -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF1DB954)
                        )
                    }
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs available",
                            color = Color.Gray
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(headerGradient),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .align(Alignment.CenterHorizontally)
                                ) {
                                    Image(
                                        painter = painterResource(id = coverDrawableId),
                                        contentDescription = chartTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Text(
                                    text = chartTitle,
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Most played songs on Purrytify",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                        
                        itemsIndexed(songs.value) { index, song ->
                            TrendingSongItem(
                                song = song,
                                rank = index + 1,
                                musicViewModel = musicViewModel,
                                context = context,
                                onClick = {
                                    scope.launch {
                                        val mappedSong = Song(
                                            title = song.title,
                                            artist = song.artist,
                                            coverUri = song.artworkUrl,
                                            uri = song.audioUrl,
                                            duration = song.duration
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
    var isDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    
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
                if (isDownloaded) Color(0xFF1DB954).copy(alpha = 0.1f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp))
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
        
        Text(
            text = song.duration,
            color = Color.Gray,
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
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
    
    if (showOptionsDialog) {
        SongOptionsDialog(
            song = localSong,
            isOnlineSong = true,
            onShareClick = {
                showShareDialog = true
            },
            onEditClick = {
            },
            onDeleteClick = {
            },
            onDismiss = { showOptionsDialog = false }
        )
    }
    
    if (showShareDialog) {
        ShareSongDialog(
            songId = song.id,
            songTitle = song.title,
            songArtist = song.artist,
            songUrl = song.audioUrl,
            onDismiss = { showShareDialog = false }
        )
    }
}