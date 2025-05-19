package com.example.purrytify.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.RepeatMode
import com.example.purrytify.ui.viewmodel.SongViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor
import com.example.purrytify.ui.dialogs.ShareSongDialog
import com.example.purrytify.data.model.OnlineSong
import com.example.purrytify.ui.components.AudioDeviceBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        musicViewModel.initializePlaybackControls(songViewModel, context)
    }

    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val currentPosition by musicViewModel.currentPosition.collectAsState()
    val duration by musicViewModel.duration.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()
    val isShuffleOn by musicViewModel.isShuffleOn.collectAsState()
    val currentOnlineSongId by musicViewModel.currentOnlineSongId.collectAsState()
    val currentAudioDevice by musicViewModel.currentAudioDevice.collectAsState()
    val showAudioDeviceSelector by musicViewModel.showAudioDeviceSelector.collectAsState()
    var isSongLiked by remember { mutableStateOf(false) }
    val currentSongId = remember { mutableStateOf(-1) }

    LaunchedEffect(currentSong) {
        currentSong?.let { song ->
            // For online songs, try to extract ID from URL if pattern matches
            val songId = if (song.uri.startsWith("http")) {
                // Extract id from the artwork URL pattern if possible
                // Pattern: https://storage.googleapis.com/mad-public-bucket/cover/{title}.png
                -1 // For now, just use -1 for online songs
            } else {
                songViewModel.getSongId(song.title, song.artist)
            }
            currentSongId.value = songId

            // Don't check liked status for online songs
            isSongLiked = if (songId == -1) false else songViewModel.isSongLiked(songId)
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var newSongTitle by remember { mutableStateOf("") }
    var newSongArtist by remember { mutableStateOf("") }
    var newSongImageUri by remember { mutableStateOf<Uri?>(null) }
    var newSongAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioDuration by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val songTitleWidth = remember { mutableStateOf(0) }
    val containerWidth = remember { mutableStateOf(0) }

    LaunchedEffect(currentSong, songTitleWidth.value, containerWidth.value) {
        if (songTitleWidth.value > containerWidth.value && containerWidth.value > 0) {
            delay(1500)
            while (true) {
                scrollState.animateScrollTo(
                    songTitleWidth.value - containerWidth.value,
                    animationSpec = tween(
                        durationMillis = ((songTitleWidth.value - containerWidth.value) * 15),
                        easing = LinearEasing
                    )
                )

                scrollState.animateScrollTo(
                    0,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                )
                delay(1000)
            }
        }
    }

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
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF7D002B),
                            Color(0xFF3D0014),
                            Color(0xFF1A0008)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No song is currently playing",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF7D002B),
                        Color(0xFF3D0014),
                        Color(0xFF1A0008)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "9:41",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SignalCellular4Bar,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { showMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                val imageModel = when {
                    currentSong!!.coverUri.startsWith("http") -> currentSong!!.coverUri // Online song URL
                    currentSong!!.coverUri.isNotEmpty() -> File(currentSong!!.coverUri) // Local file
                    else -> "https://example.com/placeholder.jpg" // Placeholder
                }
                
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Album Cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                containerWidth.value = coordinates.size.width
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                        ) {
                            Text(
                                text = currentSong!!.title,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    songTitleWidth.value = coordinates.size.width
                                }
                            )
                            if (songTitleWidth.value > containerWidth.value) {
                                Spacer(modifier = Modifier.width(32.dp))
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                // Don't allow liking online songs (they're not in the local database)
                                if (currentSongId.value == -1) {
                                    Toast.makeText(context, "Cannot like online songs", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                
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
                }

                Text(
                    text = currentSong!!.artist,
                    color = Color(0xFFCCCCCC),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Log values for debugging
                LaunchedEffect(currentPosition, duration) {
                    if (duration > 0) {
                        Log.d("MusicPlayerScreen", "Position: $currentPosition, Duration: $duration, Progress: ${currentPosition.toFloat() / duration}")
                    }
                }
                
                // Add some debugging for when the song changes
                LaunchedEffect(currentSong) {
                    Log.d("MusicPlayerScreen", "Song changed: ${currentSong?.title}, Duration: $duration")
                }

                // Ensure duration is at least 1 to avoid division by zero
                val safeDuration = duration.coerceAtLeast(1)
                
                Slider(
                    value = currentPosition.toFloat().coerceIn(0f, safeDuration.toFloat()),
                    onValueChange = { musicViewModel.seekTo(it.toInt()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0x40FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatDuration(safeDuration),
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 200.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { musicViewModel.toggleShuffle() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleOn) Color(0xFF1DB954) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { musicViewModel.playPrevious() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { musicViewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { musicViewModel.playNext() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { musicViewModel.toggleRepeatMode() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.OFF -> Icons.Rounded.Repeat
                            RepeatMode.ALL -> Icons.Rounded.Repeat
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        },
                        contentDescription = "Repeat",
                        tint = when (repeatMode) {
                            RepeatMode.OFF -> Color.White
                            RepeatMode.ALL -> Color(0xFF1DB954)
                            RepeatMode.ONE -> Color(0xFF1DB954)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Audio device selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { musicViewModel.showAudioDeviceSelector() }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Headphones,
                        contentDescription = "Select Audio Output",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = currentAudioDevice,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
    if (showMenu) {
        val isOnlineSong = currentSong?.uri?.startsWith("http") == true
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showMenu = false }
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp)
                    .width(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF222222)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Share Song option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showShareDialog = true
                                showMenu = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Share Song",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                    
                    Divider(
                        color = Color(0xFF333333),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    // Edit Song option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isOnlineSong) {
                                if (!isOnlineSong) {
                                    currentSong?.let { handleEditSong(it) }
                                    showMenu = false
                                } else {
                                    Toast.makeText(context, "Cannot edit online songs", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = if (isOnlineSong) Color(0xFF555555) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Edit Song",
                            color = if (isOnlineSong) Color(0xFF555555) else Color.White,
                            fontSize = 16.sp
                        )
                    }

                    Divider(
                        color = Color(0xFF333333),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isOnlineSong) {
                                if (!isOnlineSong) {
                                    currentSong?.let { handleDeleteSong(it) }
                                    showMenu = false
                                } else {
                                    Toast.makeText(context, "Cannot delete online songs", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = if (isOnlineSong) Color(0xFF555555) else Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Delete Song",
                            color = if (isOnlineSong) Color(0xFF555555) else Color(0xFFFF5252),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog && currentSong != null) {
        ModalBottomSheet(
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
                    .padding(horizontal = 24.dp, vertical = 16.dp)
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
                                .clickable { audioPickerLauncher.launch("audio/*") }
                                .border(
                                    width = 1.dp,
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(0xFF444444),
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
                                    tint = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(0xFF888888),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (newSongAudioUri != null) "New File" else "Keep Current",
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(0xFF888888),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (newSongAudioUri != null) audioDuration else currentSong!!.duration,
                                    color = if (newSongAudioUri != null) Color(0xFF1DB954) else Color(0xFF888888),
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
                                    val oldSong = currentSong!!

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
                                            newSongTitle = ""
                                            newSongArtist = ""
                                            newSongImageUri = null
                                            newSongAudioUri = null
                                            audioDuration = ""
                                            errorMessage = null
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

    if (showDeleteConfirmation && currentSong != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showDeleteConfirmation = false
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
                    text = "Are you sure you want to delete \"${currentSong!!.title}\" by ${currentSong!!.artist}?",
                    color = Color(0xFFCCCCCC),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFCCCCCC))
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val songToDelete = currentSong!!
                                musicViewModel.stopAndClearCurrentSong()
                                songViewModel.deleteSong(
                                    song = songToDelete,
                                    musicViewModel = musicViewModel,
                                    onComplete = {
                                        showDeleteConfirmation = false
                                        Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                                        onBackClick()
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
    
    // Show share dialog
    if (showShareDialog && currentSong != null) {
        val isOnlineSong = currentSong!!.uri.startsWith("http")
        ShareSongDialog(
            songId = if (isOnlineSong) currentOnlineSongId else currentSongId.value,
            songTitle = currentSong!!.title,
            songArtist = currentSong!!.artist,
            songUrl = null, // We always use deep links, not direct URLs
            onDismiss = { showShareDialog = false }
        )
    }

    // Show audio device selector
    if (showAudioDeviceSelector) {
        AudioDeviceBottomSheet(
            onDismiss = { musicViewModel.dismissAudioDeviceSelector() }
        )
    }
}

// Convert duration string (mm:ss) to milliseconds
fun parseDurationToMillis(duration: String): Long {
    return try {
        val parts = duration.split(":")
        if (parts.size == 2) {
            val minutes = parts[0].toLongOrNull() ?: 0
            val seconds = parts[1].toLongOrNull() ?: 0
            (minutes * 60 + seconds) * 1000
        } else {
            0L
        }
    } catch (e: Exception) {
        0L
    }
}

private fun formatDuration(milliseconds: Int): String {
    val totalSeconds = floor(milliseconds / 1000f).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}