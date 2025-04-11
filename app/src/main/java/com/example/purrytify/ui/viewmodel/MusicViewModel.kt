package com.example.purrytify.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.purrytify.ui.screens.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

enum class RepeatMode {
    OFF, ALL, ONE
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

    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null

    private var currentPlaylist: List<Song> = listOf()
    private var currentIndex: Int = 0

    private var songViewModel: SongViewModel? = null
    private var context: Context? = null

    fun initializePlaybackControls(songViewModel: SongViewModel, context: Context) {
        this.songViewModel = songViewModel
        this.context = context
        viewModelScope.launch {
            // Save the full playlist
            currentPlaylist = songViewModel.allSongs.first()

            // If no song is currently playing, start with the first song
            if (currentSong.value == null && currentPlaylist.isNotEmpty()) {
                currentIndex = 0
                playSong(currentPlaylist[currentIndex], context)
            }
        }
    }

    fun playSong(song: Song, context: Context) {
        // Find the index of the song in the playlist
        currentIndex = currentPlaylist.indexOfFirst {
            it.title == song.title && it.artist == song.artist
        }.takeIf { it != -1 } ?: 0

        // Existing playSong logic
        _currentSong.value = song
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(song.uri))
                prepare()
                start()
                _duration.value = duration
                _isPlaying.value = true

                setOnCompletionListener { onSongCompletion() }
            }
            startUpdatingProgress()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying){
                it.pause()
                _isPlaying.value = false
            }
            else{
                it.start()
                _isPlaying.value = true
            }
        }
    }

    private fun startUpdatingProgress() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let {
                    _currentPosition.value = it.currentPosition
                }
                delay(1000)
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    // Play next song
    fun playNext() {
        if (currentPlaylist.isEmpty()) return

        // Determine next index based on repeat and shuffle modes
        currentIndex = when {
            _isShuffleOn.value -> Random.nextInt(currentPlaylist.size)
            _repeatMode.value == RepeatMode.ALL && currentIndex == currentPlaylist.size - 1 -> 0
            else -> (currentIndex + 1) % currentPlaylist.size
        }

        // Play the next song
        playSong(currentPlaylist[currentIndex], context ?: return)
    }

    // Play previous song
    fun playPrevious() {
        if (currentPlaylist.isEmpty()) return

        // Determine previous index
        currentIndex = when {
            _isShuffleOn.value -> Random.nextInt(currentPlaylist.size)
            _repeatMode.value == RepeatMode.ALL && currentIndex == 0 -> currentPlaylist.size - 1
            else -> (currentIndex - 1 + currentPlaylist.size) % currentPlaylist.size
        }

        // Play the previous song
        playSong(currentPlaylist[currentIndex], context ?: return)
    }

    // Toggle repeat mode
    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    // Toggle shuffle
    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value

        // Update playlist based on shuffle state
        songViewModel?.let { viewModel ->
            currentPlaylist = if (_isShuffleOn.value) {
                runBlocking {
                    viewModel.allSongs.first().shuffled()
                }
            } else {
                runBlocking {
                    viewModel.allSongs.first()
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
        }
    }

    // Handle song completion
    private fun onSongCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Replay the current song
                mediaPlayer?.let {
                    it.seekTo(0)
                    it.start()
                }
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                // Play next song
                playNext()
            }
        }
    }

    // Update the current song with new details (after editing)
    fun updateCurrentSong(updatedSong: Song) {
        val wasPlaying = _isPlaying.value
        val currentPosition = _currentPosition.value

        // Update the current song reference
        _currentSong.value = updatedSong

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
                            setOnCompletionListener { onSongCompletion() }
                        }
                        startUpdatingProgress()
                    }
                }
            }
        }
    }
    // Stop playing and clear current song (for deletion)
    fun stopAndClearCurrentSong() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
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
    }
}