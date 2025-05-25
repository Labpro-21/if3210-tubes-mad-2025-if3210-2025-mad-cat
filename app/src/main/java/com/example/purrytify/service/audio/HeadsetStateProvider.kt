package com.example.purrytify.service.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HeadsetStateProvider(
    private val context: Context,
    private val audioManager: AudioManager
) {
    private val _isHeadsetPlugged = MutableStateFlow(getHeadsetState())
    val isHeadsetPlugged: StateFlow<Boolean> = _isHeadsetPlugged.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == AudioManager.ACTION_HEADSET_PLUG) {
                when (intent.getIntExtra("state", -1)) {
                    0 -> {
                        Log.d("HeadsetStateProvider", "Wired headset disconnected")
                        _isHeadsetPlugged.value = false
                    }
                    1 -> {
                        Log.d("HeadsetStateProvider", "Wired headset connected")
                        _isHeadsetPlugged.value = true
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG)
        try {
            context.registerReceiver(receiver, filter)
            Log.d("HeadsetStateProvider", "Headset state provider initialized")
        } catch (e: Exception) {
            Log.e("HeadsetStateProvider", "Error registering receiver", e)
        }
    }

    private fun getHeadsetState(): Boolean {
        return try {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            audioDevices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } catch (e: Exception) {
            Log.e("HeadsetStateProvider", "Error getting headset state", e)
            false
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
            Log.d("HeadsetStateProvider", "Headset state provider cleaned up")
        } catch (e: Exception) {
            Log.e("HeadsetStateProvider", "Error unregistering receiver", e)
        }
    }
} 