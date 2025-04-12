package com.example.purrytify.data.api

import android.content.Context
import android.util.Log
import com.example.purrytify.data.preferences.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(private val context: Context) : Interceptor {
    private val tokenManager = TokenManager(context)
    
    // Use a global lock to prevent multiple refresh attempts simultaneously
    companion object {
        private val lock = Any()
        private var isRefreshing = false
        private var lastRefreshAttempt = 0L
        private var lastVerifyAttempt = 0L
        private var isSessionActive = true
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip auth for login and refresh token requests
        if (originalRequest.url.encodedPath.contains("login") || 
            originalRequest.url.encodedPath.contains("refresh-token")) {
            return chain.proceed(originalRequest)
        }

        if (!isSessionActive) {
            val token = tokenManager.getToken()
            if (!token.isNullOrEmpty()) {
                isSessionActive = true
            } else {
                return chain.proceed(originalRequest)
            }
        }

        verifyTokenIfNeeded()

        val requestWithAuth = addAuthHeader(originalRequest)
        var response = chain.proceed(requestWithAuth)

        if (response.code == 401) {
            Log.d("AuthInterceptor", "Received 401, attempting to refresh token")
            
            synchronized(lock) {
                if (!isRefreshing) {
                    isRefreshing = true

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastRefreshAttempt > 10000) { // 10 seconds between attempts
                        lastRefreshAttempt = currentTime
                        
                        val refreshSuccess = refreshToken()
                        if (refreshSuccess) {
                            // Retry request with new token
                            response.close()
                            val newRequestWithAuth = addAuthHeader(originalRequest)
                            val newResponse = chain.proceed(newRequestWithAuth)
                            Log.d("AuthInterceptor", "Retried request with new token, status: ${newResponse.code}")
                            isRefreshing = false
                            return newResponse
                        } else {
                            // If refresh failed, mark session as inactive
                            isSessionActive = false
                            Log.d("AuthInterceptor", "Token refresh failed, marking session as inactive")
                        }
                    } else {
                        Log.d("AuthInterceptor", "Skipping refresh attempt, too soon since last attempt")
                    }
                    isRefreshing = false
                } else {
                    Log.d("AuthInterceptor", "Another thread is already refreshing the token")
                }
            }
        }
        
        return response
    }
    
    private fun addAuthHeader(request: Request): Request {
        val token = tokenManager.getToken()
        return if (!token.isNullOrEmpty()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
    }
    
    private fun verifyTokenIfNeeded() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVerifyAttempt < 180000) {
            return
        }
        
        synchronized(lock) {
            if (currentTime - lastVerifyAttempt < 180000) {
                return
            }
            
            lastVerifyAttempt = currentTime
            val token = tokenManager.getToken() ?: return
            
            try {
                runBlocking {
                    val client = RetrofitClient.getRefreshClient()

                    val response = client.getProfile()
                    
                    if (response.isSuccessful) {
                        Log.d("AuthInterceptor", "Token verified successfully via profile endpoint")
                        isSessionActive = true
                    } else {
                        if (response.code() == 401) {
                            Log.d("AuthInterceptor", "Token verification failed, attempting refresh")
                            refreshToken()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthInterceptor", "Error verifying token", e)
            }
        }
    }
    
    private fun refreshToken(): Boolean {
        val refreshToken = tokenManager.getRefreshToken() ?: return false
        
        return runBlocking {
            try {
                val client = RetrofitClient.getRefreshClient()
                
                val refreshRequest = RefreshTokenRequest(refreshToken)
                val response = client.refreshToken(refreshRequest)
                
                if (response.isSuccessful) {
                    response.body()?.let { refreshResponse ->
                        Log.d("AuthInterceptor", "Token refresh successful")

                        tokenManager.updateToken(refreshResponse.accessToken, refreshResponse.refreshToken)
                        isSessionActive = true
                        return@runBlocking true
                    } ?: run {
                        Log.e("AuthInterceptor", "Token refresh response body was null")
                        return@runBlocking false
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("AuthInterceptor", "Token refresh failed: ${response.code()}, Error: $errorBody")

                    if (response.code() == 403) {
                        Log.d("AuthInterceptor", "Refresh token is invalid, clearing tokens")
                        tokenManager.clearTokens()
                        isSessionActive = false
                    }
                    
                    return@runBlocking false
                }
            } catch (e: Exception) {
                Log.e("AuthInterceptor", "Exception refreshing token", e)
                return@runBlocking false
            }
        }
    }
}