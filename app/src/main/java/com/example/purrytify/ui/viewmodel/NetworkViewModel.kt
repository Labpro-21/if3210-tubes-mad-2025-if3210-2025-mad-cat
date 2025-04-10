package com.example.purrytify.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.network.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetworkViewModel(
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _status = MutableStateFlow(ConnectivityObserver.Status.Unavailable)
    val status: StateFlow<ConnectivityObserver.Status> = _status

    init {
        viewModelScope.launch {
            connectivityObserver.observe().collectLatest {
                _status.value = it
            }
        }
    }
}