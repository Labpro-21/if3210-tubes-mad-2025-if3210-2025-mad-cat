package com.example.purrytify.data.api
import com.example.purrytify.data.models.ProfileResponse

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String?,
    val refreshToken: String?
)

interface ApiService {
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/profile")
    suspend fun getProfile(): Response<ProfileResponse>

    @POST("api/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    // The verify-token endpoint doesn't exist in the API, so use profile endpoint as a token check
    @GET("api/profile")
    suspend fun verifyToken(): Response<Any>
    
    @Multipart
    @PUT("api/profile")
    suspend fun updateProfile(
        @Part("location") location: RequestBody,
        @Part profilePhoto: MultipartBody.Part?
    ): Response<ProfileResponse>
}

data class RefreshTokenRequest(val refreshToken: String)

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String
)