// Additional updates for MediaPlaybackService.kt to handle timeout and cleanup
// Add these improvements to your existing service:

package com.example.purrytify.service

// Add this at the class level (after the class declaration):
    private var serviceStartTime: Long = 0
    private val SERVICE_TIMEOUT = 30 * 60 * 1000L // 30 minutes timeout
    private var timeoutCheckJob: Job? = null

// Add this method to check for service timeout:
    private fun startTimeoutCheck() {
        timeoutCheckJob?.cancel()
        timeoutCheckJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(60000) // Check every minute
                if (!isPlaying && System.currentTimeMillis() - serviceStartTime > SERVICE_TIMEOUT) {
                    Log.d("MediaPlaybackService", "Service idle timeout reached, stopping service")
                    stopSelf()
                }
            }
        }
    }

// Update the onCreate method to include timeout check:
    override fun onCreate() {
        super.onCreate()
        
        serviceStartTime = System.currentTimeMillis()
        
        try {
            // Initialize media session
            mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession").apply {
                setCallback(mediaSessionCallback)
                isActive = true
            }
            
            // Initialize notification manager
            notificationManager = NotificationManager(this)
            
            // Start timeout check
            startTimeoutCheck()
            
            Log.d("MediaPlaybackService", "Service created successfully")
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error in onCreate", e)
        }
    }

// Update the onStartCommand to handle ACTION_STOP properly:
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        Log.d("MediaPlaybackService", "onStartCommand, action: ${intent?.action}")
        
        when (intent?.action) {
            "ACTION_STOP" -> {
                Log.d("MediaPlaybackService", "Received ACTION_STOP, cleaning up")
                try {
                    // Stop media playback
                    mediaPlayer?.apply {
                        if (isPlaying) {
                            stop()
                        }
                        release()
                    }
                    mediaPlayer = null
                    
                    // Update state
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                    
                    // Stop foreground service and remove notification
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    
                    // Release media session
                    mediaSession.isActive = false
                    mediaSession.release()
                    
                    // Cancel all jobs
                    updateJob?.cancel()
                    timeoutCheckJob?.cancel()
                    
                    // Finally stop the service
                    stopSelf()
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Error in ACTION_STOP", e)
                    stopSelf() // Ensure service stops even if there's an error
                }
                return START_NOT_STICKY
            }
            // ... rest of the cases
        }
        
        return START_STICKY
    }

// Update the onDestroy method:
    override fun onDestroy() {
        try {
            // Cancel all jobs
            updateJob?.cancel()
            timeoutCheckJob?.cancel()
            
            // Release media player
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Release media session
            mediaSession.isActive = false
            mediaSession.release()
            
            // Remove notification
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error in onDestroy", e)
        }
        
        super.onDestroy()
    }

// Add a method to handle app trimming memory (for performance):
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_UI_HIDDEN -> {
                // App is in background
                Log.d("MediaPlaybackService", "App UI hidden")
            }
            TRIM_MEMORY_RUNNING_LOW, TRIM_MEMORY_RUNNING_CRITICAL -> {
                // System is running low on memory
                Log.d("MediaPlaybackService", "System low on memory")
                if (!isPlaying) {
                    // If not playing, consider stopping the service
                    stopSelf()
                }
            }
        }
    }
