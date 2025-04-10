package com.example.purrytify.data.preferences

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.purrytify.data.api.ApiService
import com.example.purrytify.data.api.RefreshTokenRequest

class TokenManager(private val context: Context) {
    private val TAG = "TokenManager"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "purrytify_token_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    //region Network Layer and Retrofit

    private object RetrofitClient {
        private const val BASE_URL = "YOUR_BASE_URL" // Replace with your base URL

        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        private val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val instance: ApiService by lazy {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            retrofit.create(ApiService::class.java)
        }
    }



    //region JWT Helper

    private object JwtHelper {

        private const val JWT_SECRET = "YOUR_JWT_SECRET" // Replace with your JWT secret

        fun isTokenExpired(token: String): Boolean {
            return try {
                val claims = Jwts.parserBuilder()
                    .setSigningKey(JWT_SECRET.toByteArray())
                    .build()
                    .parseClaimsJws(token)
                    .body
                val expiration = claims.expiration
                expiration.before(Date())
            } catch (e: ExpiredJwtException) {
                true
            } catch (e: Exception) {
                // Token parsing failed
                false
            }
        }

        fun getExpirationTime(token: String): Date? {
            return try {
                val claims: Claims = Jwts.parserBuilder()
                    .setSigningKey(JWT_SECRET.toByteArray())
                    .build()
                    .parseClaimsJws(token)
                    .body
                claims.expiration
            } catch (e: Exception) {
                null // Return null if there is an error or the token is invalid
            }
        }

        // Add any other JWT helper methods if needed
    }

    //endregion

    fun saveToken(token: String?) {
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "Attempted to save null or empty token")
            return
        }
        Log.d(TAG, "Saving token: ${token.take(10)}...")
        sharedPrefs.edit().putString("jwt_token", token).apply()
    }

    fun saveRefreshToken(refreshToken: String?) {
        if (refreshToken.isNullOrEmpty()) {
            Log.w(TAG, "Attempted to save null or empty refresh token")
            return
        }
        Log.d(TAG, "Saving refresh token: ${refreshToken.take(10)}...")
        sharedPrefs.edit().putString("refreshToken", refreshToken).apply()
    }

    fun getToken(): String? {
        val token = sharedPrefs.getString("jwt_token", null)
        if (token != null) {
            Log.d(TAG, "Retrieved token: ${token.take(10)}...")
        } else {
            Log.d(TAG, "No token found")
        }
        return token
    }

    fun getRefreshToken(): String? {
        return sharedPrefs.getString("refreshToken", null)
    }

    fun clearTokens() {
        Log.d(TAG, "Clearing all tokens")
        sharedPrefs.edit().clear().apply()
    }

    fun hasToken(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    fun updateToken(newToken: String?, newRefreshToken: String?) {
        if (!newToken.isNullOrEmpty()) {
            saveToken(newToken)
        }

        if (!newRefreshToken.isNullOrEmpty()) {
            saveRefreshToken(newRefreshToken)
        }
    }

    //region Worker Management

    fun scheduleTokenRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val refreshRequest = PeriodicWorkRequest.Builder(
            TokenRefreshWorker::class.java,
            15, // Interval at least 15 Minutes
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(refreshRequest)
    }

    //endregion

    //region Worker Class

    class TokenRefreshWorker(appContext: Context, workerParams: WorkerParameters) :
        CoroutineWorker(appContext, workerParams) {

        private val tokenManager = TokenManager(appContext)
        private val apiService = RetrofitClient.instance // Use the existing ApiService instance

        override suspend fun doWork(): Result {
            return withContext(Dispatchers.IO) {
                val accessToken = tokenManager.getToken()
                val refreshToken = tokenManager.getRefreshToken()

                if (accessToken == null || refreshToken == null) {
                    // No tokens to refresh
                    return@withContext Result.failure()
                }
                // Check if token will expire in 1 minutes
                val expirationTime: Date? = JwtHelper.getExpirationTime(accessToken)
                val currentTime = Date()
                //check if token is valid
                val isTokenValid = verifyToken(accessToken)

                if (!isTokenValid) {
                    Log.i("Worker", "Token is invalid, refreshing...")
                    return@withContext tryRefreshToken(refreshToken)
                }else if (expirationTime != null && expirationTime.time - currentTime.time < 60000) {
                    Log.i("Worker", "Token will expire soon, refreshing...")
                    return@withContext tryRefreshToken(refreshToken)
                }else {
                    Log.i("Worker", "Token is still valid")
                    return@withContext Result.success()
                }
            }
        }

        private suspend fun verifyToken(token: String): Boolean {
            return try {
                val response = apiService.verifyToken("Bearer $token")
                if (response.isSuccessful) {
                    // Token is valid
                    Log.d("Worker", "Token verification successful.")
                    true
                } else if (response.code() == 403) {
                    // Token is expired
                    Log.d("Worker", "Token verification failed (403 Forbidden).")
                    false
                } else {
                    // Other errors
                    Log.e("Worker", "Token verification failed with code: ${response.code()}")
                    false
                }
            } catch (e: Exception) {
                Log.e("Worker", "Error verifying token: ${e.message}")
                false
            }
        }

        private suspend fun tryRefreshToken(refreshToken: String): Result {
            try {
                val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))
                if (response.isSuccessful) {
                    val newTokens = response.body()
                    if (newTokens != null) {
                        tokenManager.updateToken(newTokens.token, newTokens.refreshToken)
                        Log.i("Worker", "Token refreshed successfully")
                        return Result.success()
                    }
                }
                Log.i("Worker", "Failed to refresh token: ${response.code()}")
                return Result.failure()
            } catch (e: Exception) {
                Log.e("Worker", "Error refreshing token: ${e.message}")
                return Result.failure()
            }
        }
    }
}