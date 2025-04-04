package com.example.purrytify.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String?,
    val refreshToken: String?
)

interface ApiService {
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}