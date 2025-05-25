package com.example.purrytify.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.purrytify.data.api.RefreshTokenRequest
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.preferences.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class TokenAutoRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val tokenManager = TokenManager(context)
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("TokenWorker", "TokenAutoRefreshWorker started")
        
        val token = tokenManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.d("TokenWorker", "No token found. Skipping refresh.")
            return@withContext Result.success()
        }

        val isTokenValid = checkTokenValidity(token)
        if (isTokenValid) {
            Log.d("TokenWorker", "Token is still valid. No refresh needed.")
            return@withContext Result.success()
        }

        Log.d("TokenWorker", "Token is expired. Attempting to refresh token")
        val refreshToken = tokenManager.getRefreshToken()

        if (refreshToken.isNullOrEmpty()) {
            Log.d("TokenWorker", "No refresh token available. Logging out user.")
            tokenManager.clearTokens()
            return@withContext Result.success()
        } 
        
        try {
            val response = RetrofitClient.apiService.refreshToken(
                RefreshTokenRequest(refreshToken)
            )
            
            if (response.isSuccessful) {
                response.body()?.let { refreshResponse ->
                    val newToken = refreshResponse.accessToken
                    val newRefreshToken = refreshResponse.refreshToken

                    tokenManager.updateToken(newToken, newRefreshToken)
                    Log.d("TokenWorker", "Token refreshed successfully")
                    return@withContext Result.success()
                } ?: run {
                    Log.e("TokenWorker", "Refresh response body was null")
                    return@withContext Result.retry()
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e("TokenWorker", "Token refresh failed: ${response.code()}, Error: $errorBody")
                
                if (response.code() == 401) {
                    tokenManager.clearTokens()
                    return@withContext Result.success()
                }
                
                return@withContext Result.retry()
            }
        } catch (e: Exception) {
            Log.e("TokenWorker", "Exception during token refresh: ${e.message}")
            return@withContext Result.retry()
        }
    }
    
    private suspend fun checkTokenValidity(token: String): Boolean {
        return try {
            val client = RetrofitClient.getRefreshClient()
            val response = client.verifyToken("Bearer $token")
            
            if (response.isSuccessful) {
                Log.d("TokenWorker", "Token is valid")
                true
            } else {
                Log.d("TokenWorker", "Token verification failed with code: ${response.code()}")
                response.code() != 403 && response.code() != 401
            }
        } catch (e: Exception) {
            Log.e("TokenWorker", "Error checking token validity: ${e.message}")
            true
        }
    }
}
