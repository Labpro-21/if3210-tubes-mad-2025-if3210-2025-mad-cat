package com.example.purrytify.data.api

import com.example.purrytify.data.model.OnlineSong
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Response

interface TrendingApiService {
    @GET("api/top-songs/global")
    suspend fun getTopGlobalSongs(): Response<List<OnlineSong>>
    
    @GET("api/top-songs/{country_code}")
    suspend fun getTopCountrySongs(
        @Path("country_code") countryCode: String
    ): Response<List<OnlineSong>>
    
    @GET("api/song/{song_id}")
    suspend fun getSongById(
        @Path("song_id") songId: Int
    ): Response<OnlineSong>
}
