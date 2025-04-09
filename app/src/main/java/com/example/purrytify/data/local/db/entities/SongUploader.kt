package com.example.purrytify.data.local.db.entities

import androidx.room.Entity

@Entity("song_uploader", primaryKeys = ["uploaderEmail", "songId"])
data class SongUploader(
    val uploaderEmail: String,
    val songId: Int
)