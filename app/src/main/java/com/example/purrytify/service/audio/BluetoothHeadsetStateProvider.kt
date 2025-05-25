package com.example.purrytify.service.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothHeadsetStateProvider(
    private val context: Context,
    private val bluetoothManager: BluetoothManager
) {
    private val _isHeadsetConnected = MutableStateFlow(getHeadsetState())
    val isHeadsetConnected: StateFlow<Boolean> = _isHeadsetConnected.asStateFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    val profile = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, -1)
                    
                    if (profile == BluetoothProfile.HEADSET || profile == BluetoothProfile.A2DP) {
                        when (state) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.d("BluetoothHeadsetStateProvider", "Bluetooth audio device connected")
                                _isHeadsetConnected.value = true
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.d("BluetoothHeadsetStateProvider", "Bluetooth audio device disconnected")
                                _isHeadsetConnected.value = getHeadsetState()
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        
        try {
            context.registerReceiver(bluetoothReceiver, filter)
            
            val adapter = bluetoothManager.adapter
            if (adapter != null && hasBluetoothPermission()) {
                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (profile == BluetoothProfile.HEADSET || profile == BluetoothProfile.A2DP) {
                            _isHeadsetConnected.value = getHeadsetState()
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == BluetoothProfile.HEADSET || profile == BluetoothProfile.A2DP) {
                            _isHeadsetConnected.value = getHeadsetState()
                        }
                    }
                }, BluetoothProfile.HEADSET)
                
                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (profile == BluetoothProfile.A2DP) {
                            _isHeadsetConnected.value = getHeadsetState()
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == BluetoothProfile.A2DP) {
                            _isHeadsetConnected.value = getHeadsetState()
                        }
                    }
                }, BluetoothProfile.A2DP)
            }
            
            Log.d("BluetoothHeadsetStateProvider", "Bluetooth headset state provider initialized")
        } catch (e: Exception) {
            Log.e("BluetoothHeadsetStateProvider", "Error initializing provider", e)
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getHeadsetState(): Boolean {
        return try {
            val adapter = bluetoothManager.adapter
            if (adapter == null || !hasBluetoothPermission()) {
                return false
            }
            
            val headsetConnected = adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
            val a2dpConnected = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
            
            headsetConnected || a2dpConnected
        } catch (e: Exception) {
            Log.e("BluetoothHeadsetStateProvider", "Error getting Bluetooth headset state", e)
            false
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
            Log.d("BluetoothHeadsetStateProvider", "Bluetooth headset state provider cleaned up")
        } catch (e: Exception) {
            Log.e("BluetoothHeadsetStateProvider", "Error unregistering receiver", e)
        }
    }
} 