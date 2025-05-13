package com.example.purrytify.data.repository

import com.example.purrytify.data.api.TrendingApiService
import com.example.purrytify.data.model.OnlineSong

class TrendingRepository(
    private val trendingApiService: TrendingApiService
) {
    suspend fun getTopGlobalSongs(): Result<List<OnlineSong>> {
        return try {
            val response = trendingApiService.getTopGlobalSongs()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch global top songs: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getTopCountrySongs(countryCode: String): Result<List<OnlineSong>> {
        return try {
            val response = trendingApiService.getTopCountrySongs(countryCode)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch country top songs: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getSupportedCountries(): Map<String, String> {
        return mapOf(
            "ID" to "Indonesia",
            "MY" to "Malaysia",
            "US" to "USA",
            "GB" to "UK",
            "CH" to "Switzerland",
            "DE" to "Germany",
            "BR" to "Brazil"
        )
    }
}
