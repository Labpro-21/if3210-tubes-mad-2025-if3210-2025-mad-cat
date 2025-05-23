package com.example.purrytify.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.purrytify.R
import com.example.purrytify.service.AudioDevice
import com.example.purrytify.service.AudioDeviceManager
import com.example.purrytify.service.AudioDeviceType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AudioDeviceBottomSheet : BottomSheetDialogFragment() {
    private lateinit var deviceManager: AudioDeviceManager
    private lateinit var deviceAdapter: AudioDeviceAdapter
    private lateinit var deviceRecyclerView: RecyclerView
    private lateinit var scanningProgress: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_audio_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceManager = AudioDeviceManager(requireContext())
        deviceRecyclerView = view.findViewById(R.id.deviceRecyclerView)
        scanningProgress = view.findViewById(R.id.scanningProgress)

        setupDeviceList()
        startDeviceDiscovery()
    }

    private fun setupDeviceList() {
        deviceAdapter = AudioDeviceAdapter { device ->
            deviceManager.switchToDevice(device)
            dismiss()
        }
        deviceRecyclerView.adapter = deviceAdapter

        // Observe available devices
        viewLifecycleOwner.lifecycleScope.launch {
            deviceManager.availableDevices.collectLatest { devices ->
                deviceAdapter.submitList(devices)
            }
        }

        // Observe active device
        viewLifecycleOwner.lifecycleScope.launch {
            deviceManager.activeDevice.collectLatest { activeDevice ->
                deviceAdapter.setActiveDevice(activeDevice)
            }
        }
    }

    private fun startDeviceDiscovery() {
        scanningProgress.visibility = View.VISIBLE
        deviceManager.startDeviceDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceManager.cleanup()
    }

    private class AudioDeviceAdapter(
        private val onDeviceSelected: (AudioDevice) -> Unit
    ) : RecyclerView.Adapter<AudioDeviceAdapter.ViewHolder>() {

        private var devices: List<AudioDevice> = emptyList()
        private var activeDevice: AudioDevice? = null

        fun submitList(newDevices: List<AudioDevice>) {
            devices = newDevices
            notifyDataSetChanged()
        }

        fun setActiveDevice(device: AudioDevice?) {
            activeDevice = device
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_audio_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.bind(device, device.id == activeDevice?.id)
            holder.itemView.setOnClickListener { onDeviceSelected(device) }
        }

        override fun getItemCount(): Int = devices.size

        private class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val deviceIcon: ImageView = view.findViewById(R.id.deviceIcon)
            private val deviceName: TextView = view.findViewById(R.id.deviceName)
            private val deviceStatus: TextView = view.findViewById(R.id.deviceStatus)
            private val deviceSelected: RadioButton = view.findViewById(R.id.deviceSelected)

            fun bind(device: AudioDevice, isActive: Boolean) {
                deviceName.text = device.name
                deviceSelected.isChecked = isActive

                // Set icon based on device type
                deviceIcon.setImageResource(when (device.type) {
                    AudioDeviceType.BLUETOOTH_DEVICE -> R.drawable.ic_bluetooth_audio
                    AudioDeviceType.INTERNAL_SPEAKER -> R.drawable.ic_speaker
                    AudioDeviceType.WIRED_HEADSET -> R.drawable.ic_headset
                    AudioDeviceType.USB_DEVICE -> R.drawable.ic_usb
                })

                // Set status text
                deviceStatus.text = when {
                    isActive -> "Connected • Active"
                    device.isConnected -> "Connected"
                    else -> "Available"
                }
            }
        }
    }
}