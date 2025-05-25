package com.example.purrytify.service.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.example.purrytify.data.model.AudioDevice
import com.example.purrytify.data.model.AudioDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    
    private val _availableDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioDevice>> = _availableDevices.asStateFlow()
    
    private val _activeDevice = MutableStateFlow<AudioDevice?>(null)
    val activeDevice: StateFlow<AudioDevice?> = _activeDevice.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var userSelectedDeviceId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val isEmulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")
    
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            if (isEmulator) {
                Log.d("AudioDeviceManager", "Skipping receiver on emulator")
                return
            }
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    updateAudioDevices()
                    if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                        setAudioOutputToSpeaker()
                        _errorMessage.value = "Bluetooth device disconnected. Switched to internal speaker."
                    }
                }
            }
        }
    }
    
    init {
        initializeAudioRouting()
    }
    
    private fun initializeAudioRouting() {
        startMonitoringAudioOutput()
        Log.d("AudioDeviceManager", "initializeAudioRouting: isEmulator=$isEmulator")
        if (isEmulator) {
            _errorMessage.value = "Audio routing not supported on emulator. Using internal speaker."
            setAudioOutputToSpeaker()
            return
        }
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                _errorMessage.value = "Bluetooth is not supported on this device."
                setAudioOutputToSpeaker()
                return
            }
            updateAudioDevices()
            registerAudioDeviceReceiver()
        } catch (e: Exception) {
            _errorMessage.value = "Error initializing audio routing: ${e.message}"
            setAudioOutputToSpeaker()
        }
    }
    
    private fun startMonitoringAudioOutput() {
        updateJob = serviceScope.launch {
            while (true) {
                val newDevice = getCurrentRoutedDevice()
                if (userSelectedDeviceId == null || newDevice?.id == userSelectedDeviceId) {
                    _activeDevice.value = newDevice
                    Log.d("AudioDeviceManager", "Detected audio switch to: ${newDevice?.name}")
                }
                delay(3000)
            }
        }
    }
    
    private fun getCurrentRoutedDevice(): AudioDevice? {
        val routedDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val selected = routedDevices.find {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        return if (selected != null) {
            AudioDevice(
                id = selected.id.toString(),
                name = selected.productName?.toString() ?: "Bluetooth Device",
                type = when (selected.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceType.BLUETOOTH_HEADPHONES
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceType.WIRED_HEADPHONES
                    else -> AudioDeviceType.BLUETOOTH_HEADPHONES
                },
                isConnected = true,
                isActive = true,
                deviceInfo = selected
            )
        } else {
            AudioDevice(
                id = "speaker",
                name = "Internal Speaker",
                type = AudioDeviceType.SPEAKER,
                isConnected = true,
                isActive = true
            )
        }
    }
    
    fun updateAudioDevices() {
        Log.d("AudioDeviceManager", "updateAudioDevices: isEmulator=$isEmulator")
        val devices = mutableListOf<AudioDevice>()

        devices.add(
            AudioDevice(
                id = "speaker",
                name = "Internal Speaker",
                type = AudioDeviceType.SPEAKER,
                isConnected = true,
                isActive = false
            )
        )

        if (isEmulator) {
            _availableDevices.value = devices
            if (_activeDevice.value == null) {
                _activeDevice.value = devices.firstOrNull { it.type == AudioDeviceType.SPEAKER }
            }
            Log.d("AudioDeviceManager", "updateAudioDevices: devices=${_availableDevices.value}")
            return
        }

        try {
            val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            availableDevices.forEach { device ->
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    devices.add(
                        AudioDevice(
                            id = device.id.toString(),
                            name = device.productName?.toString() ?: "Unknown Device",
                            type = when (device.type) {
                                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceType.BLUETOOTH_HEADPHONES
                                AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceType.WIRED_HEADPHONES
                                else -> AudioDeviceType.BLUETOOTH_HEADPHONES
                            },
                            isConnected = true,
                            isActive = false,
                            deviceInfo = device
                        )
                    )
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error detecting audio devices: ${e.message}"
        }

        _availableDevices.value = devices

        val preferredId = mediaPlayer?.preferredDevice?.id
        val matchingDevice = devices.find { it.id == preferredId.toString() }

        val currentDevice = matchingDevice ?: devices.firstOrNull { it.type == AudioDeviceType.SPEAKER }
        _activeDevice.value = currentDevice

        _availableDevices.value = devices.map { device ->
            device.copy(isActive = device.id == currentDevice?.id)
        }

        Log.d("AudioDeviceManager", "updateAudioDevices: current=${_activeDevice.value?.name}")
    }
    
    fun selectAudioDevice(device: AudioDevice): Boolean {
        if (isEmulator) {
            _errorMessage.value = "Device selection not supported on emulator."
            return false
        }

        userSelectedDeviceId = device.id

        try {
            if (device.type == AudioDeviceType.SPEAKER) {
                setAudioOutputToSpeaker()
                return true
            } else {
                val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val targetDevice = availableDevices.find { it.id.toString() == device.id }
                if (targetDevice != null) {
                    Log.d("AudioDeviceManager", "Setting preferred device to: ${targetDevice.productName} (ID: ${targetDevice.id})")
                    
                    val wasPlaying = mediaPlayer?.isPlaying ?: false
                    Log.d("AudioDeviceManager", "MediaPlayer is currently playing: $wasPlaying")
                    
                    if (wasPlaying) {
                        val currentPosition = mediaPlayer?.currentPosition ?: 0
                        
                        mediaPlayer?.setPreferredDevice(targetDevice)
                        mediaPlayer?.pause()
                        serviceScope.launch {
                            delay(50)
                            
                            try {
                                mediaPlayer?.start()
                                
                                val newPosition = mediaPlayer?.currentPosition ?: 0
                                if (abs(newPosition - currentPosition) > 100) {
                                    mediaPlayer?.seekTo(currentPosition)
                                }
                                
                                Log.d("AudioDeviceManager", "Device switch completed with pause/resume")
                            } catch (e: Exception) {
                                Log.e("AudioDeviceManager", "Error during device switch resume", e)
                            }
                        }
                    } else {
                        mediaPlayer?.setPreferredDevice(targetDevice)
                        Log.d("AudioDeviceManager", "Device set for next playback")
                    }
                    
                    _activeDevice.value = device
                    
                    _availableDevices.value = _availableDevices.value.map { d ->
                        d.copy(isActive = d.id == device.id)
                    }
                    
                    _errorMessage.value = null
                    Log.d("AudioDeviceManager", "Successfully switched to device: ${device.name}")
                    return true
                } else {
                    _errorMessage.value = "Selected device not available"
                    setAudioOutputToSpeaker()
                    return false
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error selecting audio device: ${e.message}"
            setAudioOutputToSpeaker()
            return false
        }
    }
    
    private fun setAudioOutputToSpeaker() {
        try {
            Log.d("AudioDeviceManager", "Setting audio output to internal speaker")
            
            val wasPlaying = mediaPlayer?.isPlaying ?: false
            Log.d("AudioDeviceManager", "MediaPlayer is currently playing: $wasPlaying")
            
            mediaPlayer?.setPreferredDevice(null)
            audioManager.isSpeakerphoneOn = true
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            } catch (e: Exception) {
                Log.e("AudioDeviceManager", "Error stopping Bluetooth SCO", e)
            }
            
            val speakerDevice = _availableDevices.value.firstOrNull { it.type == AudioDeviceType.SPEAKER }
            _activeDevice.value = speakerDevice
            _availableDevices.value = _availableDevices.value.map { device ->
                device.copy(isActive = device.type == AudioDeviceType.SPEAKER)
            }
            
            Log.d("AudioDeviceManager", "Successfully switched to internal speaker")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error setting speaker output", e)
            _errorMessage.value = "Error setting speaker output: ${e.message}"
        }
    }
    
    private fun registerAudioDeviceReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            context.registerReceiver(audioDeviceReceiver, filter)
            Log.d("AudioDeviceManager", "Audio device receiver registered")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error registering audio device receiver", e)
        }
    }
    
    fun setMediaPlayer(player: MediaPlayer?) {
        this.mediaPlayer = player
        Log.d("AudioDeviceManager", "MediaPlayer set: ${player != null}")
    }
    

    
    fun requestAudioFocus(): Boolean {
        return true
    }
    
    fun abandonAudioFocus() {
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
            }
            
            Log.d("AudioDeviceManager", "Current app devices:")
            _availableDevices.value.forEach { device ->
                Log.d("AudioDeviceManager", "  ${device.name} (${device.id}) - Active: ${device.isActive}")
            }
            
            Log.d("AudioDeviceManager", "System state:")
            Log.d("AudioDeviceManager", "  Speakerphone: ${audioManager.isSpeakerphoneOn}")
            Log.d("AudioDeviceManager", "  Bluetooth SCO: ${audioManager.isBluetoothScoOn}")
            Log.d("AudioDeviceManager", "  Audio mode: ${audioManager.mode}")
            Log.d("AudioDeviceManager", "=== END DEBUG ===")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error in debug logging", e)
        }
    }
    
    fun startDeviceDiscovery() {
        updateAudioDevices()
    }
    
    fun switchToDevice(device: AudioDevice): Boolean {
        return selectAudioDevice(device)
    }
    
    fun cleanup() {
        try {
            updateJob?.cancel()
            updateJob = null
            
            try {
                context.unregisterReceiver(audioDeviceReceiver)
                Log.d("AudioDeviceManager", "Audio device receiver unregistered")
            } catch (e: Exception) {
                Log.e("AudioDeviceManager", "Error unregistering receiver", e)
            }
            
            abandonAudioFocus()
            Log.d("AudioDeviceManager", "Audio device manager cleaned up")
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error during cleanup", e)
        }
    }
} 