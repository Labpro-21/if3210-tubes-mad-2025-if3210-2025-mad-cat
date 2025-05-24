package com.example.purrytify.ui.screens

data class Song(
    val title: String,
    val artist: String,
    val duration: String,
    val uri: String,
    val coverUri: String = ""
)