package com.example.purrytify.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.purrytify.ui.components.BottomNavBar
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import java.io.File

@Composable
fun TopSongsScreen(
    navController: NavController,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current

    val timeListened by ListeningAnalytics.timeListened.collectAsStateWithLifecycle()
    val songListeningData = remember(timeListened) { ListeningAnalytics.getAllSongListeningData() }

    val songCount = songListeningData.size

    val gradientColors = listOf(
        Color(0xFF095256),
        Color(0xFF121212)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "Most Played Songs",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("You listened to ")
                        withStyle(style = SpanStyle(
                            color = Color(0xFFFFD700), // Gold/yellow color
                            fontWeight = FontWeight.Bold
                        )) {
                            append("$songCount")
                        }
                        append(" different songs this month")
                    },
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(bottom = 56.dp)
            ) {
                itemsIndexed(songListeningData) { index, (title, playCount, duration) ->
                    TopSongItem(
                        index = index + 1,
                        title = title,
                        playCount = playCount,
                        duration = duration,
                        songViewModel = songViewModel,
                        musicViewModel = musicViewModel, // Pass musicViewModel here
                        onClick = {
                            val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList()).value
                            allSongs.find { it.title == title }?.let { song ->
                                musicViewModel.playSong(song, context)
                                onNavigateToPlayer()
                            }
                        }
                    )
                    
                    Divider(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        BottomNavBar(
            navController = navController,
            musicViewModel = musicViewModel,
            songViewModel = songViewModel,
            currentRoute = "top_songs",
            onMiniPlayerClick = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun TopSongItem(
    index: Int,
    title: String, 
    playCount: Int,
    duration: Long,
    songViewModel: SongViewModel,
    musicViewModel: MusicViewModel,
    onClick: @Composable () -> Unit
) {
    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val song = allSongs.find { it.title == title }
    
    val currentPosition by musicViewModel.currentPosition.collectAsState()
    val totalDuration by musicViewModel.duration.collectAsState()
    val currentSong by musicViewModel.currentSong.collectAsState() 
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index number
            Text(
                text = String.format("%02d", index),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.width(40.dp)
            )
            
            // Song details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                song?.let {
                    Text(
                        text = it.artist,
                        style = TextStyle(
                            color = Color.Gray,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    text = "${ListeningAnalytics.formatDuration(duration)} of listening time",
                    style = TextStyle(
                        color = Color(0xFF1DB954),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Cover art
            song?.let { currentSong ->
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    if (currentSong.coverUri.isNotEmpty()) {
                        AsyncImage(
                            model = File(currentSong.coverUri),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2A2A2A))
                        )
                    }
                }
            } ?: Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A2A2A))
            )
        }

    }
}

