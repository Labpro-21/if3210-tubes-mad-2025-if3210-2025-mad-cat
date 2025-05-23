package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.purrytify.service.AudioDevice
import com.example.purrytify.service.AudioDeviceManager
import com.example.purrytify.service.AudioDeviceType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDeviceBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    
    var isScanning by remember { mutableStateOf(true) }
    val deviceManager = remember { AudioDeviceManager(context) }
    val devices by deviceManager.availableDevices.collectAsState()
    val activeDevice by deviceManager.activeDevice.collectAsState()
    
    LaunchedEffect(Unit) {
        deviceManager.startDeviceDiscovery()
        // Stop scanning after a few seconds
        kotlinx.coroutines.delay(5000)
        isScanning = false
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Output Device",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF1DB954)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Scanning for devices...")
                }
            }
            
            LazyColumn {
                items(devices) { device ->
                    DeviceListItem(
                        device = device,
                        isActive = device.id == activeDevice?.id,
                        onClick = {
                            deviceManager.switchToDevice(device)
                            scope.launch {
                                kotlinx.coroutines.delay(300)
                                onDismiss()
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DeviceListItem(
    device: AudioDevice,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Icon(
            imageVector = getDeviceIcon(device.type),
            contentDescription = null,
            tint = if (isActive) Color(0xFF1DB954) else Color.White,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = device.name,
                fontSize = 16.sp,
                color = if (isActive) Color(0xFF1DB954) else Color.White
            )
            
            Text(
                text = when {
                    isActive -> "Connected • Active"
                    device.isConnected -> "Connected"
                    else -> "Available"
                },
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        
        RadioButton(
            selected = isActive,
            onClick = null
        )
    }
    
    Divider(
        color = Color.DarkGray.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

@Composable
fun getDeviceIcon(type: AudioDeviceType): ImageVector {
    return when (type) {
        AudioDeviceType.BLUETOOTH_DEVICE -> Icons.Filled.Headphones
        AudioDeviceType.INTERNAL_SPEAKER -> Icons.Filled.Speaker
        AudioDeviceType.WIRED_HEADSET -> Icons.Filled.Headset
        AudioDeviceType.USB_DEVICE -> Icons.Filled.Usb
    }
}