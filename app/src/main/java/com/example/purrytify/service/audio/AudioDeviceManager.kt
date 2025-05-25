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
import androidx.media.VolumeProviderCompat
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
            
            // Process Bluetooth devices - enumerate ALL connected Bluetooth devices
            if (bluetoothHeadsetStateProvider.isHeadsetConnected.value) {
                val bluetoothDevices = audioDevices.filter {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                
                if (bluetoothDevices.isNotEmpty()) {
                    // Add each Bluetooth device separately
                    bluetoothDevices.forEachIndexed { index, bluetoothDevice ->
                        val deviceName = getBluetoothDeviceNameForDevice(bluetoothDevice, index)
                        
                        // Only mark one Bluetooth device as active at a time
                        val bluetoothIsActive = !audioManager.isSpeakerphoneOn && 
                                              bluetoothHeadsetStateProvider.isHeadsetConnected.value &&
                                              (if (isManualSelection && manuallySelectedDeviceId != null) {
                                                  "bluetooth_${bluetoothDevice.id}" == manuallySelectedDeviceId
                                              } else {
                                                  index == 0 // Default to first device if no manual selection
                                              })
                        
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
                        
                        Log.d("AudioDeviceManager", "Added Bluetooth device: $deviceName (ID: bluetooth_${bluetoothDevice.id})")
                    }
                } else {
                    // Fallback: try to get Bluetooth device names from adapter
                    val connectedBluetoothDevices = getConnectedBluetoothDevices()
                    connectedBluetoothDevices.forEachIndexed { index, deviceName ->
                        val bluetoothIsActive = !audioManager.isSpeakerphoneOn && 
                                              bluetoothHeadsetStateProvider.isHeadsetConnected.value &&
                                              (if (isManualSelection && manuallySelectedDeviceId != null) {
                                                  "bluetooth_fallback_$index" == manuallySelectedDeviceId
                                              } else {
                                                  index == 0 // Default to first device if no manual selection
                                              })
                        
                        devices.add(
                            AudioDevice(
                                id = "bluetooth_fallback_$index",
                                name = deviceName,
                                type = AudioDeviceType.BLUETOOTH_HEADSET,
                                isConnected = true,
                                isActive = bluetoothIsActive
                            )
                        )
                        
                        Log.d("AudioDeviceManager", "Added fallback Bluetooth device: $deviceName (ID: bluetooth_fallback_$index)")
                    }
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
    
    private fun getBluetoothDeviceNameForDevice(bluetoothAudioDevice: AudioDeviceInfo, index: Int): String {
        return try {
            // First, try to get the name from the AudioDeviceInfo
            val productName = bluetoothAudioDevice.productName?.toString()
            if (!productName.isNullOrBlank() && 
                !productName.contains("phone", ignoreCase = true) && 
                !productName.contains("android", ignoreCase = true) &&
                productName.length > 3) {
                return productName
            }
            
            // Try to match with connected Bluetooth devices by address if available
            val connectedDevices = getConnectedBluetoothDevices()
            if (index < connectedDevices.size) {
                return connectedDevices[index]
            }
            
            // Final fallback based on device type
            val baseName = when (bluetoothAudioDevice.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Headphones"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
                else -> "Bluetooth Device"
            }
            
            return if (index > 0) "$baseName ${index + 1}" else baseName
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error getting Bluetooth device name for device", e)
            "Bluetooth Device ${index + 1}"
        }
    }

    private fun getConnectedBluetoothDevices(): List<String> {
        return try {
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null || !hasBluetoothPermission()) {
                return listOf("Bluetooth Audio")
            }
            
            val deviceNames = mutableListOf<String>()
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
                        deviceNames.add(deviceName)
                        Log.d("AudioDeviceManager", "Found connected Bluetooth device: $deviceName")
                    }
                } catch (e: Exception) {
                    Log.e("AudioDeviceManager", "Error getting Bluetooth device name", e)
                }
            }
            
            return if (deviceNames.isEmpty()) listOf("Bluetooth Audio") else deviceNames
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error getting connected Bluetooth devices", e)
            listOf("Bluetooth Audio")
        }
    }

    private fun getConnectedBluetoothDeviceName(): String {
        return try {
            val devices = getConnectedBluetoothDevices()
            return devices.firstOrNull() ?: "Bluetooth Audio"
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
                    Log.d("AudioDeviceManager", "=== FORCING SPEAKER SELECTION ===")
                    
                    // Step 1: Clear any previously set communication device first
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            audioManager.clearCommunicationDevice()
                            Log.d("AudioDeviceManager", "Cleared communication device for speaker")
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error clearing communication device", e)
                        }
                    }
                    
                    // Step 2: Force speakerphone on and set proper mode
                    audioManager.isSpeakerphoneOn = true
                    audioManager.mode = AudioManager.MODE_NORMAL
                    
                    // Step 3: Find and explicitly set the built-in speaker as communication device
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            val builtinSpeaker = audioDevices.find { 
                                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
                            }
                            
                            if (builtinSpeaker != null) {
                                val success = audioManager.setCommunicationDevice(builtinSpeaker)
                                Log.d("AudioDeviceManager", "Set built-in speaker as communication device: $success")
                                
                                if (!success) {
                                    Log.w("AudioDeviceManager", "Failed to set built-in speaker, trying alternative")
                                    // Force speaker routing by disabling Bluetooth SCO
                                    try {
                                        audioManager.stopBluetoothSco()
                                        audioManager.isBluetoothScoOn = false
                                    } catch (e: Exception) {
                                        Log.e("AudioDeviceManager", "Error stopping Bluetooth SCO", e)
                                    }
                                }
                            } else {
                                Log.w("AudioDeviceManager", "Built-in speaker not found in audio devices")
                            }
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error setting built-in speaker as communication device", e)
                        }
                    }
                    
                    // Step 4: Additional legacy approach for older Android versions
                    try {
                        // Disable Bluetooth SCO to ensure audio doesn't route to Bluetooth
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        Log.d("AudioDeviceManager", "Disabled Bluetooth SCO for speaker")
                    } catch (e: Exception) {
                        Log.e("AudioDeviceManager", "Error disabling Bluetooth SCO", e)
                    }
                    
                    Log.d("AudioDeviceManager", "=== SPEAKER SELECTION COMPLETE ===")
                    Log.d("AudioDeviceManager", "Speakerphone: ${audioManager.isSpeakerphoneOn}")
                    Log.d("AudioDeviceManager", "Bluetooth SCO: ${audioManager.isBluetoothScoOn}")
                }
                
                AudioDeviceType.BLUETOOTH_HEADSET, 
                AudioDeviceType.BLUETOOTH_HEADPHONES,
                AudioDeviceType.BLUETOOTH_SPEAKER -> {
                    // For Bluetooth, turn off speakerphone and set proper mode
                    audioManager.isSpeakerphoneOn = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                    
                    // Clear any previously set communication device first
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            audioManager.clearCommunicationDevice()
                            Log.d("AudioDeviceManager", "Cleared previous communication device")
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error clearing communication device", e)
                        }
                    }
                    
                    // Set the specific Bluetooth device as communication device
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.deviceInfo != null) {
                        try {
                            val success = audioManager.setCommunicationDevice(device.deviceInfo)
                            Log.d("AudioDeviceManager", "Set Bluetooth communication device: ${device.name}, success: $success")
                            
                            if (!success) {
                                Log.w("AudioDeviceManager", "Failed to set communication device, trying alternative approach")
                                // Alternative approach: force audio routing through media session
                                try {
                                    mediaSession?.setPlaybackToRemote(
                                        object : VolumeProviderCompat(
                                            VOLUME_CONTROL_FIXED,
                                            100,
                                            50
                                        ) {
                                            override fun onSetVolumeTo(volume: Int) {
                                                // Handle volume changes if needed
                                            }
                                        }
                                    )
                                } catch (e2: Exception) {
                                    Log.e("AudioDeviceManager", "Error setting playback to remote", e2)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error setting Bluetooth as communication device", e)
                        }
                    } else {
                        // For older Android versions, use legacy approach
                        try {
                            // Force audio routing by temporarily changing stream volume
                            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_SHOW_UI)
                            Log.d("AudioDeviceManager", "Applied legacy Bluetooth routing for device: ${device.name}")
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error applying legacy Bluetooth routing", e)
                        }
                    }
                    
                    Log.d("AudioDeviceManager", "Switched to Bluetooth device: ${device.name} - speakerphone: ${audioManager.isSpeakerphoneOn}")
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
            
            // Force audio routing refresh with enhanced approach for different device types
            try {
                when {
                    device.type.isBluetooth() -> {
                        // Enhanced refresh for Bluetooth devices
                        forceBluetoothAudioRouting(device)
                    }
                    device.type == AudioDeviceType.SPEAKER -> {
                        // Enhanced refresh for speaker to ensure it routes to built-in speaker
                        forceSpeakerAudioRouting()
                    }
                    else -> {
                        // Standard refresh for other devices
                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                    }
                }
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
                // Find the first Bluetooth device (default) or keep existing active Bluetooth device
                val bluetoothDevices = devices.filter { it.type.isBluetooth() }
                val currentActiveBluetoothDevice = bluetoothDevices.find { it.isActive }
                
                (currentActiveBluetoothDevice ?: bluetoothDevices.firstOrNull())?.also {
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
    
    fun debugLogAvailableDevices() {
        try {
            Log.d("AudioDeviceManager", "=== DEBUG: Available Audio Devices ===")
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            audioDevices.forEachIndexed { index, device ->
                Log.d("AudioDeviceManager", "Device $index: type=${device.type}, id=${device.id}, product=${device.productName}")
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> Log.d("AudioDeviceManager", "  -> Bluetooth SCO")
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Log.d("AudioDeviceManager", "  -> Bluetooth A2DP")
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Log.d("AudioDeviceManager", "  -> Built-in Speaker")
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> Log.d("AudioDeviceManager", "  -> Wired Headset")
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Log.d("AudioDeviceManager", "  -> Wired Headphones")
                    else -> Log.d("AudioDeviceManager", "  -> Other: ${device.type}")
                }
            }
            
            Log.d("AudioDeviceManager", "Current app devices:")
            _availableDevices.value.forEach { device ->
                Log.d("AudioDeviceManager", "  ${device.name} (${device.id}) - Active: ${device.isActive}")
            }
            
            Log.d("AudioDeviceManager", "System state:")
            Log.d("AudioDeviceManager", "  Speakerphone: ${audioManager.isSpeakerphoneOn}")
            Log.d("AudioDeviceManager", "  Bluetooth connected: ${bluetoothHeadsetStateProvider.isHeadsetConnected.value}")
            Log.d("AudioDeviceManager", "  Wired connected: ${headsetStateProvider.isHeadsetPlugged.value}")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val commDevice = audioManager.communicationDevice
                Log.d("AudioDeviceManager", "  Communication device: ${commDevice?.productName} (type: ${commDevice?.type}, id: ${commDevice?.id})")
            }
            Log.d("AudioDeviceManager", "  Bluetooth SCO: ${audioManager.isBluetoothScoOn}")
            Log.d("AudioDeviceManager", "  Audio mode: ${audioManager.mode}")
            
            // Log all Bluetooth devices specifically
            val bluetoothDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            Log.d("AudioDeviceManager", "Bluetooth audio devices found: ${bluetoothDevices.size}")
            bluetoothDevices.forEachIndexed { index, device ->
                Log.d("AudioDeviceManager", "  BT Device $index: ${device.productName} (type: ${device.type}, id: ${device.id})")
            }
            
            Log.d("AudioDeviceManager", "=== END DEBUG ===")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error in debug logging", e)
        }
    }
    
    fun startDeviceDiscovery() {
        updateAvailableDevices()
    }
    
    private fun getCurrentlyActiveAudioDevice(): AudioDeviceInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice
            } else {
                // For older versions, try to determine from available devices
                val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                outputDevices.find { device ->
                    when {
                        audioManager.isSpeakerphoneOn -> device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        bluetoothHeadsetStateProvider.isHeadsetConnected.value -> 
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        headsetStateProvider.isHeadsetPlugged.value -> 
                            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
                            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        else -> device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error getting currently active audio device", e)
            null
        }
    }
    
    fun switchToDevice(device: AudioDevice): Boolean {
        return selectAudioDevice(device)
    }
    
    private fun forceBluetoothAudioRouting(device: AudioDevice) {
        try {
            Log.d("AudioDeviceManager", "Forcing Bluetooth audio routing for: ${device.name}")
            
            // Multiple approaches to ensure Bluetooth routing works
            
            // 1. Force volume change with audio focus
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_SHOW_UI)
            
            // 2. Brief mode change to force re-evaluation
            val currentMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Thread.sleep(50) // Brief delay
            audioManager.mode = currentMode
            
            // 3. For Android 12+, ensure communication device is properly set
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.deviceInfo != null) {
                // Small delay then re-set communication device
                Thread.sleep(100)
                try {
                    audioManager.setCommunicationDevice(device.deviceInfo)
                    Log.d("AudioDeviceManager", "Re-applied communication device for: ${device.name}")
                } catch (e: Exception) {
                    Log.e("AudioDeviceManager", "Error re-applying communication device", e)
                }
            }
            
            // 4. Update media session to trigger routing refresh
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY)
                    .build()
            )
            
            Log.d("AudioDeviceManager", "Completed Bluetooth audio routing refresh")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error forcing Bluetooth audio routing", e)
        }
    }
    
    private fun forceSpeakerAudioRouting() {
        try {
            Log.d("AudioDeviceManager", "Forcing speaker audio routing")
            
            // Multiple approaches to ensure speaker routing works
            
            // 1. Ensure speakerphone is on
            audioManager.isSpeakerphoneOn = true
            
            // 2. Disable all Bluetooth audio connections
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                Log.d("AudioDeviceManager", "Disabled Bluetooth SCO for speaker routing")
            } catch (e: Exception) {
                Log.e("AudioDeviceManager", "Error disabling Bluetooth SCO", e)
            }
            
            // 3. Force volume change to trigger routing refresh
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_SHOW_UI)
            
            // 4. Brief mode change to force re-evaluation
            val currentMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Thread.sleep(50) // Brief delay
            audioManager.mode = AudioManager.MODE_NORMAL
            
            // 5. For Android 12+, ensure built-in speaker is set as communication device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val builtinSpeaker = audioDevices.find { 
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
                    }
                    
                    if (builtinSpeaker != null) {
                        Thread.sleep(100) // Small delay
                        val success = audioManager.setCommunicationDevice(builtinSpeaker)
                        Log.d("AudioDeviceManager", "Re-applied built-in speaker as communication device: $success")
                    }
                } catch (e: Exception) {
                    Log.e("AudioDeviceManager", "Error re-applying built-in speaker", e)
                }
            }
            
            // 6. Update media session to trigger routing refresh
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY)
                    .build()
            )
            
            Log.d("AudioDeviceManager", "Completed speaker audio routing refresh")
            Log.d("AudioDeviceManager", "Final state - Speakerphone: ${audioManager.isSpeakerphoneOn}, Bluetooth SCO: ${audioManager.isBluetoothScoOn}")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error forcing speaker audio routing", e)
        }
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