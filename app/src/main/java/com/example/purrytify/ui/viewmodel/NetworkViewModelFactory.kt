package com.example.purrytify.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.network.ConnectivityObserver

class NetworkViewModelFactory(
    private val connectivityObserver: ConnectivityObserver
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NetworkViewModel(connectivityObserver) as T
    }
}