package com.example.purrytify.service.auth

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.purrytify.data.api.RefreshTokenRequest
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.preferences.TokenManager
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class TokenRefreshService : Service() {
    private val TAG = "TokenRefreshService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var refreshJob: Job? = null
    private lateinit var tokenManager: TokenManager
    
    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        Log.d(TAG, "TokenRefreshService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TokenRefreshService started")
        startTokenRefreshLoop()
        return START_STICKY
    }
    
    private fun startTokenRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            while (isActive) {
                try {
                    val token = tokenManager.getToken()
                    if (!token.isNullOrEmpty()) {
                        val isExpired = checkTokenExpiration(token)
                        if (isExpired) {
                            Log.d(TAG, "Token is expired, attempting refresh")
                            refreshToken()
                        } else {
                            Log.d(TAG, "Token is still valid")
                        }
                    } else {
                        Log.d(TAG, "No token found, stopping service")
                        stopSelf()
                        break
                    }
                    
                    delay(TimeUnit.MINUTES.toMillis(3))
                } catch (e: Exception) {
                    Log.e(TAG, "Error in token refresh loop", e)
                    delay(TimeUnit.MINUTES.toMillis(1))
                }
            }
        }
    }
    
    private suspend fun checkTokenExpiration(token: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = RetrofitClient.getRefreshClient()
                val response = client.verifyToken("Bearer $token")
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Token verification successful")
                    false
                } else {
                    Log.d(TAG, "Token verification failed with code: ${response.code()}")
                    response.code() == 403 || response.code() == 401
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying token", e)
                false
            }
        }
    }
    
    private suspend fun refreshToken() {
        withContext(Dispatchers.IO) {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isNullOrEmpty()) {
                Log.e(TAG, "No refresh token available, clearing tokens")
                tokenManager.clearTokens()
                stopSelf()
                return@withContext
            }
            
            try {
                val client = RetrofitClient.getRefreshClient()
                val response = client.refreshToken(RefreshTokenRequest(refreshToken))
                
                if (response.isSuccessful) {
                    response.body()?.let { refreshResponse ->
                        tokenManager.updateToken(
                            refreshResponse.accessToken,
                            refreshResponse.refreshToken
                        )
                        Log.d(TAG, "Token refreshed successfully")
                    }
                } else {
                    Log.e(TAG, "Token refresh failed: ${response.code()}")
                    if (response.code() == 401 || response.code() == 403) {
                        tokenManager.clearTokens()
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during token refresh", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        refreshJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "TokenRefreshService destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
