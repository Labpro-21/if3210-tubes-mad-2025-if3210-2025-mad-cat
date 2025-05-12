package com.example.purrytify.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.repository.TrendingRepository
import com.example.purrytify.di.NetworkModule

class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val okHttpClient = NetworkModule.provideOkHttpClient(context)
            val retrofit = NetworkModule.provideRetrofit(okHttpClient)
            val trendingApiService = NetworkModule.provideTrendingApiService(retrofit)
            val trendingRepository = TrendingRepository(trendingApiService)
            return HomeViewModel(trendingRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
