package com.example.purrytify.data.model

import com.google.gson.annotations.SerializedName

data class OnlineSong(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("artist")
    val artist: String,
    
    @SerializedName("artwork")
    val artworkUrl: String,
    
    @SerializedName("url")
    val audioUrl: String,
    
    @SerializedName("duration")
    val duration: String,
    
    @SerializedName("country")
    val country: String,
    
    @SerializedName("rank")
    val rank: Int,
    
    @SerializedName("createdAt")
    val createdAt: String,
    
    @SerializedName("updatedAt")
    val updatedAt: String
) {
    fun getDurationInMillis(): Long {
        val parts = duration.split(":")
        return if (parts.size == 2) {
            val minutes = parts[0].toLongOrNull() ?: 0
            val seconds = parts[1].toLongOrNull() ?: 0
            (minutes * 60 + seconds) * 1000
        } else {
            0L
        }
    }
}
