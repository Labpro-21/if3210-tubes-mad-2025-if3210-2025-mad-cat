package com.example.purrytify.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val coverUri: String,
    val uri: String,
    val duration: String
)