package com.example.purrytify.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.purrytify.ui.screens.Song
import com.example.purrytify.ui.viewmodel.MusicViewModel

@Composable
fun ShowQueueDialog(
    showQueueDialog: Boolean,
    onDismiss: () -> Unit,
    musicViewModel: MusicViewModel,
    context: Context
) {
    if (showQueueDialog) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF121212),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Now playing",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val currentSong by musicViewModel.currentSong.collectAsState()

                    currentSong?.let { song ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = rememberAsyncImagePainter(song.coverUri),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = song.title, color = Color.White)
                                Text(text = song.artist, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Next in Queue",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { musicViewModel.clearQueue() }) {
                            Text("Clear Queue", color = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val queue by musicViewModel.songQueue.collectAsState()

                    if (queue.isEmpty()) {
                        Text(
                            text = "Queue is empty",
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn {
                            items(queue) { song: Song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable {
                                            musicViewModel.playSong(song, context)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(song.coverUri),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = song.title, color = Color.White)
                                        Text(
                                            text = song.artist,
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
