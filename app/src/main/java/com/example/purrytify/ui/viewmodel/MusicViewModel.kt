package com.example.purrytify.ui.viewmodel

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.purrytify.service.MediaPlaybackService
import com.example.purrytify.ui.screens.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

enum class RepeatMode {
    OFF,    // No repeat - play through playlist once
    ALL,    // Repeat entire playlist
    ONE     // Repeat current song only
}

enum class PlaylistContext {
    LIBRARY,    // Playing from local library
    ONLINE      // Playing from online charts (global/country)
}

class MusicViewModel : ViewModel() {
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode

    private val _isShuffleOn = MutableStateFlow(false)
    val isShuffleOn: StateFlow<Boolean> = _isShuffleOn
    
    // Playlist context - to track where the song is being played from
    private val _playlistContext = MutableStateFlow(PlaylistContext.LIBRARY)
    val playlistContext: StateFlow<PlaylistContext> = _playlistContext
    
    // Online playlist storage
    private var onlinePlaylist: List<Song> = listOf()
    private var onlinePlaylistType: String = "" // "global" or country code

    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private var serviceConnected = false

    private var currentPlaylist: List<Song> = listOf()
    private var currentIndex: Int = 0

    private var songViewModel: SongViewModel? = null
    private var context: Context? = null
    
    // Media service
    private var mediaService: MediaPlaybackService? = null
    private var isServiceBound = false
    
