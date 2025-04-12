package com.example.purrytify.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.purrytify.ui.screens.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PersistentTracker(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("PurrytifyTrackers", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val RECENTLY_PLAYED_KEY = "recently_played_songs"
        private const val LISTENED_SONGS_KEY = "listened_songs"
        private const val MAX_HISTORY_SIZE = 10
    }

    // Recently Played Songs Tracking
    fun addSongToRecentlyPlayed(song: Song) {
        // Get current list
        val currentList = getRecentlyPlayedSongs().toMutableList()

        // Remove duplicate if exists
        currentList.removeAll { it.title == song.title && it.artist == song.artist }

        // Add to beginning of list
        currentList.add(0, song)

        // Trim to max size
        val finalList = currentList.take(MAX_HISTORY_SIZE)

        // Save to preferences
        val jsonString = gson.toJson(finalList)
        prefs.edit().putString(RECENTLY_PLAYED_KEY, jsonString).apply()
    }

    fun getRecentlyPlayedSongs(): List<Song> {
        val jsonString = prefs.getString(RECENTLY_PLAYED_KEY, null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            emptyList()
        }
    }

    // Listened Songs Tracking
    fun addListenedSong(song: Song) {
        // Get current set of listened songs
        val currentSet = getListenedSongs().toMutableSet()

        // Create a unique key for the song
        val songKey = "${song.title}_${song.artist}"

        // Add to set
        currentSet.add(songKey)

        // Save to preferences
        val jsonString = gson.toJson(currentSet)
        prefs.edit().putString(LISTENED_SONGS_KEY, jsonString).apply()
    }

    fun getListenedSongs(): Set<String> {
        val jsonString = prefs.getString(LISTENED_SONGS_KEY, null)
        return if (jsonString != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            emptySet()
        }
    }

    // Clear all tracking data if needed (e.g., on logout)
    fun clearAllTracking() {
        prefs.edit().apply {
            remove(RECENTLY_PLAYED_KEY)
            remove(LISTENED_SONGS_KEY)
            apply()
        }
    }
}