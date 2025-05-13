package com.example.purrytify.data.local.db.entities

import androidx.room.Entity

@Entity("downloaded_songs", primaryKeys = ["userEmail", "songTitle", "songArtist"])
data class DownloadedSongCrossRef(
    val userEmail: String,
    val songTitle: String,
    val songArtist: String
)