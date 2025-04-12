package com.example.purrytify.data.local.db.entities

import androidx.room.Entity

@Entity("recently_played", primaryKeys = ["userEmail", "songId", "timestamp"])
data class RecentlyPlayedSong(
    val userEmail: String,
    val songId: Int,
    val timestamp: Long // To order by most recent
)