package com.example.purrytify.data.model

import android.media.AudioDeviceInfo

data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isConnected: Boolean = false,
    val isActive: Boolean = false,
    val deviceInfo: AudioDeviceInfo? = null
) 