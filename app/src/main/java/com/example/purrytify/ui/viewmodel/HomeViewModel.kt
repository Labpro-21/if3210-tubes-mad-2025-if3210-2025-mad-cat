package com.example.purrytify.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.model.OnlineSong
import com.example.purrytify.data.repository.TrendingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val trendingRepository: TrendingRepository
) : ViewModel() {
    
    private val _globalTopSongs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val globalTopSongs: StateFlow<List<OnlineSong>> = _globalTopSongs.asStateFlow()
    
    private val _countryTopSongs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val countryTopSongs: StateFlow<List<OnlineSong>> = _countryTopSongs.asStateFlow()
    
    private val _isLoadingGlobal = MutableStateFlow(false)
    val isLoadingGlobal: StateFlow<Boolean> = _isLoadingGlobal.asStateFlow()
    
    private val _isLoadingCountry = MutableStateFlow(false)
    val isLoadingCountry: StateFlow<Boolean> = _isLoadingCountry.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    init {
        fetchGlobalTopSongs()
    }
    
    fun fetchGlobalTopSongs() {
        viewModelScope.launch {
            _isLoadingGlobal.value = true
            _errorMessage.value = null
            
            trendingRepository.getTopGlobalSongs()
                .onSuccess { songs ->
                    _globalTopSongs.value = songs
                }
                .onFailure { exception ->
                    _errorMessage.value = "Failed to load global top songs: ${exception.message}"
                }
            
            _isLoadingGlobal.value = false
        }
    }
    
    fun fetchCountryTopSongs(countryCode: String) {
        viewModelScope.launch {
            _isLoadingCountry.value = true
            _errorMessage.value = null
            
            trendingRepository.getTopCountrySongs(countryCode)
                .onSuccess { songs ->
                    _countryTopSongs.value = songs
                }
                .onFailure { exception ->
                    _errorMessage.value = "Failed to load country top songs: ${exception.message}"
                    _countryTopSongs.value = emptyList()
                }
            
            _isLoadingCountry.value = false
        }
    }
    
    fun isCountrySupported(countryCode: String): Boolean {
        return trendingRepository.getSupportedCountries().containsKey(countryCode)
    }
    
    fun getSupportedCountries(): Map<String, String> {
        return trendingRepository.getSupportedCountries()
    }
}