    // BroadcastReceiver for playback state changes
    private val playbackStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.purrytify.PLAYBACK_STATE_CHANGED") {
                val isPlaying = intent.getBooleanExtra("isPlaying", false)
                _isPlaying.value = isPlaying
                Log.d("MusicViewModel", "Received playback state broadcast: playing=$isPlaying")
            }
        }
    }

    // BroadcastReceiver for media actions
    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.purrytify.MEDIA_ACTION") {
                val action = intent.getStringExtra("action")
                Log.d("MusicViewModel", "Received media action broadcast: $action")
                when (action) {
                    "ACTION_NEXT" -> {
                        Log.d("MusicViewModel", "Processing ACTION_NEXT")
                        playNext()
                    }
                    "ACTION_PREVIOUS" -> {
                        Log.d("MusicViewModel", "Processing ACTION_PREVIOUS")
                        playPrevious()
                    }
                    "ACTION_TOGGLE_SHUFFLE" -> {
                        Log.d("MusicViewModel", "Processing ACTION_TOGGLE_SHUFFLE")
                        toggleShuffle()
                    }
                    "ACTION_TOGGLE_REPEAT" -> {
                        Log.d("MusicViewModel", "Processing ACTION_TOGGLE_REPEAT")
                        toggleRepeatMode()
                    }
                    "ACTION_SONG_COMPLETED" -> {
                        Log.d("MusicViewModel", "Processing ACTION_SONG_COMPLETED")
                        onSongCompletion()
                    }
                }
            }
        }
    }

    // Register receivers
    private fun registerPlaybackStateReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context?.registerReceiver(
                playbackStateReceiver,
                IntentFilter("com.example.purrytify.PLAYBACK_STATE_CHANGED"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context?.registerReceiver(
                playbackStateReceiver,
                IntentFilter("com.example.purrytify.PLAYBACK_STATE_CHANGED")
            )
        }
    }

    private fun registerMediaActionReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context?.registerReceiver(
                mediaActionReceiver,
                IntentFilter("com.example.purrytify.MEDIA_ACTION"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context?.registerReceiver(
                mediaActionReceiver,
                IntentFilter("com.example.purrytify.MEDIA_ACTION")
            )
        }
    }
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MusicViewModel", "Service connected")
            try {
                val binder = service as MediaPlaybackService.MediaServiceBinder
                mediaService = binder.getService()
                isServiceBound = true
                
                // If a song is currently playing, update the service
                currentSong.value?.let { song ->
                    if (_isPlaying.value) {
                        Log.d("MusicViewModel", "Updating service with current song: ${song.title}")
                        mediaService?.playSong(song)
                    }
                }
                
                // Register broadcast receivers
                registerPlaybackStateReceiver()
                registerMediaActionReceiver()
                
                // Start updating progress if we have a current song
                if (currentSong.value != null) {
                    startUpdatingProgress()
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error in onServiceConnected", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MusicViewModel", "Service disconnected")
            mediaService = null
            isServiceBound = false
            
            // Unregister receivers when service disconnects
            context?.let { ctx ->
                try {
                    ctx.unregisterReceiver(playbackStateReceiver)
                    ctx.unregisterReceiver(mediaActionReceiver)
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error unregistering receivers", e)
                }
            }
        }
    }

    fun initializePlaybackControls(songViewModel: SongViewModel, context: Context) {
        Log.d("MusicViewModel", "Initializing playback controls")
        this.songViewModel = songViewModel
        this.context = context
        
        // Start and bind to the media service
        val intent = Intent(context, MediaPlaybackService::class.java)
        intent.action = "START_FOREGROUND" // Add specific action for starting foreground
        
        // Start service in a try/catch and delay binding to ensure service is ready
        try {
            context.startService(intent)
            Log.d("MusicViewModel", "Service started successfully")
            
            // Delay binding slightly to give the service time to start
            viewModelScope.launch {
                delay(500) // Short delay for service to initialize
                try {
                    serviceConnected = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                    Log.d("MusicViewModel", "Service binding initiated, connected: $serviceConnected")
                    
                    // Save the full playlist
                    try {
                        currentPlaylist = songViewModel.allSongs.first()
                        Log.d("MusicViewModel", "Playlist loaded with ${currentPlaylist.size} songs")
                        if (currentPlaylist.isEmpty()) {
                            Log.w("MusicViewModel", "Warning: Playlist is empty!")
                        }
                        
                        // Don't auto-play on app start
                        // Just load the playlist, song will be played when user selects it
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error loading playlist", e)
                        currentPlaylist = emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error binding to service", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error starting service", e)
        }
    }

    // Set online playlist for charts playback
    fun setOnlinePlaylist(songs: List<Song>, type: String) {
        onlinePlaylist = songs
        onlinePlaylistType = type
        Log.d("MusicViewModel", "Set online playlist with ${songs.size} songs, type: $type")
    }
    
    fun playSong(song: Song, context: Context, fromOnlinePlaylist: Boolean = false, onlineType: String = "") {
        Log.d("MusicViewModel", "Playing song: ${song.title} by ${song.artist}")
        Log.d("MusicViewModel", "From online: $fromOnlinePlaylist, type: $onlineType")
        Log.d("MusicViewModel", "Current repeat mode: ${_repeatMode.value}, shuffle: ${_isShuffleOn.value}")
        
        // Set playlist context
        if (fromOnlinePlaylist) {
            _playlistContext.value = PlaylistContext.ONLINE
            onlinePlaylistType = onlineType
            // Use the current online playlist
            currentPlaylist = onlinePlaylist
            Log.d("MusicViewModel", "Using online playlist with ${currentPlaylist.size} songs")
        } else {
            _playlistContext.value = PlaylistContext.LIBRARY
            // Use the local library playlist
            songViewModel?.let { viewModel ->
                currentPlaylist = runBlocking {
                    viewModel.allSongs.first()
                }
                Log.d("MusicViewModel", "Using library playlist with ${currentPlaylist.size} songs")
            }
        }
        
        // Find the index of the song in the playlist
        currentIndex = currentPlaylist.indexOfFirst {
            it.title == song.title && it.artist == song.artist
        }.takeIf { it != -1 } ?: 0

        // Update the current song and reset position
        _currentSong.value = song
        _currentPosition.value = 0
                
        // Update service states
        if (isServiceBound && mediaService != null) {
            mediaService?.updateShuffleState(_isShuffleOn.value)
            val repeatModeInt = when (_repeatMode.value) {
                RepeatMode.OFF -> 0
                RepeatMode.ALL -> 1
                RepeatMode.ONE -> 2
            }
            mediaService?.updateRepeatMode(repeatModeInt)
        }
        
        try {
            // Use the service if bound
            if (isServiceBound && mediaService != null) {
                Log.d("MusicViewModel", "Using media service to play song")
                mediaService?.playSong(song)
                _isPlaying.value = true
                
                // Get duration from MediaPlayer in service
                viewModelScope.launch {
                    delay(100) // Small delay to let media player prepare
                    mediaService?.getMediaPlayer()?.let { player ->
                        _duration.value = player.duration
                        Log.d("MusicViewModel", "Duration from service: ${_duration.value}")
                    }
                }
            } else {
                // Fallback to local media player if service not bound
                Log.d("MusicViewModel", "Fallback: Using local MediaPlayer (service not bound)")
                
                // Try to start and connect to the service again
                if (this.context != null) {
                    val intent = Intent(this.context, MediaPlaybackService::class.java)
                    intent.action = "START_FOREGROUND"
                    this.context?.startService(intent)
                    serviceConnected = this.context?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE) ?: false
                    Log.d("MusicViewModel", "Re-attempting service connection: $serviceConnected")
                }
                
                // Fallback to using MediaPlayer directly
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(song.uri))
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        _duration.value = duration
                        Log.d("MusicViewModel", "Duration from local player: ${_duration.value}")
                        _isPlaying.value = true

                        setOnCompletionListener { 
                            Log.d("MusicViewModel", "Local MediaPlayer song completed")
                            _isPlaying.value = false // Set playing to false when completed
                            onSongCompletion() 
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e("MusicViewModel", "MediaPlayer error: what=$what, extra=$extra")
                            false
                        }
                        startUpdatingProgress()
                    }
                }
            }
            startUpdatingProgress()
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error playing song", e)
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        if (isServiceBound && mediaService != null) {
            mediaService?.togglePlayPause()
            // Update state immediately
            _isPlaying.value = !_isPlaying.value
        } else {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                } else {
                    it.start()
                    _isPlaying.value = true
                }
            }
        }
    }

    private fun startUpdatingProgress() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            while (true) {
                try {
                    if (isServiceBound && mediaService != null) {
                        // Get position from service when available
                        mediaService?.let { service ->
                            val player = service.getMediaPlayer()
                            if (player != null && !player.isReleased()) {
                                val newPosition = player.currentPosition
                                val newIsPlaying = player.isPlaying
                                val newDuration = player.duration
                                
                                // Only update if values have changed
                                if (_currentPosition.value != newPosition) {
                                    _currentPosition.value = newPosition
                                }
                                if (_isPlaying.value != newIsPlaying) {
                                    _isPlaying.value = newIsPlaying
                                }
                                if (_duration.value != newDuration && newDuration > 0) {
                                    _duration.value = newDuration
                                }
                            }
                        }
                    } else {
                        mediaPlayer?.let { player ->
                            if (!player.isReleased()) {
                                val newPosition = player.currentPosition
                                val newIsPlaying = player.isPlaying
                                val newDuration = player.duration
                                
                                // Only update if values have changed
                                if (_currentPosition.value != newPosition) {
                                    _currentPosition.value = newPosition
                                }
                                if (_isPlaying.value != newIsPlaying) {
                                    _isPlaying.value = newIsPlaying
                                }
                                if (_duration.value != newDuration && newDuration > 0) {
                                    _duration.value = newDuration
                                }
                            }
                        }
                    }
                } catch (e: IllegalStateException) {
                    Log.e("MusicViewModel", "MediaPlayer in invalid state", e)
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error updating progress", e)
                }
                delay(100) // Update more frequently for smoother progress bar
            }
        }
    }
    
    // Extension function to check if MediaPlayer is released
    private fun MediaPlayer.isReleased(): Boolean {
        return try {
            // Try to access a property that would throw if released
            this.currentPosition
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    fun seekTo(position: Int) {
        if (isServiceBound && mediaService != null) {
            mediaService?.seekTo(position)
        } else {
            mediaPlayer?.seekTo(position)
        }
        _currentPosition.value = position
    }

    // Play next song
    fun playNext() {
        try {
            if (currentPlaylist.isEmpty()) {
                Log.e("MusicViewModel", "Playlist is empty")
                return
            }
            
            if (context == null) {
                Log.e("MusicViewModel", "Context is null")
                return
            }
            
            val previousIndex = currentIndex
            
            // Handle repeat one mode - should stay on current song
            if (_repeatMode.value == RepeatMode.ONE) {
                // In repeat one mode, "next" plays the same song again from start
                Log.d("MusicViewModel", "Repeat ONE mode - replaying current song")
                currentSong.value?.let { song ->
                    context?.let { ctx ->
                        playSong(song, ctx, 
                            fromOnlinePlaylist = (_playlistContext.value == PlaylistContext.ONLINE),
                            onlineType = onlinePlaylistType
                        )
                    }
                }
                return
            }
            
            // Determine next index based on shuffle mode
            currentIndex = when {
                _isShuffleOn.value -> {
                    // For shuffle, pick a random song different from current
                    if (currentPlaylist.size > 1) {
                        var newIndex: Int
                        do {
                            newIndex = Random.nextInt(currentPlaylist.size)
                        } while (newIndex == previousIndex && currentPlaylist.size > 1)
                        newIndex
                    } else {
                        0
                    }
                }
                else -> {
                    // Normal progression through playlist
                    if (currentIndex < currentPlaylist.size - 1) {
                        currentIndex + 1
                    } else {
                        // Loop back to start always (for any mode)
                        0
                    }
                }
            }
            
            // Ensure index is valid
            if (currentIndex >= currentPlaylist.size) {
                Log.e("MusicViewModel", "Invalid index: $currentIndex, playlist size: ${currentPlaylist.size}")
                currentIndex = 0
            }
            
            // Play the next song
            context?.let {
                playSong(currentPlaylist[currentIndex], it,
                    fromOnlinePlaylist = (_playlistContext.value == PlaylistContext.ONLINE),
                    onlineType = onlinePlaylistType
                )
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error in playNext", e)
        }
    }

    // Play previous song
    fun playPrevious() {
        if (currentPlaylist.isEmpty()) return
        
        // If repeat one is active and user presses previous, disable repeat one
        if (_repeatMode.value == RepeatMode.ONE) {
            Log.d("MusicViewModel", "Previous pressed in repeat one mode - switching to repeat all")
            _repeatMode.value = RepeatMode.ALL
            // Update service with new repeat mode
            if (isServiceBound && mediaService != null) {
                mediaService?.updateRepeatMode(1) // 1 = ALL
            }
        }
        
        // Check current position to determine behavior
        val currentPos = _currentPosition.value
        Log.d("MusicViewModel", "Current position: $currentPos ms")
        
        // Standard behavior: if less than 3 seconds in, go to previous; otherwise restart current
        if (currentPos < 3000) {
            // Go to previous song
            Log.d("MusicViewModel", "Position < 3s, going to previous song")
            
            // Determine previous index
            currentIndex = when {
                _isShuffleOn.value -> {
                    // For shuffle, pick a random song
                    Random.nextInt(currentPlaylist.size)
                }
                else -> {
                    // Normal progression backwards through playlist
                    if (currentIndex > 0) {
                        currentIndex - 1
                    } else {
                        // Loop to end regardless of repeat mode
                        currentPlaylist.size - 1
                    }
                }
            }
            
            // Play the previous song
            context?.let {
                playSong(currentPlaylist[currentIndex], it,
                    fromOnlinePlaylist = (_playlistContext.value == PlaylistContext.ONLINE),
                    onlineType = onlinePlaylistType
                )
            }
        } else {
            // Restart current song
            Log.d("MusicViewModel", "Position >= 3s, restarting current song")
            currentSong.value?.let { song ->
                context?.let { ctx ->
                    playSong(song, ctx,
                        fromOnlinePlaylist = (_playlistContext.value == PlaylistContext.ONLINE),
                        onlineType = onlinePlaylistType
                    )
                }
            }
        }
    }

    // Toggle repeat mode
    fun toggleRepeatMode() {
        val oldMode = _repeatMode.value
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        
        Log.d("MusicViewModel", "Toggled repeat mode from $oldMode to ${_repeatMode.value}")
        
        // Update service with new repeat mode
        if (isServiceBound && mediaService != null) {
            val repeatModeInt = when (_repeatMode.value) {
                RepeatMode.OFF -> 0
                RepeatMode.ALL -> 1
                RepeatMode.ONE -> 2
            }
            Log.d("MusicViewModel", "Updating service with repeat mode: $repeatModeInt")
            mediaService?.updateRepeatMode(repeatModeInt)
        }
    }

    // Toggle shuffle
    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value

        // Update playlist based on shuffle state and context
        currentPlaylist = when (_playlistContext.value) {
            PlaylistContext.ONLINE -> {
                if (_isShuffleOn.value) {
                    onlinePlaylist.shuffled()
                } else {
                    onlinePlaylist
                }
            }
            PlaylistContext.LIBRARY -> {
                songViewModel?.let { viewModel ->
                    runBlocking {
                        val songs = viewModel.allSongs.first()
                        if (_isShuffleOn.value) songs.shuffled() else songs
                    }
                } ?: emptyList()
            }
        }
        
        // Ensure current song remains the same if possible
        currentSong.value?.let { current ->
            val index = currentPlaylist.indexOfFirst {
                it.title == current.title && it.artist == current.artist
            }
            if (index != -1) {
                currentIndex = index
            }
        }
        
        // Update service with new shuffle state
        if (isServiceBound && mediaService != null) {
            mediaService?.updateShuffleState(_isShuffleOn.value)
        }
    }

    // Handle song completion
    private fun onSongCompletion() {
        try {
            Log.d("MusicViewModel", "Song completion. RepeatMode: ${_repeatMode.value}, Shuffle: ${_isShuffleOn.value}")
            Log.d("MusicViewModel", "Current playlist size: ${currentPlaylist.size}, currentIndex: $currentIndex")
            
            // Check if we have a valid context and song
            if (context == null || currentSong.value == null) {
                Log.e("MusicViewModel", "Context or currentSong is null")
                return
            }
            
            when (_repeatMode.value) {
                RepeatMode.ONE -> {
                    // Repeat the current song - proper implementation
                    Log.d("MusicViewModel", "Repeat ONE - replaying current song")
                    currentSong.value?.let { song ->
                        context?.let { ctx ->
                            // For Repeat One, we need to properly restart the song
                            Log.d("MusicViewModel", "Restarting song: ${song.title}")
                            // Call playSong to restart from beginning
                            playSong(song, ctx,
                                fromOnlinePlaylist = (_playlistContext.value == PlaylistContext.ONLINE),
                                onlineType = onlinePlaylistType
                            )
                        }
                    }
                }
                RepeatMode.ALL -> {
                    // Play next song and loop back to start if at end
                    Log.d("MusicViewModel", "Repeat ALL - playing next song")
                    playNext()
                }
                RepeatMode.OFF -> {
                    // Always play next song (will loop naturally)
                    Log.d("MusicViewModel", "Playing next song (will loop if at end)")
                    playNext()
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error in onSongCompletion", e)
        }
    }

    // Update the current song with new details (after editing)
    fun updateCurrentSong(updatedSong: Song) {
        val wasPlaying = _isPlaying.value
        val currentPosition = _currentPosition.value

        // Update the current song reference
        _currentSong.value = updatedSong

        // Don't update online playlists - they're managed by the server
        if (_playlistContext.value == PlaylistContext.LIBRARY) {
            // Update the playlist
            viewModelScope.launch {
                songViewModel?.let { viewModel ->
                    // Get fresh playlist
                    currentPlaylist = viewModel.allSongs.first()

                    // Find updated song in playlist
                    val updatedIndex = currentPlaylist.indexOfFirst {
                        it.uri == updatedSong.uri
                    }

                    if (updatedIndex != -1) {
                        currentIndex = updatedIndex
                    }

                    // If the song was playing, restart it with the updated details
                    if (wasPlaying) {
                        context?.let { ctx ->
                            if (isServiceBound && mediaService != null) {
                                mediaService?.playSong(updatedSong)
                                mediaService?.seekTo(currentPosition)
                            } else {
                                mediaPlayer?.release()
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(ctx, Uri.parse(updatedSong.uri))
                                    prepare()
                                    // Restore position if possible
                                    if (currentPosition > 0 && currentPosition < duration) {
                                        seekTo(currentPosition)
                                    }
                                    if (wasPlaying) {
                                        start()
                                        _isPlaying.value = true
                                    }
                                    _duration.value = duration
                                    setOnCompletionListener { 
                                        Log.d("MusicViewModel", "MediaPlayer song completed during restoration")
                                        onSongCompletion() 
                                    }
                                }
                                startUpdatingProgress()
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Stop playing and clear current song (for deletion)
    fun stopAndClearCurrentSong() {
        if (isServiceBound && mediaService != null) {
            // Stop the service
            context?.let { ctx ->
                val intent = Intent(ctx, MediaPlaybackService::class.java)
                ctx.stopService(intent)
            }
        } else {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        }
        
        updateJob?.cancel()

        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
        _currentSong.value = null
        currentPlaylist = emptyList()
        currentIndex = 0
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        updateJob?.cancel()
        
        // Unregister receivers
        context?.let { ctx ->
            try {
                ctx.unregisterReceiver(playbackStateReceiver)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error unregistering playback receiver", e)
            }
            
            try {
                ctx.unregisterReceiver(mediaActionReceiver)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error unregistering media receiver", e)
            }
        }
        
        // Unbind from service
        context?.let {
            if (isServiceBound) {
                it.unbindService(serviceConnection)
                isServiceBound = false
            }
        }
    }
}