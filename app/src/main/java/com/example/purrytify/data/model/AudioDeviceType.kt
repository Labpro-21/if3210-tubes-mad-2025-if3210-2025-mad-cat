package com.example.purrytify.data.model

enum class AudioDeviceType {
    SPEAKER,
    EARPIECE,
    WIRED_HEADPHONES,
    WIRED_HEADSET,
    BLUETOOTH_HEADPHONES,
    BLUETOOTH_HEADSET,
    BLUETOOTH_SPEAKER,
    USB_HEADSET,
    INTERNAL_SPEAKER;
    
    fun getDisplayName(): String {
        return when (this) {
            SPEAKER -> "Speaker"
            EARPIECE -> "Earpiece"
            WIRED_HEADPHONES -> "Wired Headphones"
            WIRED_HEADSET -> "Wired Headset"
            BLUETOOTH_HEADPHONES -> "Bluetooth Headphones"
            BLUETOOTH_HEADSET -> "Bluetooth Headset"
            BLUETOOTH_SPEAKER -> "Bluetooth Speaker"
            USB_HEADSET -> "USB Headset"
            INTERNAL_SPEAKER -> "Internal Speaker"
        }
    }
    
    fun isBluetooth(): Boolean {
        return this == BLUETOOTH_HEADPHONES || this == BLUETOOTH_HEADSET || this == BLUETOOTH_SPEAKER
    }
    
    fun isWired(): Boolean {
        return this == WIRED_HEADPHONES || this == WIRED_HEADSET || this == USB_HEADSET
    }
} 