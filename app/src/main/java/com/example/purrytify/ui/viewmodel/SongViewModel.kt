package com.example.purrytify.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.local.db.entities.LikedSongCrossRef
import com.example.purrytify.data.local.db.entities.SongEntity
import com.example.purrytify.ui.screens.Song
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application).songDao()
    
    // MutableStateFlow to store the current user's email
    private val _currentUserEmail = MutableStateFlow("")
    val currentUserEmail: StateFlow<String> = _currentUserEmail

    // Update user email when it's available
    fun updateUserEmail(email: String) {
        if (email.isNotEmpty()) {
            Log.d("SongViewModel", "Updating user email to: $email")
            _currentUserEmail.value = email
        } else {
            Log.e("SongViewModel", "Attempted to update with empty email")
        }
    }

    // Dynamic Flow that updates when user email changes
    val allSongs: Flow<List<Song>> = currentUserEmail
        .filterNot { it.isEmpty() }
        .flatMapLatest { email ->
            songDao.getSongsByUser(email).map { entities ->
                entities.map { entity ->
                    Song(
                        title = entity.title,
                        artist = entity.artist,
                        duration = entity.duration,
                        uri = entity.uri,
                        coverUri = entity.coverUri ?: ""
                    )
                }
            }
        }

    // Dynamic Flow for liked songs that updates when user email changes
    val likedSongs: Flow<List<Song>> = currentUserEmail
        .filterNot { it.isEmpty() }
        .flatMapLatest { email ->
            songDao.getLikedSongsFlow(email).map { entities ->
                entities.map { entity ->
                    Song(
                        title = entity.title,
                        artist = entity.artist,
                        duration = entity.duration,
                        uri = entity.uri,
                        coverUri = entity.coverUri ?: ""
                    )
                }
            }
        }

    // Keep this function but don't use it in our implementation
    fun extractAndSaveArtwork(context: Context, uri: Uri): String? {
        Log.d("SongViewModel", "Artwork extraction is disabled, returning empty string")
        return ""
    }

    // Validate user email before performing operations
    private fun validateUserEmail(email: String): Boolean {
        if (email.isEmpty()) {
            Log.e("SongViewModel", "User email is empty. Cannot perform operation.")
            return false
        }
        return true
    }

    fun insertSong(song: Song, userEmail: String) {
        if (!validateUserEmail(userEmail)) return
        
        viewModelScope.launch {
            val entity = SongEntity(
                title = song.title,
                artist = song.artist,
                duration = song.duration,
                uri = song.uri,
                coverUri = song.coverUri
            )

            val newId = songDao.insertSong(entity).toInt()
            songDao.registerUserToSong(userEmail, newId)
        }
    }

    fun checkAndInsertSong(
        context: Context,
        song: Song,
        userEmail: String,
        onExists: () -> Unit
    ) {
        if (!validateUserEmail(userEmail)) return
        
        viewModelScope.launch {
            val existsForUser = songDao.isSongExistsForUser(song.title, song.artist, userEmail)
            val exists = songDao.isSongExists(song.title, song.artist)

            if (existsForUser) {
                onExists()
            } else if (exists) {
                val songId = songDao.getSongId(song.title, song.artist)
                songDao.registerUserToSong(userEmail, songId)
            } else {
                Log.d("SongViewModel", "Using provided coverUri: ${song.coverUri}")

                val entity = SongEntity(
                    title = song.title,
                    artist = song.artist,
                    duration = song.duration,
                    uri = song.uri,
                    coverUri = song.coverUri
                )
                val newId = songDao.insertSong(entity).toInt()
                songDao.registerUserToSong(userEmail, newId)
            }
        }
    }

    // Get song ID by title and artist
    suspend fun getSongId(title: String, artist: String): Int {
        return songDao.getSongId(title, artist)
    }

    // Check if a song is liked
    suspend fun isSongLiked(userEmail: String, songId: Int): Boolean {
        if (!validateUserEmail(userEmail)) return false
        return songDao.isSongLiked(userEmail, songId)
    }

    // Like a song
    fun likeSong(userEmail: String, songId: Int) {
        if (!validateUserEmail(userEmail)) return
        
        viewModelScope.launch {
            val crossRef = LikedSongCrossRef(userEmail, songId)
            songDao.likeSong(crossRef)
        }
    }

    // Unlike a song
    fun unlikeSong(userEmail: String, songId: Int) {
        if (!validateUserEmail(userEmail)) return
        
        viewModelScope.launch {
            val crossRef = LikedSongCrossRef(userEmail, songId)
            songDao.unlikeSong(crossRef)
        }
    }

    // Delete a song
    fun deleteSong(
        song: Song,
        musicViewModel: MusicViewModel,
        userEmail: String,
        onComplete: () -> Unit = {}
    ) {
        if (!validateUserEmail(userEmail)) return
        
        viewModelScope.launch {
            try {
                // Get the song ID first
                val songId = songDao.getSongId(song.title, song.artist)

                // Clear the current song in MusicViewModel
                musicViewModel.stopAndClearCurrentSong()

                // Delete song uploader relationship
                songDao.deleteUserSong(userEmail, songId)

                // Check if the song is still used by other users
                val isUsedByOthers = songDao.isSongUsedByOthers(songId)

                if (!isUsedByOthers) {
                    // Delete physical files
                    if (song.coverUri.isNotEmpty()) {
                        val coverFile = File(song.coverUri)
                        if (coverFile.exists()) {
                            coverFile.delete()
                        }
                    }

                    val audioFile = File(song.uri)
                    if (audioFile.exists()) {
                        audioFile.delete()
                    }

                    // Delete from database
                    songDao.deleteSong(songId)
                }

                onComplete()
            } catch (e: Exception) {
                Log.e("SongViewModel", "Error deleting song", e)
                e.printStackTrace()
            }
        }
    }

    // Update a song
    fun updateSong(
        oldSong: Song,
        newSong: Song,
        userEmail: String,
        onComplete: () -> Unit = {}
    ) {
        if (!validateUserEmail(userEmail)) return
        
        viewModelScope.launch {
            try {
                val songId = songDao.getSongId(oldSong.title, oldSong.artist)

                // Update the song entity
                val entity = SongEntity(
                    id = songId,
                    title = newSong.title,
                    artist = newSong.artist,
                    duration = newSong.duration,
                    uri = newSong.uri,
                    coverUri = newSong.coverUri
                )

                songDao.updateSong(entity)
                onComplete()
            } catch (e: Exception) {
                Log.e("SongViewModel", "Error updating song", e)
                e.printStackTrace()
            }
        }
    }
}