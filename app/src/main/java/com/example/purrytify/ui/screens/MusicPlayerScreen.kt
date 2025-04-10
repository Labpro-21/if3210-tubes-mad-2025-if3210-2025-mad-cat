package com.example.purrytify.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.RepeatMode
import com.example.purrytify.ui.viewmodel.SongViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor

@Composable
fun MusicPlayerScreen(
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userEmail = "13522126@std.stei.itb.ac.id" // This would typically come from your auth system

    // Initialize playback controls when the screen is first composed
    LaunchedEffect(Unit) {
        musicViewModel.initializePlaybackControls(songViewModel, context)
    }

    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentPosition by musicViewModel.currentPosition.collectAsState()
    val duration by musicViewModel.duration.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()
    val isShuffleOn by musicViewModel.isShuffleOn.collectAsState()

    // States for edit and delete functionality
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var newSongTitle by remember { mutableStateOf("") }
    var newSongArtist by remember { mutableStateOf("") }
    var newSongImageUri by remember { mutableStateOf<Uri?>(null) }
    var newSongAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioDuration by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        newSongImageUri = uri
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            newSongAudioUri = it

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, it)

                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
                val seconds = (durationMs / 1000) % 60
                val minutes = (durationMs / (1000 * 60)) % 60
                audioDuration = "$minutes:${String.format("%02d", seconds)}"

                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!title.isNullOrBlank()) {
                    newSongTitle = title
                }

                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?:
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                if (!artist.isNullOrBlank()) {
                    newSongArtist = artist
                }

            } catch (e: Exception) {
                e.printStackTrace()
                audioDuration = "0:00"
            } finally {
                retriever.release()
            }
        }
    }

    fun saveAudioFile(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "song_${System.currentTimeMillis()}.mp3"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveImageFile(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "cover_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun handleEditSong(song: Song) {
        newSongTitle = song.title
        newSongArtist = song.artist
        audioDuration = song.duration

        // We're keeping the original audio and image paths unless the user changes them
        newSongAudioUri = null
        newSongImageUri = null

        showEditDialog = true
    }

    fun handleDeleteSong(song: Song) {
        showDeleteConfirmation = true
    }

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
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = { showMenu = true }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF2A2A2A))
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Song", color = Color.White) },
                    onClick = {
                        currentSong?.let { handleEditSong(it) }
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color.White
                        )
                    }
                )

                DropdownMenuItem(
                    text = { Text("Delete Song", color = Color.Red) },
                    onClick = {
                        currentSong?.let { handleDeleteSong(it) }
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red
                        )
                    }
                )
            }
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
                // Shuffle button
                IconButton(
                    onClick = { musicViewModel.toggleShuffle() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleOn) Color.Green else Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Previous button
                IconButton(
                    onClick = { musicViewModel.playPrevious() },
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
                    onClick = { musicViewModel.playNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat button
                IconButton(
                    onClick = { musicViewModel.toggleRepeatMode() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.OFF -> Icons.Default.Repeat
                            RepeatMode.ALL -> Icons.Default.Repeat
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                        },
                        contentDescription = "Repeat",
                        tint = when (repeatMode) {
                            RepeatMode.OFF -> Color.White
                            RepeatMode.ALL -> Color.Green
                            RepeatMode.ONE -> Color.Green
                        },
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }

    // Edit Song Dialog
    if (showEditDialog && currentSong != null) {
        Dialog(
            onDismissRequest = {
                if (!isUploading) {
                    showEditDialog = false
                    newSongTitle = ""
                    newSongArtist = ""
                    newSongImageUri = null
                    newSongAudioUri = null
                    audioDuration = ""
                    errorMessage = null
                }
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF121212)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Edit Song",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2A2A2A))
                                    .clickable { imagePickerLauncher.launch("image/*") }
                                    .border(
                                        width = 1.dp,
                                        color = Color.Gray,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (newSongImageUri != null) {
                                    AsyncImage(
                                        model = newSongImageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (currentSong!!.coverUri.isNotEmpty() && File(currentSong!!.coverUri).exists()) {
                                    AsyncImage(
                                        model = File(currentSong!!.coverUri),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                        Text(
                                            text = "Change Cover",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2A2A2A))
                                    .clickable { audioPickerLauncher.launch("audio/*") }
                                    .border(
                                        width = 1.dp,
                                        color = Color.Gray,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (newSongAudioUri != null) Color(0xFF1DB954) else Color.Gray
                                    )
                                    Text(
                                        text = if (newSongAudioUri != null) "New File" else "Keep Current",
                                        color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color.Gray,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = if (newSongAudioUri != null) audioDuration else currentSong!!.duration,
                                        color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Title",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newSongTitle,
                        onValueChange = { newSongTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Title", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Artist",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newSongArtist,
                        onValueChange = { newSongArtist = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Artist", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (!isUploading) {
                                    showEditDialog = false
                                    newSongTitle = ""
                                    newSongArtist = ""
                                    newSongImageUri = null
                                    newSongAudioUri = null
                                    audioDuration = ""
                                    errorMessage = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A2A2A)
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            enabled = !isUploading
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = {
                                if (newSongTitle.isBlank()) {
                                    errorMessage = "Title cannot be empty"
                                    return@Button
                                }

                                if (newSongArtist.isBlank()) {
                                    errorMessage = "Artist cannot be empty"
                                    return@Button
                                }

                                errorMessage = null
                                isUploading = true

                                scope.launch {
                                    try {
                                        val oldSong = currentSong!!

                                        // Handle audio file
                                        val audioPath = if (newSongAudioUri != null) {
                                            val savedPath = saveAudioFile(newSongAudioUri!!)
                                            if (savedPath == null) {
                                                errorMessage = "Failed to save audio file"
                                                isUploading = false
                                                return@launch
                                            }
                                            savedPath
                                        } else {
                                            oldSong.uri
                                        }

                                        // Handle cover image
                                        val imagePath = if (newSongImageUri != null) {
                                            saveImageFile(newSongImageUri!!) ?: ""
                                        } else {
                                            oldSong.coverUri
                                        }

                                        // Create the updated song
                                        val updatedSong = Song(
                                            title = newSongTitle,
                                            artist = newSongArtist,
                                            coverUri = imagePath,
                                            uri = audioPath,
                                            duration = if (newSongAudioUri != null) audioDuration else oldSong.duration
                                        )

                                        // Update the song
                                        songViewModel.updateSong(
                                            oldSong = oldSong,
                                            newSong = updatedSong,
                                            onComplete = {
                                                isUploading = false
                                                showEditDialog = false
                                                newSongTitle = ""
                                                newSongArtist = ""
                                                newSongImageUri = null
                                                newSongAudioUri = null
                                                audioDuration = ""
                                                errorMessage = null

                                                // Need to update the currently playing song too
                                                musicViewModel.updateCurrentSong(updatedSong)

                                                scope.launch {
                                                    Toast.makeText(context, "Song updated", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    } catch (e: Exception) {
                                        errorMessage = "Error: ${e.message}"
                                        isUploading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954)
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            enabled = !isUploading && newSongTitle.isNotBlank() && newSongArtist.isNotBlank()
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Update", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation && currentSong != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
            },
            title = {
                Text(
                    text = "Delete Song",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${currentSong!!.title}\" by ${currentSong!!.artist}?",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val songToDelete = currentSong!!

                            // First stop playing and move to next song if possible
                            musicViewModel.stopAndClearCurrentSong()

                            // Then delete the song
                            songViewModel.deleteSong(
                                song = songToDelete,
                                onComplete = {
                                    showDeleteConfirmation = false
                                    Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                                    // Return to previous screen after deletion
                                    onBackClick()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF2A2A2A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

private fun formatDuration(milliseconds: Int): String {
    val totalSeconds = floor(milliseconds / 1000f).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}