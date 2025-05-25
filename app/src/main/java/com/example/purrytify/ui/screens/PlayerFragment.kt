package com.example.purrytify.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.purrytify.R
import com.example.purrytify.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {
    private val musicViewModel: MusicViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.audioDeviceButton).setOnClickListener {
            musicViewModel.showAudioDeviceSelector()
        }

        val audioDeviceName = view.findViewById<TextView>(R.id.audioDeviceName)
        viewLifecycleOwner.lifecycleScope.launch {
            musicViewModel.currentAudioDevice.collectLatest { deviceName ->
                audioDeviceName.text = deviceName
            }
        }
    }
}