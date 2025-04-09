package com.example.purrytify.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("liked_songs", primaryKeys = ["userEmail", "songId"])
data class LikedSongCrossRef(
    val userEmail: String,
    val songId: Int
)