package com.example.purrytify.data

import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.screens.Song

object PlayHistoryTracker {
    private val userHistories = mutableMapOf<String, MutableList<Song>>()
    private const val MAX_HISTORY_SIZE = 10

    fun addSongToHistory(email: String, song: Song, tokenManager: TokenManager, context: android.content.Context) {
        val history = tokenManager.getRecentlyPlayed(email).toMutableList()

        history.removeAll { it.title == song.title && it.artist == song.artist }
        history.add(0, song)

        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }

        tokenManager.saveRecentlyPlayed(email, history)
    }

    fun getRecentlyPlayedSongs(email: String, tokenManager: TokenManager): List<Song> {
        return tokenManager.getRecentlyPlayed(email)
    }

    fun clearHistory(email: String, tokenManager: TokenManager) {
        tokenManager.saveRecentlyPlayed(email, emptyList())
    }
}