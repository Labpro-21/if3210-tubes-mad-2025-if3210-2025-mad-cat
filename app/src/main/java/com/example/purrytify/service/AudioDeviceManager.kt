package com.example.purrytify.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isConnected: Boolean = false,
    val isActive: Boolean = false
)

enum class AudioDeviceType {
    INTERNAL_SPEAKER,
    BLUETOOTH_DEVICE,
    WIRED_HEADSET,
    USB_DEVICE
}

class AudioDeviceManager(private val context: Context) {
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager: BluetoothManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(BluetoothManager::class.java)
    } else {
        null
    }
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _availableDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioDevice>> = _availableDevices

    private val _activeDevice = MutableStateFlow<AudioDevice?>(null)
    val activeDevice: StateFlow<AudioDevice?> = _activeDevice

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (context?.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            handleBluetoothDeviceFound(intent)
                        }
                    } else {
                        handleBluetoothDeviceFound(intent)
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> updateConnectedDevices()
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> updateConnectedDevices()
                AudioManager.ACTION_HEADSET_PLUG -> updateConnectedDevices()
            }
        }
    }

    init {
        registerReceivers()
        updateConnectedDevices()
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    fun startDeviceDiscovery() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter?.startDiscovery()
                }
            } else {
                bluetoothAdapter?.startDiscovery()
            }
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error starting device discovery", e)
        }
    }

    private fun handleBluetoothDeviceFound(intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        device?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    addBluetoothDevice(it)
                }
            } else {
                addBluetoothDevice(it)
            }
        }
    }

    private fun addBluetoothDevice(device: BluetoothDevice) {
        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                device.name ?: "Unknown Device"
            } else {
                "Unknown Device"
            }
        } else {
            @Suppress("DEPRECATION")
            device.name ?: "Unknown Device"
        }

        val newDevice = AudioDevice(
            id = device.address,
            name = deviceName,
            type = AudioDeviceType.BLUETOOTH_DEVICE,
            isConnected = device.bondState == BluetoothDevice.BOND_BONDED
        )

        val currentDevices = _availableDevices.value.toMutableList()
        if (!currentDevices.any { it.id == newDevice.id }) {
            currentDevices.add(newDevice)
            _availableDevices.value = currentDevices
        }
    }

    private fun updateConnectedDevices() {
        val devices = mutableListOf<AudioDevice>()

        // Add internal speaker
        devices.add(AudioDevice(
            id = "internal_speaker",
            name = "Internal Speaker",
            type = AudioDeviceType.INTERNAL_SPEAKER,
            isConnected = true,
            isActive = isInternalSpeakerActive()
        ))

        // Add wired devices
        if (audioManager.isWiredHeadsetOn) {
            devices.add(AudioDevice(
                id = "wired_headset",
                name = "Wired Headset",
                type = AudioDeviceType.WIRED_HEADSET,
                isConnected = true,
                isActive = !isInternalSpeakerActive()
            ))
        }

        // Add Bluetooth devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                addConnectedBluetoothDevices(devices)
            }
        } else {
            addConnectedBluetoothDevices(devices)
        }

        _availableDevices.value = devices
        updateActiveDevice(devices)
    }

    private fun addConnectedBluetoothDevices(devices: MutableList<AudioDevice>) {
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    device.name ?: "Unknown Device"
                } else {
                    "Unknown Device"
                }
            } else {
                @Suppress("DEPRECATION")
                device.name ?: "Unknown Device"
            }

            devices.add(AudioDevice(
                id = device.address,
                name = deviceName,
                type = AudioDeviceType.BLUETOOTH_DEVICE,
                isConnected = true,
                isActive = isBluetoothDeviceActive(device)
            ))
        }
    }

    private fun isInternalSpeakerActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && it.isSink }
        }
        return !audioManager.isWiredHeadsetOn && !audioManager.isBluetoothA2dpOn
    }

    private fun isBluetoothDeviceActive(device: BluetoothDevice): Boolean {
        return try {
            val a2dpProfile = bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    // Not needed for this check
                }

                override fun onServiceDisconnected(profile: Int) {
                    // Not needed for this check
                }
            }, BluetoothProfile.A2DP)

            a2dpProfile == true
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error checking Bluetooth device status", e)
            false
        }
    }

    private fun updateActiveDevice(devices: List<AudioDevice>) {
        val active = devices.find { it.isActive }
        _activeDevice.value = active ?: devices.firstOrNull()
    }

    fun switchToDevice(device: AudioDevice) {
        when (device.type) {
            AudioDeviceType.BLUETOOTH_DEVICE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        switchToBluetoothDevice(device)
                    }
                } else {
                    switchToBluetoothDevice(device)
                }
            }
            AudioDeviceType.INTERNAL_SPEAKER -> {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                updateConnectedDevices()
            }
            else -> {
                // Handle other device types if needed
                updateConnectedDevices()
            }
        }
    }

    private fun switchToBluetoothDevice(device: AudioDevice) {
        try {
            bluetoothAdapter?.bondedDevices?.find { it.address == device.id }?.let { btDevice ->
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                // The system will automatically route audio to the Bluetooth device
                updateConnectedDevices()
            }
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error switching to Bluetooth device", e)
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {
            Log.e("AudioDeviceManager", "Error during cleanup", e)
        }
    }
}