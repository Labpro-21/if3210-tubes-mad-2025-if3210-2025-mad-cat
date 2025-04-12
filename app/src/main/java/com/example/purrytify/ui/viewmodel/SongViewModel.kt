package com.example.purrytify.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.local.db.entities.LikedSongCrossRef
import com.example.purrytify.data.local.db.entities.SongEntity
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.screens.Song
import com.tubesmobile.purrytify.data.local.db.AppDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application).songDao()
    private val tokenManager = TokenManager(application)

    // Track current user's email
    private val _currentUserEmail = MutableStateFlow(tokenManager.getEmail() ?: "guest@example.com")
    val currentUserEmail = _currentUserEmail.asStateFlow()

    init {
        Log.d("SongViewModel", "Initial email: ${_currentUserEmail.value}")
    }

    // Fetch all songs for the current user
    val allSongs: Flow<List<Song>> = currentUserEmail.flatMapLatest { email ->
        Log.d("SongViewModel", "Fetching all songs for email: $email")
        songDao.getSongsByUser(email).map { entities ->
            Log.d("SongViewModel", "Fetched all songs: ${entities.size} songs")
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

    // Fetch liked songs for the current user
    val likedSongs: Flow<List<Song>> = currentUserEmail.flatMapLatest { email ->
        Log.d("SongViewModel", "Fetching liked songs for email: $email")
        songDao.getLikedSongsFlow(email).map { entities ->
            Log.d("SongViewModel", "Fetched liked songs: ${entities.size} songs")
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

    fun logDatabaseContents() {
        viewModelScope.launch {
            // Log all songs for the current user
            val email = currentUserEmail.value
            val allSongsFromDb = songDao.getSongsByUser(email).first()  // Collect the result from Flow
            Log.d("SongViewModel", "All songs for $email: ${allSongsFromDb.size}")

            allSongsFromDb.forEach {
                Log.d("SongViewModel", "Song: ${it.title} by ${it.artist}")
            }

            // Log liked songs for the current user
            val likedSongsFromDb = songDao.getLikedSongsFlow(email).first()  // Collect the result from Flow
            Log.d("SongViewModel", "Liked songs for $email: ${likedSongsFromDb.size}")

            likedSongsFromDb.forEach {
                Log.d("SongViewModel", "Liked Song: ${it.title} by ${it.artist}")
            }
        }
    }


    // Keep this function but don't use it in our implementation
    fun extractAndSaveArtwork(context: Context, uri: Uri): String? {
        Log.d("SongViewModel", "Artwork extraction is disabled, returning empty string")
        return ""
    }

    // Insert a song with the current user's email
    fun insertSong(song: Song) {
        viewModelScope.launch {
            val email = currentUserEmail.value
            val entity = SongEntity(
                title = song.title,
                artist = song.artist,
                duration = song.duration,
                uri = song.uri,
                coverUri = song.coverUri
            )

            val newId = songDao.insertSong(entity).toInt()
            songDao.registerUserToSong(email, newId)
        }
    }

    // Check and insert a song (if necessary) for the current user
    fun checkAndInsertSong(
        context: Context,
        song: Song,
        onExists: () -> Unit
    ) {
        viewModelScope.launch {
            val email = currentUserEmail.value
            val existsForUser = songDao.isSongExistsForUser(song.title, song.artist, email)
            val exists = songDao.isSongExists(song.title, song.artist)

            if (existsForUser) {
                onExists()
            } else if (exists) {
                val songId = songDao.getSongId(song.title, song.artist)
                songDao.registerUserToSong(email, songId)
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
                songDao.registerUserToSong(email, newId)
            }
        }
    }

    // Get song ID by title and artist
    suspend fun getSongId(title: String, artist: String): Int {
        return songDao.getSongId(title, artist)
    }

    // Check if a song is liked by the current user
    suspend fun isSongLiked(songId: Int): Boolean {
        val email = currentUserEmail.value
        return songDao.isSongLiked(email, songId)
    }

    // Like a song for the current user
    fun likeSong(songId: Int) {
        viewModelScope.launch {
            val email = currentUserEmail.value
            val crossRef = LikedSongCrossRef(email, songId)
            songDao.likeSong(crossRef)
        }
    }

    // Unlike a song for the current user
    fun unlikeSong(songId: Int) {
        viewModelScope.launch {
            val email = currentUserEmail.value
            val crossRef = LikedSongCrossRef(email, songId)
            songDao.unlikeSong(crossRef)
        }
    }

    // Delete a song for the current user
    fun deleteSong(
        song: Song,
        musicViewModel: MusicViewModel,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val email = currentUserEmail.value
                val songId = songDao.getSongId(song.title, song.artist)

                // Clear the current song in MusicViewModel
                musicViewModel.stopAndClearCurrentSong()

                // Delete song uploader relationship
                songDao.deleteUserSong(email, songId)

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

                    // Delete from recently played
                    PlayHistoryTracker.getRecentlyPlayedSongs(email, tokenManager).find {
                        it.title == song.title && it.artist == song.artist
                    }?.let {
                        val updatedList = PlayHistoryTracker.getRecentlyPlayedSongs(email, tokenManager).toMutableList()
                        updatedList.remove(it)

                        PlayHistoryTracker.clearHistory(email)

                        for (s in updatedList) {
                            PlayHistoryTracker.addSongToHistory(email, s, tokenManager)
                        }
                    }
                }

                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Update a song for the current user
    fun updateSong(
        oldSong: Song,
        newSong: Song,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val email = currentUserEmail.value
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

    fun updateUserEmail(email: String) {
        _currentUserEmail.value = email
        Log.d("SongViewModel", "Updated user email: $email")
    }
}