package com.example.purrytify.service

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class TokenRefreshManager(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun initiateRefreshCycle() {
        // Run only when connected to the internet
        val refreshConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Task will repeat every 3 minutes
        val periodicTokenWork = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
            3, TimeUnit.MINUTES
        )
            .setConstraints(refreshConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                45, TimeUnit.SECONDS
            )
            .build()

        // Replace existing task with this new one if already scheduled
        workManager.enqueueUniquePeriodicWork(
            WORK_ID_TOKEN_REFRESH,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicTokenWork
        )
    }

    fun haltRefreshCycle() {
        // Stops the token refresh cycle if user logs out or token is no longer needed
        workManager.cancelUniqueWork(WORK_ID_TOKEN_REFRESH)
    }

    companion object {
        private const val WORK_ID_TOKEN_REFRESH = "token_cycle_worker"
    }
}
