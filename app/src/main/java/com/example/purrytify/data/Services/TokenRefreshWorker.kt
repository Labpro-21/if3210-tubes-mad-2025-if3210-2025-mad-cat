package com.example.purrytify.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenRefreshWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sessionManager by lazy { TokenManager(context) }
    private val authRepo by lazy { UserRepository(sessionManager) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(LOG_TAG, "Token refresh task initiated.")

        return@withContext executeTokenCheck()
    }

    private suspend fun executeTokenCheck(): Result {
        if (!sessionManager.isLoggedIn()) {
            Log.i(LOG_TAG, "No user session detected. Skipping token validation.")
            return Result.success()
        }

        return try {
            val valid = authRepo.verifyToken()

            if (valid) {
                Log.i(LOG_TAG, "Token is active. No refresh needed.")
                Result.success()
            } else {
                handleTokenExpiration()
            }

        } catch (error: Exception) {
            Log.w(LOG_TAG, "Exception during token verification: ${error.message}", error)
            Result.retry()
        }
    }

    private suspend fun handleTokenExpiration(): Result {
        Log.i(LOG_TAG, "Token appears to be expired. Attempting to refresh...")

        val response = authRepo.refreshToken()

        return if (response.isSuccess) {
            Log.i(LOG_TAG, "Token successfully updated.")
            Result.success()
        } else {
            Log.e(LOG_TAG, "Unable to refresh token. Logging out user.")
            sessionManager.clearTokens()
            Result.failure()
        }
    }

    companion object {
        private const val LOG_TAG = "Worker-TokenLifecycle"
    }
}
