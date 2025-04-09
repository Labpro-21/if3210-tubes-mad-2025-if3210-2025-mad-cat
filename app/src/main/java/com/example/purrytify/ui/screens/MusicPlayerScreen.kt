package com.example.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.purrytify.ui.viewmodel.MusicViewModel
import java.io.File
import kotlin.math.floor

@Composable
fun MusicPlayerScreen(
    musicViewModel: MusicViewModel,
    onBackClick: () -> Unit
) {
    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentPosition by musicViewModel.currentPosition.collectAsState()
    val duration by musicViewModel.duration.collectAsState()

    val context = LocalContext.current

    if (currentSong == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            Text("No song is currently playing", color = Color.White)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF400017)) // Gradient background would be better
    ) {
        // Back button at the top
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Options menu
        IconButton(
            onClick = { /* Show options menu */ },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.White
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Album art
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = if (currentSong!!.coverUri.isNotEmpty())
                        File(currentSong!!.coverUri)
                    else
                        "https://example.com/placeholder.jpg",
                    contentDescription = "Album Cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Song title and artist
            Text(
                text = currentSong!!.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentSong!!.artist,
                color = Color.LightGray,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress slider
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { musicViewModel.seekTo(it.toInt()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Time indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Text(
                        text = formatDuration(duration),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Player controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button
                IconButton(
                    onClick = { /* Previous song */ },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause button
                IconButton(
                    onClick = { musicViewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = { /* Next song */ },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// Helper function to format duration
private fun formatDuration(milliseconds: Int): String {
    val totalSeconds = floor(milliseconds / 1000f).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}