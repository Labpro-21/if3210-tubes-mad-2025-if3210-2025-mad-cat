package com.example.purrytify.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.InputFilter
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.purrytify.ui.components.AdaptiveNavigation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import com.example.purrytify.R
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import android.view.inputmethod.EditorInfo
import com.example.purrytify.ui.dialogs.SongOptionsDialog
import com.example.purrytify.ui.dialogs.ShareSongDialog
import androidx.compose.material.icons.filled.MoreVert
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.utils.isLandscape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    songViewModel: SongViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedLibraryMode by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    // Update the SongViewModel with current user email when screen loads
    LaunchedEffect(Unit) {
        val tokenManager = TokenManager(context)
        val currentEmail = tokenManager.getEmail() ?: "guest@example.com"
        songViewModel.updateUserEmail(currentEmail)
        Log.d("LibraryScreen", "Updated SongViewModel with email: $currentEmail")
    }

    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    val likedSongs =
        songViewModel.likedSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    val filteredSongs =
        remember(selectedLibraryMode, allSongs.value, likedSongs.value, searchQuery) {
            val songs = when (selectedLibraryMode) {
                "Liked" -> likedSongs.value
                else -> allSongs.value
            }
            if (searchQuery.isNotBlank()) {
                songs.filter {
                    it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(
                        searchQuery,
                        ignoreCase = true
                    )
                }
            } else {
                songs
            }
        }

    val songsToDisplay = remember(selectedLibraryMode, allSongs.value, likedSongs.value) {
        when (selectedLibraryMode) {
            "Liked" -> likedSongs.value
            else -> allSongs.value
        }
    }

    var showUploadDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedSongForEdit by remember { mutableStateOf<Song?>(null) }

    var newSongTitle by remember { mutableStateOf("") }
    var newSongArtist by remember { mutableStateOf("") }
    var newSongImageUri by remember { mutableStateOf<Uri?>(null) }
    var newSongAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioDuration by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var debugInfo by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        newSongImageUri = uri

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

                val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLong() ?: 0
                val seconds = (durationMs / 1000) % 60
                val minutes = (durationMs / (1000 * 60)) % 60
                audioDuration = "$minutes:${String.format("%02d", seconds)}"

                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!title.isNullOrBlank()) {
                    newSongTitle = title
                }

                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
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

    AdaptiveNavigation(
        navController = navController,
        musicViewModel = musicViewModel,
        songViewModel = songViewModel,
        currentRoute = "library",
        onMiniPlayerClick = onNavigateToPlayer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
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
                    debugInfo = ""
                }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Song",
                        tint = Color.White
                    )
                }
            }

            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        hint = "What do you want to listen to?"
                        setHintTextColor(android.graphics.Color.DKGRAY)
                        setTextColor(android.graphics.Color.BLACK)
                        setPadding(32, 32, 32, 32)
                        textSize = 14f
                        background = AppCompatResources.getDrawable(context, R.drawable.bg_search)

                        val searchDrawable =
                            AppCompatResources.getDrawable(context, R.drawable.ic_search)
                        searchDrawable?.setBounds(
                            0,
                            0,
                            searchDrawable.intrinsicWidth,
                            searchDrawable.intrinsicHeight
                        )
                        setCompoundDrawablesWithIntrinsicBounds(searchDrawable, null, null, null)
                        compoundDrawablePadding = 16

                        filters = arrayOf(
                            InputFilter { source, _, _, _, _, _ ->
                                if (source != null && source.matches("[a-zA-Z ]+".toRegex())) {
                                    return@InputFilter source
                                }
                                return@InputFilter ""
                            }
                        )
                    }
                },
                update = { editText ->
                    editText.setText(searchQuery)
                    editText.setSelection(searchQuery.length)
                    editText.addTextChangedListener {
                        searchQuery = it.toString()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

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

            if (filteredSongs.isEmpty()) {
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
                        if (selectedLibraryMode == "Liked" && searchQuery.isBlank()) {
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
                                text = "No songs found",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try searching with a different keyword",
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
                    items(filteredSongs) { song ->
                        LibrarySongItem(
                            song = song,
                            songViewModel = songViewModel,
                            musicViewModel = musicViewModel,
                            onEditClick = { selectedSong ->
                                selectedSongForEdit = selectedSong
                                newSongTitle = selectedSong.title
                                newSongArtist = selectedSong.artist
                                audioDuration = selectedSong.duration
                                newSongAudioUri = null
                                newSongImageUri = null
                                showEditDialog = true
                            },
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
    }

    if (showUploadDialog) {
        val isLandscapeMode = isLandscape()
        val scrollState = rememberScrollState()
        
        ModalBottomSheet(
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
            },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF1E1E1E),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF555555))
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = if (isLandscapeMode) 16.dp else 24.dp,
                        vertical = if (isLandscapeMode) 12.dp else 16.dp
                    )
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Text(
                    text = "Upload Song",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = if (isLandscapeMode) 20.sp else 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = if (isLandscapeMode) 12.dp else 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isLandscapeMode) 100.dp else 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable { imagePickerLauncher.launch("image/*") }
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF444444),
                                    shape = RoundedCornerShape(12.dp)
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
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Image,
                                        contentDescription = null,
                                        tint = Color(0xFF888888),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add Cover",
                                        color = Color(0xFF888888),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(if (isLandscapeMode) 6.dp else 8.dp))
                        Text(
                            text = "Cover Art",
                            color = Color(0xFFCCCCCC),
                            fontSize = if (isLandscapeMode) 11.sp else 12.sp
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isLandscapeMode) 100.dp else 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable { audioPickerLauncher.launch("audio/*") }
                                .border(
                                    width = 1.dp,
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                        0xFF444444
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    tint = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                        0xFF888888
                                    ),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (newSongAudioUri != null) "File Selected" else "Add Audio",
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                        0xFF888888
                                    ),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                if (audioDuration.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = audioDuration,
                                        color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                            0xFF888888
                                        ),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(if (isLandscapeMode) 6.dp else 8.dp))
                        Text(
                            text = "Audio File",
                            color = Color(0xFFCCCCCC),
                            fontSize = if (isLandscapeMode) 11.sp else 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscapeMode) 16.dp else 24.dp))

                Text(
                    text = "Title",
                    color = Color(0xFFCCCCCC),
                    fontSize = if (isLandscapeMode) 13.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = if (isLandscapeMode) 6.dp else 8.dp)
                )

                OutlinedTextField(
                    value = newSongTitle,
                    onValueChange = { newSongTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter song title", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF1DB954)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Artist",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = newSongArtist,
                    onValueChange = { newSongArtist = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter artist name", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF1DB954)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 16.sp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscapeMode) 16.dp else 24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
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
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFCCCCCC))
                    }

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

                                    var savedImagePath = ""
                                    if (newSongImageUri != null) {
                                        debugInfo = "Processing image upload..."
                                        val path = saveImageFile(newSongImageUri!!)
                                        if (path != null && path.isNotEmpty()) {
                                            savedImagePath = path
                                            debugInfo = "Image saved successfully"
                                        } else {
                                            debugInfo = "Failed to save image"
                                        }
                                    } else {
                                        debugInfo = "No cover image provided"
                                    }

                                    val newSong = Song(
                                        title = newSongTitle,
                                        artist = newSongArtist,
                                        coverUri = savedImagePath,
                                        uri = savedAudioPath,
                                        duration = audioDuration
                                    )

                                    songViewModel.checkAndInsertSong(
                                        context,
                                        newSong,
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

                                    Toast.makeText(
                                        context,
                                        "Song added to library",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    errorMessage = "Error: ${e.message}"
                                    debugInfo = "Upload error: ${e.message}"
                                    isUploading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isUploading && newSongTitle.isNotBlank() && newSongArtist.isNotBlank() && newSongAudioUri != null,
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Upload Song", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscapeMode) 12.dp else 16.dp))
            }
        }
    }

    // Edit song dialog
    if (showEditDialog && selectedSongForEdit != null) {
        val isLandscapeModeEdit = isLandscape()
        val scrollStateEdit = rememberScrollState()
        
        val editImagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            newSongImageUri = uri
        }

        val editAudioPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                newSongAudioUri = it
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, it)
                    val durationMs =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLong() ?: 0
                    val seconds = (durationMs / 1000) % 60
                    val minutes = (durationMs / (1000 * 60)) % 60
                    audioDuration = "$minutes:${String.format("%02d", seconds)}"
                } catch (e: Exception) {
                    e.printStackTrace()
                    audioDuration = "0:00"
                } finally {
                    retriever.release()
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                if (!isUploading) {
                    showEditDialog = false
                    selectedSongForEdit = null
                    newSongTitle = ""
                    newSongArtist = ""
                    newSongImageUri = null
                    newSongAudioUri = null
                    audioDuration = ""
                    errorMessage = null
                }
            },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF1E1E1E),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF555555))
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollStateEdit)
                    .padding(
                        horizontal = if (isLandscapeModeEdit) 16.dp else 24.dp,
                        vertical = if (isLandscapeModeEdit) 12.dp else 16.dp
                    )
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Text(
                    text = "Edit Song",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable { editImagePickerLauncher.launch("image/*") }
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF444444),
                                    shape = RoundedCornerShape(12.dp)
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
                            } else if (selectedSongForEdit!!.coverUri.isNotEmpty() && File(
                                    selectedSongForEdit!!.coverUri
                                ).exists()
                            ) {
                                AsyncImage(
                                    model = File(selectedSongForEdit!!.coverUri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF888888),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Change Cover",
                                        color = Color(0xFF888888),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cover Art",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable { editAudioPickerLauncher.launch("audio/*") }
                                .border(
                                    width = 1.dp,
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                        0xFF444444
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    tint = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                        0xFF888888
                                    ),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (newSongAudioUri != null) "New File" else "Keep Current",
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                        0xFF888888
                                    ),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (newSongAudioUri != null) audioDuration else selectedSongForEdit!!.duration,
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(
                                        0xFF888888
                                    ),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Audio File",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Title",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = newSongTitle,
                    onValueChange = { newSongTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter song title", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF1DB954)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Artist",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = newSongArtist,
                    onValueChange = { newSongArtist = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter artist name", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF1DB954)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 16.sp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            if (!isUploading) {
                                showEditDialog = false
                                selectedSongForEdit = null
                                newSongTitle = ""
                                newSongArtist = ""
                                newSongImageUri = null
                                newSongAudioUri = null
                                audioDuration = ""
                                errorMessage = null
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFCCCCCC))
                    }

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
                                    val oldSong = selectedSongForEdit!!

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

                                    val imagePath = if (newSongImageUri != null) {
                                        saveImageFile(newSongImageUri!!) ?: ""
                                    } else {
                                        oldSong.coverUri
                                    }

                                    val updatedSong = Song(
                                        title = newSongTitle,
                                        artist = newSongArtist,
                                        coverUri = imagePath,
                                        uri = audioPath,
                                        duration = if (newSongAudioUri != null) audioDuration else oldSong.duration
                                    )

                                    songViewModel.updateSong(
                                        oldSong = oldSong,
                                        newSong = updatedSong,
                                        onComplete = {
                                            isUploading = false
                                            showEditDialog = false
                                            selectedSongForEdit = null
                                            newSongTitle = ""
                                            newSongArtist = ""
                                            newSongImageUri = null
                                            newSongAudioUri = null
                                            audioDuration = ""
                                            errorMessage = null
                                            musicViewModel.updateCurrentSong(updatedSong)

                                            scope.launch {
                                                Toast.makeText(
                                                    context,
                                                    "Song updated",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isUploading && newSongTitle.isNotBlank() && newSongArtist.isNotBlank(),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save Changes", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongItem(
    song: Song,
    songViewModel: SongViewModel,
    musicViewModel: MusicViewModel,
    onEditClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableStateOf(-1) }
    
    LaunchedEffect(song) {
        try {
            currentSongId = songViewModel.getSongId(song.title, song.artist)
        } catch (e: Exception) {
            currentSongId = -1
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val hasCover = song.coverUri.isNotEmpty() && File(song.coverUri).exists()

        if (hasCover) {
            AsyncImage(
                model = File(song.coverUri),
                contentDescription = song.title,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                onError = {
                    Log.e("LibrarySongItem", "Failed to load image from ${song.coverUri}")
                }
            )
        } else {
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
        
        // Three dots menu button
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
            isOnlineSong = false,
            onShareClick = {
                showShareDialog = true
            },
            onEditClick = {
                onEditClick(song)
            },
            onDeleteClick = {
                showDeleteConfirmation = true
            },
            onDismiss = { showOptionsDialog = false }
        )
    }
    
    // Show share dialog
    if (showShareDialog) {
        ShareSongDialog(
            songId = currentSongId,
            songTitle = song.title,
            songArtist = song.artist,
            songUrl = null,
            onDismiss = { showShareDialog = false }
        )
    }
    
    // Delete confirmation
    if (showDeleteConfirmation) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteConfirmation = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF1E1E1E),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF555555))
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Delete Song",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Are you sure you want to delete \"${song.title}\" by ${song.artist}?",
                    color = Color(0xFFCCCCCC),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmation = false },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFCCCCCC))
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                songViewModel.deleteSong(
                                    song = song,
                                    musicViewModel = musicViewModel,
                                    onComplete = {
                                        showDeleteConfirmation = false
                                        Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}