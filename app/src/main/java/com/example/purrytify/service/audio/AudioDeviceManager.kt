package com.example.purrytify.service.audio

import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.purrytify.data.model.AudioDevice
import com.example.purrytify.data.model.AudioDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AudioDeviceManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AudioDeviceManager? = null
        
        fun getInstance(context: Context): AudioDeviceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioDeviceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    
    private val headsetStateProvider = HeadsetStateProvider(context, audioManager)
    private val bluetoothHeadsetStateProvider = BluetoothHeadsetStateProvider(context, bluetoothManager)
    
    private val _availableDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioDevice>> = _availableDevices.asStateFlow()
    
    private val _activeDevice = MutableStateFlow<AudioDevice?>(null)
    val activeDevice: StateFlow<AudioDevice?> = _activeDevice.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedIsMicrophoneMuted: Boolean = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    // Flag to track if device was manually selected by user
    private var isManualSelection = false
    private var manuallySelectedDeviceId: String? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    // Media session for audio output control
    private var mediaSession: MediaSessionCompat? = null
    
    init {
        initializeAudioDeviceMonitoring()
        setupMediaSession()
    }
    
    private fun setupMediaSession() {
        try {
            // Create the media session for notification controls
            mediaSession = MediaSessionCompat(context, "PurrytifyAudioDeviceManager").apply {
                // Set metadata and state to make the session active
                setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Purrytify")
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Playing Music")
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
                        .build()
                )
                
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(
                            PlaybackStateCompat.STATE_PLAYING,
                            0L,
                            1.0f
                        )
                        .setActions(PlaybackStateCompat.ACTION_PLAY)
                        .build()
                )
                
                isActive = true
            }
            
            Log.d("AudioDeviceManager", "Media session set up for audio control")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error setting up media session", e)
        }
    }
    
    private fun initializeAudioDeviceMonitoring() {
        coroutineScope.launch {
            combine(
                headsetStateProvider.isHeadsetPlugged,
                bluetoothHeadsetStateProvider.isHeadsetConnected
            ) { wiredConnected, bluetoothConnected ->
                updateAvailableDevices()
            }.collect { }
        }
        
        updateAvailableDevices()
    }
    
    fun requestAudioFocus(): Boolean {
        return try {
            savedAudioMode = audioManager.mode
            savedIsMicrophoneMuted = audioManager.isMicrophoneMute
            
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = getAudioFocusRequest()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                audioManager.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            if (hasAudioFocus) {
                audioManager.mode = AudioManager.MODE_NORMAL
                Log.d("AudioDeviceManager", "Audio focus granted")
            } else {
                Log.w("AudioDeviceManager", "Audio focus request failed")
            }
            
            hasAudioFocus
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error requesting audio focus", e)
            false
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getAudioFocusRequest(): AudioFocusRequest {
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
    }
    
    fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                audioManager.abandonAudioFocus { }
            }
            
            audioManager.mode = savedAudioMode
            audioManager.isMicrophoneMute = savedIsMicrophoneMuted
            
            hasAudioFocus = false
            Log.d("AudioDeviceManager", "Audio focus abandoned")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error abandoning audio focus", e)
        }
    }
    
    private fun updateAvailableDevices() {
        try {
            val devices = mutableListOf<AudioDevice>()
            
            // Get all available audio devices first
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            // Log all found devices for debugging
            audioDevices.forEach {
                Log.d("AudioDeviceManager", "Found audio device: type=${it.type}, product=${it.productName}")
            }
            
            // Always add speaker (but check if it should be active)
            val speakerIsActive = !hasWiredOrBluetoothConnected() && audioManager.isSpeakerphoneOn
            devices.add(
                AudioDevice(
                    id = "speaker",
                    name = "Speaker",
                    type = AudioDeviceType.SPEAKER,
                    isConnected = true,
                    isActive = speakerIsActive
                )
            )
            
            // Process wired headphones/headsets
            if (headsetStateProvider.isHeadsetPlugged.value) {
                val wiredDevice = audioDevices.find { 
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                
                if (wiredDevice != null) {
                    // Improve device name detection for wired devices
                    val deviceName = getWiredDeviceName(wiredDevice)
                    
                    val wiredIsActive = !audioManager.isSpeakerphoneOn && !bluetoothHeadsetStateProvider.isHeadsetConnected.value
                    devices.add(
                        AudioDevice(
                            id = "wired_${wiredDevice.id}",
                            name = deviceName,
                            type = if (wiredDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET) AudioDeviceType.USB_HEADSET 
                                   else AudioDeviceType.WIRED_HEADSET,
                            isConnected = true,
                            isActive = wiredIsActive,
                            deviceInfo = wiredDevice
                        )
                    )
                }
            }
            
            // Process Bluetooth devices
            if (bluetoothHeadsetStateProvider.isHeadsetConnected.value) {
                val bluetoothDevice = audioDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                
                if (bluetoothDevice != null) {
                    // Get actual Bluetooth device name instead of phone name
                    val deviceName = getBluetoothDeviceName(bluetoothDevice)
                    
                    val bluetoothIsActive = !audioManager.isSpeakerphoneOn && bluetoothHeadsetStateProvider.isHeadsetConnected.value
                    devices.add(
                        AudioDevice(
                            id = "bluetooth_${bluetoothDevice.id}",
                            name = deviceName,
                            type = if (bluetoothDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) 
                                       AudioDeviceType.BLUETOOTH_HEADPHONES 
                                   else AudioDeviceType.BLUETOOTH_HEADSET,
                            isConnected = true,
                            isActive = bluetoothIsActive,
                            deviceInfo = bluetoothDevice
                        )
                    )
                } else {
                    // Fallback: try to get Bluetooth device name from adapter
                    val bluetoothDeviceName = getConnectedBluetoothDeviceName()
                    val bluetoothIsActive = !audioManager.isSpeakerphoneOn && bluetoothHeadsetStateProvider.isHeadsetConnected.value
                    devices.add(
                        AudioDevice(
                            id = "bluetooth_generic",
                            name = bluetoothDeviceName,
                            type = AudioDeviceType.BLUETOOTH_HEADSET,
                            isConnected = true,
                            isActive = bluetoothIsActive
                        )
                    )
                }
            }
            
            // Check if manually selected device is still available
            if (isManualSelection && manuallySelectedDeviceId != null) {
                val manualDeviceStillExists = devices.any { it.id == manuallySelectedDeviceId }
                if (!manualDeviceStillExists) {
                    Log.d("AudioDeviceManager", "Manually selected device no longer available, resetting to automatic")
                    isManualSelection = false
                    manuallySelectedDeviceId = null
                }
            }
            
            _availableDevices.value = devices
            
            // Update active device based on actual system state
            updateActiveDevice()
            
            Log.d("AudioDeviceManager", "Available devices updated: ${devices.size} devices")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error updating available devices", e)
            _errorMessage.value = "Failed to detect audio devices"
        }
    }
    
    private fun getWiredDeviceName(wiredDevice: AudioDeviceInfo): String {
        return try {
            // For wired devices, productName is usually more reliable
            when {
                !wiredDevice.productName.isNullOrBlank() -> {
                    val productName = wiredDevice.productName.toString()
                    // Filter out phone names and use descriptive names
                    if (productName.contains("phone", ignoreCase = true) || 
                        productName.contains("android", ignoreCase = true) ||
                        productName.length < 3) {
                        getWiredDeviceTypeName(wiredDevice.type)
                    } else {
                        productName
                    }
                }
                else -> getWiredDeviceTypeName(wiredDevice.type)
            }
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error getting wired device name", e)
            getWiredDeviceTypeName(wiredDevice.type)
        }
    }
    
    private fun getWiredDeviceTypeName(deviceType: Int): String {
        return when (deviceType) {
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            else -> "Headphones"
        }
    }
    
    private fun getBluetoothDeviceName(bluetoothAudioDevice: AudioDeviceInfo): String {
        return try {
            // First, try to get the name from connected Bluetooth devices
            val bluetoothDeviceName = getConnectedBluetoothDeviceName()
            if (bluetoothDeviceName != "Bluetooth Audio") {
                return bluetoothDeviceName
            }
            
            // Fallback to AudioDeviceInfo productName if it's not a phone name
            val productName = bluetoothAudioDevice.productName?.toString()
            if (!productName.isNullOrBlank() && 
                !productName.contains("phone", ignoreCase = true) && 
                !productName.contains("android", ignoreCase = true) &&
                productName.length > 3) {
                return productName
            }
            
            // Final fallback based on device type
            when (bluetoothAudioDevice.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Headphones"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
                else -> "Bluetooth Device"
            }
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error getting Bluetooth device name", e)
            "Bluetooth Device"
        }
    }
    
    private fun getConnectedBluetoothDeviceName(): String {
        return try {
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null || !hasBluetoothPermission()) {
                return "Bluetooth Audio"
            }
            
            // Get connected audio devices
            val connectedDevices = bluetoothAdapter.bondedDevices
            
            for (device in connectedDevices) {
                try {
                    // Check if this device is currently connected for audio
                    val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (hasBluetoothPermission()) device.name else null
                    } else {
                        @Suppress("DEPRECATION")
                        device.name
                    }
                    
                    if (!deviceName.isNullOrBlank() && deviceName.length > 2) {
                        Log.d("AudioDeviceManager", "Found Bluetooth device: $deviceName")
                        return deviceName
                    }
                } catch (e: Exception) {
                    Log.e("AudioDeviceManager", "Error getting Bluetooth device name", e)
                }
            }
            
            "Bluetooth Audio"
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error getting connected Bluetooth device name", e)
            "Bluetooth Audio"
        }
    }
    
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun hasWiredOrBluetoothConnected(): Boolean {
        return headsetStateProvider.isHeadsetPlugged.value || 
               bluetoothHeadsetStateProvider.isHeadsetConnected.value
    }
    
    fun selectAudioDevice(device: AudioDevice): Boolean {
        try {
            Log.d("AudioDeviceManager", "=== MANUAL DEVICE SELECTION ===")
            Log.d("AudioDeviceManager", "User selecting device: ${device.name} (ID: ${device.id}), type: ${device.type}")
            
            // Request audio focus first
            if (!hasAudioFocus) {
                requestAudioFocus()
            }
            
            // Mark this as a manual selection to prevent system overrides
            isManualSelection = true
            manuallySelectedDeviceId = device.id
            
            // Immediately update the active device to provide instant UI feedback
            _activeDevice.value = device
            Log.d("AudioDeviceManager", "Set active device to: ${device.name}")
            
            when (device.type) {
                AudioDeviceType.SPEAKER -> {
                    // Force use speaker even if headphones are connected
                    audioManager.isSpeakerphoneOn = true
                    audioManager.mode = AudioManager.MODE_NORMAL
                    
                    // If we're on Android 12+, clear any previously set communication device
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            audioManager.clearCommunicationDevice()
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error clearing communication device", e)
                        }
                    }
                    
                    Log.d("AudioDeviceManager", "Switched to speaker - speakerphone: ${audioManager.isSpeakerphoneOn}")
                }
                
                AudioDeviceType.BLUETOOTH_HEADSET, 
                AudioDeviceType.BLUETOOTH_HEADPHONES,
                AudioDeviceType.BLUETOOTH_SPEAKER -> {
                    // For Bluetooth, turn off speakerphone and set proper mode
                    audioManager.isSpeakerphoneOn = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                    
                    // On Android 12+, try to set communication device
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.deviceInfo != null) {
                        try {
                            audioManager.setCommunicationDevice(device.deviceInfo)
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error setting Bluetooth as communication device", e)
                        }
                    }
                    
                    Log.d("AudioDeviceManager", "Switched to Bluetooth device - speakerphone: ${audioManager.isSpeakerphoneOn}")
                }
                
                AudioDeviceType.WIRED_HEADSET,
                AudioDeviceType.WIRED_HEADPHONES,
                AudioDeviceType.USB_HEADSET -> {
                    // For wired devices, turn off speakerphone and set proper mode
                    audioManager.isSpeakerphoneOn = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                    
                    // On Android 12+, set the communication device if available
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.deviceInfo != null) {
                        try {
                            audioManager.setCommunicationDevice(device.deviceInfo)
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error setting wired device as communication device", e)
                        }
                    }
                    
                    Log.d("AudioDeviceManager", "Switched to wired headset - speakerphone: ${audioManager.isSpeakerphoneOn}")
                }
                
                else -> {
                    // Handle other device types
                    audioManager.isSpeakerphoneOn = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                    Log.d("AudioDeviceManager", "Switched to device: ${device.name} - speakerphone: ${audioManager.isSpeakerphoneOn}")
                }
            }
            
            // Force audio routing refresh
            try {
                // This forces the system to re-evaluate audio routing
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
            } catch (e: Exception) {
                Log.e("AudioDeviceManager", "Error refreshing audio routing", e)
            }
            
            // Update UI with the new device state - but don't call full update to avoid override
            updateAvailableDevicesWithActiveDevice(device)
            
            // Notify media session about the change
            try {
                mediaSession?.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY)
                        .build()
                )
            } catch (e: Exception) {
                Log.e("AudioDeviceManager", "Error updating media session", e)
            }
            
            Log.d("AudioDeviceManager", "=== MANUAL SELECTION COMPLETE ===")
            return true
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error selecting audio device", e)
            _errorMessage.value = "Failed to select audio device: ${e.message}"
            isManualSelection = false
            manuallySelectedDeviceId = null
            return false
        }
    }
    
    private fun updateAvailableDevicesWithActiveDevice(activeDevice: AudioDevice) {
        try {
            Log.d("AudioDeviceManager", "=== UPDATING DEVICE LIST UI ===")
            Log.d("AudioDeviceManager", "Setting active device: ${activeDevice.name} (ID: ${activeDevice.id})")
            
            // Update the devices list with the correct active state
            val currentDevices = _availableDevices.value.map { device ->
                val isActive = device.id == activeDevice.id
                Log.d("AudioDeviceManager", "Device: ${device.name} (ID: ${device.id}) - Active: $isActive")
                device.copy(isActive = isActive)
            }
            _availableDevices.value = currentDevices
            
            Log.d("AudioDeviceManager", "Device list updated. Active device count: ${currentDevices.count { it.isActive }}")
            Log.d("AudioDeviceManager", "=== UI UPDATE COMPLETE ===")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error updating devices with active device", e)
            // Fallback to full update
            updateAvailableDevices()
        }
    }
    
    private fun updateActiveDevice() {
        // If user manually selected a device, don't override their choice
        if (isManualSelection && manuallySelectedDeviceId != null) {
            Log.d("AudioDeviceManager", "Skipping automatic active device update - user manually selected device: $manuallySelectedDeviceId")
            return
        }
        
        val devices = _availableDevices.value
        
        Log.d("AudioDeviceManager", "=== AUTOMATIC ACTIVE DEVICE UPDATE ===")
        Log.d("AudioDeviceManager", "Bluetooth connected: ${bluetoothHeadsetStateProvider.isHeadsetConnected.value}")
        Log.d("AudioDeviceManager", "Wired connected: ${headsetStateProvider.isHeadsetPlugged.value}")
        Log.d("AudioDeviceManager", "Speaker on: ${audioManager.isSpeakerphoneOn}")
        
        // Determine which device is active based on system state
        val activeDevice = when {
            // Bluetooth has highest priority if connected
            bluetoothHeadsetStateProvider.isHeadsetConnected.value -> {
                devices.find { it.type.isBluetooth() }?.also {
                    Log.d("AudioDeviceManager", "Auto-selecting Bluetooth device: ${it.name}")
                }
            }
            
            // Then wired headsets
            headsetStateProvider.isHeadsetPlugged.value -> {
                devices.find { it.type.isWired() }?.also {
                    Log.d("AudioDeviceManager", "Auto-selecting wired device: ${it.name}")
                }
            }
            
            // Default to speaker
            else -> {
                devices.find { it.type == AudioDeviceType.SPEAKER }?.also {
                    Log.d("AudioDeviceManager", "Auto-selecting speaker: ${it.name}")
                }
            }
        }
        
        _activeDevice.value = activeDevice
        Log.d("AudioDeviceManager", "=== AUTO UPDATE COMPLETE: ${activeDevice?.name} ===")
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun startDeviceDiscovery() {
        updateAvailableDevices()
    }
    
    fun switchToDevice(device: AudioDevice): Boolean {
        return selectAudioDevice(device)
    }
    
    /**
     * Gets the MediaSession used for audio playback control and output device selection.
     * This should be used by the notification service.
     */
    fun getMediaSession(): MediaSessionCompat? {
        return mediaSession
    }
    
    fun cleanup() {
        try {
            headsetStateProvider.cleanup()
            bluetoothHeadsetStateProvider.cleanup()
            abandonAudioFocus()
            
            // Release media session
            mediaSession?.apply {
                isActive = false
                release()
            }
            mediaSession = null
            
            Log.d("AudioDeviceManager", "Audio device manager cleaned up")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error during cleanup", e)
        }
    }
} 