package com.example.purrytify.data.repository

import com.example.purrytify.data.api.ApiService
import com.example.purrytify.data.api.LoginRequest
import com.example.purrytify.data.api.LoginResponse
import com.example.purrytify.data.api.RefreshTokenRequest
import com.example.purrytify.data.api.RefreshTokenResponse
import com.example.purrytify.data.models.ProfileResponse

class AuthRepository(private val apiService: ApiService) {

    suspend fun login(isConnected: Boolean, request: LoginRequest): Result<LoginResponse> {
        return safeApiCall(isConnected) {
            val response = apiService.login(request)
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty body")
            } else {
                throw Exception("Login failed: ${response.code()}")
            }
        }
    }

    suspend fun getProfile(isConnected: Boolean, token: String): Result<ProfileResponse> {
        return safeApiCall(isConnected) {
            val response = apiService.getProfile("Bearer $token")
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty profile")
            } else {
                throw Exception("Profile failed: ${response.code()}")
            }
        }
    }

    suspend fun refreshToken(isConnected: Boolean, request: RefreshTokenRequest): Result<RefreshTokenResponse> {
        return safeApiCall(isConnected) {
            val response = apiService.refreshToken(request)
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty refresh")
            } else {
                throw Exception("Refresh failed: ${response.code()}")
            }
        }
    }

    suspend fun verifyToken(isConnected: Boolean, token: String): Result<Any> {
        return safeApiCall(isConnected) {
            val response = apiService.verifyToken("Bearer $token")
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Empty verify")
            } else {
                throw Exception("Verify failed: ${response.code()}")
            }
        }
    }

    private suspend fun <T> safeApiCall(
        isConnected: Boolean,
        apiCall: suspend () -> T
    ): Result<T> {
        return if (isConnected) {
            try {
                Result.success(apiCall())
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("No Internet Connection"))
        }
    }
}
