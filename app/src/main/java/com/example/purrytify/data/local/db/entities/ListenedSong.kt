package com.example.purrytify.data.local.db.entities

import androidx.room.Entity

@Entity("listened_songs", primaryKeys = ["userEmail", "songId"])
data class ListenedSong(
    val userEmail: String,
    val songId: Int,
    val timestamp: Long // Optional: to track when it was first listened to
)