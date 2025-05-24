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

class AudioDeviceManager(private val context: Context) {
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
            
            // Always add speaker
            devices.add(
                AudioDevice(
                    id = "speaker",
                    name = "Speaker",
                    type = AudioDeviceType.SPEAKER,
                    isConnected = true,
                    isActive = !hasWiredOrBluetoothConnected()
                )
            )
            
            // Get all available audio devices
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            // Log all found devices for debugging
            audioDevices.forEach {
                Log.d("AudioDeviceManager", "Found audio device: type=${it.type}, product=${it.productName}")
            }
            
            // Process wired headphones/headsets
            if (headsetStateProvider.isHeadsetPlugged.value) {
                val wiredDevice = audioDevices.find { 
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                
                if (wiredDevice != null) {
                    val name = wiredDevice.productName?.toString() ?: "Wired Headphones"
                    devices.add(
                        AudioDevice(
                            id = "wired_${wiredDevice.id}",
                            name = name,
                            type = AudioDeviceType.WIRED_HEADSET,
                            isConnected = true,
                            isActive = !bluetoothHeadsetStateProvider.isHeadsetConnected.value,
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
                    val name = bluetoothDevice.productName?.toString() ?: "Bluetooth Device"
                    devices.add(
                        AudioDevice(
                            id = "bluetooth_${bluetoothDevice.id}",
                            name = name,
                            type = AudioDeviceType.BLUETOOTH_HEADSET,
                            isConnected = true,
                            isActive = true,
                            deviceInfo = bluetoothDevice
                        )
                    )
                } else {
                    // Fallback if specific device not found but Bluetooth is connected
                    devices.add(
                        AudioDevice(
                            id = "bluetooth_generic",
                            name = "Bluetooth Device",
                            type = AudioDeviceType.BLUETOOTH_HEADSET,
                            isConnected = true,
                            isActive = true
                        )
                    )
                }
            }
            
            _availableDevices.value = devices
            
            // Update active device
            updateActiveDevice()
            
            Log.d("AudioDeviceManager", "Available devices updated: ${devices.size} devices")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error updating available devices", e)
            _errorMessage.value = "Failed to detect audio devices"
        }
    }
    
    private fun hasWiredOrBluetoothConnected(): Boolean {
        return headsetStateProvider.isHeadsetPlugged.value || 
               bluetoothHeadsetStateProvider.isHeadsetConnected.value
    }
    
    fun selectAudioDevice(device: AudioDevice): Boolean {
        try {
            Log.d("AudioDeviceManager", "Switching to device: ${device.name}, type: ${device.type}")
            
            when (device.type) {
                AudioDeviceType.SPEAKER -> {
                    // Force use speaker even if headphones are connected
                    audioManager.isSpeakerphoneOn = true
                    
                    // If we're on Android 12+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            audioManager.clearCommunicationDevice()
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error clearing communication device", e)
                        }
                    }
                    
                    // Also update our model
                    _activeDevice.value = device
                    Log.d("AudioDeviceManager", "Switched to speaker")
                }
                
                AudioDeviceType.BLUETOOTH_HEADSET, 
                AudioDeviceType.BLUETOOTH_HEADPHONES -> {
                    // For Bluetooth, turn off speakerphone
                    audioManager.isSpeakerphoneOn = false
                    
                    // On Android 12+, try to set communication device
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.deviceInfo != null) {
                        try {
                            audioManager.setCommunicationDevice(device.deviceInfo)
                        } catch (e: Exception) {
                            Log.e("AudioDeviceManager", "Error setting Bluetooth as communication device", e)
                        }
                    }
                    
                    // Update our model
                    _activeDevice.value = device
                    Log.d("AudioDeviceManager", "Switched to Bluetooth device")
                }
                
                AudioDeviceType.WIRED_HEADSET,
                AudioDeviceType.WIRED_HEADPHONES -> {
                    // For wired devices, just turn off speakerphone
                    audioManager.isSpeakerphoneOn = false
                    
                    // Update our model
                    _activeDevice.value = device
                    Log.d("AudioDeviceManager", "Switched to wired headset")
                }
                
                else -> {
                    // Handle other device types
                    audioManager.isSpeakerphoneOn = false
                    _activeDevice.value = device
                    Log.d("AudioDeviceManager", "Switched to device: ${device.name}")
                }
            }
            
            // Update UI
            updateAvailableDevices()
            
            return true
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error selecting audio device", e)
            _errorMessage.value = "Failed to select audio device: ${e.message}"
            return false
        }
    }
    
    private fun updateActiveDevice() {
        val devices = _availableDevices.value
        
        // Determine which device is active based on system state
        val activeDevice = when {
            // Bluetooth has highest priority if connected
            bluetoothHeadsetStateProvider.isHeadsetConnected.value -> {
                devices.find { it.type.isBluetooth() }
            }
            
            // Then wired headsets
            headsetStateProvider.isHeadsetPlugged.value -> {
                devices.find { it.type.isWired() }
            }
            
            // Default to speaker
            else -> {
                devices.find { it.type == AudioDeviceType.SPEAKER }
            }
        }
        
        _activeDevice.value = activeDevice
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