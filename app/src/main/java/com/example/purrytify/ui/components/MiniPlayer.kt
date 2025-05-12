package com.example.purrytify.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import coil.compose.AsyncImage
import com.example.purrytify.ui.screens.Song
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MiniPlayer(
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userEmail = "13522126@std.stei.itb.ac.id"

    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentPosition by musicViewModel.currentPosition.collectAsState()
    val duration by musicViewModel.duration.collectAsState()
    var isSongLiked by remember { mutableStateOf(false) }
    val currentSongId = remember { mutableStateOf(-1) }

    LaunchedEffect(currentSong) {
        currentSong?.let { song ->
            val songId = try {
                songViewModel.getSongId(song.title, song.artist)
            } catch (e: Exception) {
                -1
            }
            currentSongId.value = songId

            isSongLiked = if (songId != -1) {
                songViewModel.isSongLiked(songId)
            } else {
                false
            }
        } ?: run {
            isSongLiked = false
            currentSongId.value = -1
        }
    }

    currentSong?.let { song ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF3D1D29))
        ) {
            val progress = remember(currentPosition, duration) {
                if (duration > 0) {
                    (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else 0f
            }
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(onClick = onPlayerClick),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3D1D29)
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = if (song.coverUri.isNotEmpty())
                            File(song.coverUri)
                        else
                            "https://example.com/placeholder.jpg",
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = song.artist.split(",").firstOrNull() ?: "",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                if (currentSongId.value != -1) {
                                    if (isSongLiked) {
                                        songViewModel.unlikeSong(currentSongId.value)
                                        isSongLiked = false
                                        Toast.makeText(context, "Removed from Liked Songs", Toast.LENGTH_SHORT).show()
                                    } else {
                                        songViewModel.likeSong(currentSongId.value)
                                        isSongLiked = true
                                        Toast.makeText(context, "Added to Liked Songs", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isSongLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isSongLiked) "Unlike" else "Like",
                            tint = if (isSongLiked) Color(0xFFE91E63) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Share button
                    IconButton(
                        onClick = {
                            musicViewModel.shareSong(song, context)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { musicViewModel.togglePlayPause() }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}