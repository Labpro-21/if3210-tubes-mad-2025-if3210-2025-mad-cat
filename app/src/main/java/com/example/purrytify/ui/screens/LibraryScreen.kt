package com.example.purrytify.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.components.BottomNavBar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

data class Song(
    val title: String,
    val artist: String,
    val coverUri: String,
    val uri: String,
    val duration: String
)

fun extractMetadataFromAudio(context: android.content.Context, uri: Uri): Pair<String, String> {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)

        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?:
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: ""

        return Pair(title, artist)
    } catch (e: Exception) {
        e.printStackTrace()
        return Pair("", "")
    } finally {
        retriever.release()
    }
}

@Composable
fun LibraryScreen(
    navController: NavController,
    songViewModel: SongViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val userEmail = remember { "13522126@std.stei.itb.ac.id" } // This would typically come from your auth system
    val scope = rememberCoroutineScope()

    var selectedLibraryMode by remember { mutableStateOf("All") }

    // Get both all songs and liked songs
    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    val likedSongs = songViewModel.likedSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    // Determine which songs to display based on selected mode
    val songsToDisplay = remember(selectedLibraryMode, allSongs.value, likedSongs.value) {
        when (selectedLibraryMode) {
            "Liked" -> likedSongs.value
            else -> allSongs.value
        }
    }

    var showUploadDialog by remember { mutableStateOf(false) }

    var newSongTitle by remember { mutableStateOf("") }
    var newSongArtist by remember { mutableStateOf("") }
    var newSongImageUri by remember { mutableStateOf<Uri?>(null) }
    var newSongAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioDuration by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Debug state to track saved image path
    var debugInfo by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        newSongImageUri = uri

        // Log the URI for debugging
        if (uri != null) {
            Log.d("LibraryScreen", "Selected image URI: $uri")
            debugInfo = "Selected image: $uri"
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            newSongAudioUri = it
            Log.d("LibraryScreen", "Selected audio URI: $uri")

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

            Log.d("LibraryScreen", "Saved audio file to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("LibraryScreen", "Error saving audio file", e)
            e.printStackTrace()
            null
        }
    }

    fun saveImageFile(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("LibraryScreen", "Failed to open input stream for image URI: $uri")
                return null
            }

            val fileName = "cover_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("LibraryScreen", "Saved image file to: ${file.absolutePath}")
            debugInfo = "Saved image to: ${file.absolutePath}"

            // Verify the file was created and has content
            if (file.exists() && file.length() > 0) {
                Log.d("LibraryScreen", "Image file exists and has size: ${file.length()}")
            } else {
                Log.e("LibraryScreen", "Image file doesn't exist or is empty: ${file.absolutePath}")
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e("LibraryScreen", "Error saving image file", e)
            debugInfo = "Error saving image: ${e.message}"
            e.printStackTrace()
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(onClick = {
                    showUploadDialog = true
                    debugInfo = "" // Clear debug info
                }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Song",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF2A2A2A))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selectedLibraryMode == "All") Color(0xFF1DB954) else Color.Transparent)
                            .clickable { selectedLibraryMode = "All" }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "All",
                            color = if (selectedLibraryMode == "All") Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selectedLibraryMode == "Liked") Color(0xFF1DB954) else Color.Transparent)
                            .clickable { selectedLibraryMode = "Liked" }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Liked",
                            color = if (selectedLibraryMode == "Liked") Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (songsToDisplay.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Show different message based on selected mode
                        if (selectedLibraryMode == "Liked") {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No liked songs yet",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Like your favorite songs to see them here",
                                style = TextStyle(
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "Your library is empty",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add songs by clicking the + button",
                                style = TextStyle(
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 56.dp)
                ) {
                    items(songsToDisplay) { song ->
                        LibrarySongItem(
                            song = song,
                            onClick = {
                                scope.launch {
                                    musicViewModel.playSong(song, context)
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
            currentRoute = "library",
            onMiniPlayerClick = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showUploadDialog) {
            Dialog(
                onDismissRequest = {
                    if (!isUploading) {
                        showUploadDialog = false
                        newSongTitle = ""
                        newSongArtist = ""
                        newSongImageUri = null
                        newSongAudioUri = null
                        audioDuration = ""
                        errorMessage = null
                        debugInfo = ""
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
                            text = "Upload Song",
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
                                horizontalAlignment = CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2A2A2A))
                                        .clickable {
                                            imagePickerLauncher.launch("image/*")
                                        }
                                        .border(
                                            width = 1.dp,
                                            color = Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Center
                                ) {
                                    if (newSongImageUri != null) {
                                        AsyncImage(
                                            model = newSongImageUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color.Gray
                                            )
                                            Text(
                                                text = "Upload Cover",
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Column(
                                horizontalAlignment = CenterHorizontally
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
                                    contentAlignment = Center
                                ) {
                                    Column(
                                        horizontalAlignment = CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (newSongAudioUri != null) Color(0xFF1DB954) else Color.Gray
                                        )
                                        Text(
                                            text = "Upload File",
                                            color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color.Gray,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        if (audioDuration.isNotEmpty()) {
                                            Text(
                                                text = audioDuration,
                                                color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Show debug info when available
                        if (debugInfo.isNotEmpty()) {
                            Text(
                                text = debugInfo,
                                color = Color.Yellow,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

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
                                        showUploadDialog = false
                                        newSongTitle = ""
                                        newSongArtist = ""
                                        newSongImageUri = null
                                        newSongAudioUri = null
                                        audioDuration = ""
                                        errorMessage = null
                                        debugInfo = ""
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

                                    if (newSongAudioUri == null) {
                                        errorMessage = "Please select an audio file"
                                        return@Button
                                    }

                                    errorMessage = null
                                    isUploading = true
                                    debugInfo = "Starting upload..."

                                    scope.launch {
                                        try {
                                            val savedAudioPath = saveAudioFile(newSongAudioUri!!)

                                            if (savedAudioPath == null) {
                                                errorMessage = "Failed to save audio file"
                                                isUploading = false
                                                return@launch
                                            }

                                            // ONLY save the image if provided by user - no fallback to metadata
                                            var savedImagePath = ""
                                            if (newSongImageUri != null) {
                                                debugInfo = "Processing image upload..."
                                                val path = saveImageFile(newSongImageUri!!)
                                                if (path != null && path.isNotEmpty()) {
                                                    savedImagePath = path
                                                    debugInfo = "Image saved successfully to: $savedImagePath"
                                                } else {
                                                    debugInfo = "Failed to save image"
                                                }
                                            } else {
                                                debugInfo = "No cover image provided by user"
                                            }

                                            val newSong = Song(
                                                title = newSongTitle,
                                                artist = newSongArtist,
                                                coverUri = savedImagePath, // Will be empty if no image was uploaded
                                                uri = savedAudioPath,
                                                duration = audioDuration
                                            )

                                            songViewModel.checkAndInsertSong(
                                                context,
                                                newSong,
                                                userEmail,
                                                onExists = {
                                                    errorMessage = "Song already exists in your library"
                                                    isUploading = false
                                                }
                                            )

                                            isUploading = false
                                            showUploadDialog = false
                                            newSongTitle = ""
                                            newSongArtist = ""
                                            newSongImageUri = null
                                            newSongAudioUri = null
                                            audioDuration = ""
                                            debugInfo = ""

                                            Toast.makeText(context, "Song added to library", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("LibraryScreen", "Error in upload process", e)
                                            errorMessage = "Error: ${e.message}"
                                            debugInfo = "Upload error: ${e.message}"
                                            isUploading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1DB954)
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isUploading && newSongTitle.isNotBlank() && newSongArtist.isNotBlank() && newSongAudioUri != null
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Save", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySongItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Check if the song has a valid cover image
        val hasCover = song.coverUri.isNotEmpty() && File(song.coverUri).exists()

        if (hasCover) {
            // Display the actual cover image
            AsyncImage(
                model = File(song.coverUri),
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                onError = {
                    // Log when image loading fails
                    Log.e("LibrarySongItem", "Failed to load image from ${song.coverUri}")
                }
            )
        } else {
            // Display a placeholder with music note icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = TextStyle(
                    color = Color.Gray,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}