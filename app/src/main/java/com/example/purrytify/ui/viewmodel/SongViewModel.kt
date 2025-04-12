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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application).songDao()
    
    // Replace hardcoded email with a state flow to hold current user email
    private val _currentUserEmail = MutableStateFlow("") // Empty default, will be set when user logs in
    val currentUserEmail: Flow<String> = _currentUserEmail
    
    // Update the user email when we get it from the profile
    fun updateUserEmail(email: String) {
        if (email.isNotEmpty() && email != _currentUserEmail.value) {
            _currentUserEmail.value = email
            Log.d("SongViewModel", "User email updated to: $email")
        }
    }

    // Use flatMapLatest to update data when user email changes
    val allSongs: Flow<List<Song>> = _currentUserEmail.flatMapLatest { email ->
        if (email.isEmpty()) {
            Log.w("SongViewModel", "Trying to get songs with empty email")
        }
        
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

    // Flow for liked songs - also updates when user email changes
    val likedSongs: Flow<List<Song>> = _currentUserEmail.flatMapLatest { email ->
        if (email.isEmpty()) {
            Log.w("SongViewModel", "Trying to get liked songs with empty email")
        }
        
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

    fun insertSong(song: Song, userEmail: String){
        if (userEmail.isEmpty()) {
            Log.e("SongViewModel", "Cannot insert song: user email is empty")
            return
        }
        
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
        if (userEmail.isEmpty()) {
            Log.e("SongViewModel", "Cannot check/insert song: user email is empty")
            return
        }
        
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
        if (userEmail.isEmpty()) {
            Log.e("SongViewModel", "Cannot check liked status: user email is empty")
            return false
        }
        return songDao.isSongLiked(userEmail, songId)
    }

    // Like a song
    fun likeSong(userEmail: String, songId: Int) {
        if (userEmail.isEmpty()) {
            Log.e("SongViewModel", "Cannot like song: user email is empty")
            return
        }
        viewModelScope.launch {
            val crossRef = LikedSongCrossRef(userEmail, songId)
            songDao.likeSong(crossRef)
        }
    }

    // Unlike a song
    fun unlikeSong(userEmail: String, songId: Int) {
        if (userEmail.isEmpty()) {
            Log.e("SongViewModel", "Cannot unlike song: user email is empty")
            return
        }
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
        if (userEmail.isEmpty()) {
            Log.e("SongViewModel", "Cannot delete song: user email is empty")
            return
        }
        
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

                // If not used by others, delete the actual song and its files
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
        if (userEmail.isEmpty()) {
            Log.e("SongViewModel", "Cannot update song: user email is empty")
            return
        }
        
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
                e.printStackTrace()
            }
        }
    }
}