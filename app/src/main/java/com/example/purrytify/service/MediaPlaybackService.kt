package com.example.purrytify.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.media.session.MediaButtonReceiver
import com.example.purrytify.MainActivity
import com.example.purrytify.ui.screens.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service for media playback in the background
 */
class MediaPlaybackService : LifecycleService() {
    // Store reference to the MusicViewModel for accessing shuffle and repeat states
    private var shuffleState = false
    private var repeatMode = 0 // 0 = OFF, 1 = ALL, 2 = ONE
    private var hasCompletionListenerFired = false
    
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var isPlaying = false
    private var updateJob: Job? = null
    private var currentPosition = 0
    private var songDuration = 0 // Renamed to avoid conflict
    
    // Callback for media session events
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            mediaPlayer?.let {
                it.start()
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                updateNotification()
                startPositionUpdates()
                // Broadcast the playing state to update UI
                sendPlaybackStateBroadcast(true)
            }
        }
        
        override fun onPause() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    updateNotification()
                    stopPositionUpdates()
                    
                    // Broadcast the paused state to update UI
                    sendPlaybackStateBroadcast(false)
                    
                    // When paused, exit foreground state but keep notification visible
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(Service.STOP_FOREGROUND_DETACH) // Stop foreground but keep notification
                    } else {
                        stopForeground(false) // Older API - keep notification
                    }
                }
            }
        }
        
        override fun onStop() {
            Log.d("MediaPlaybackService", "onStop called")
            try {
                // Stop playback first
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                    mediaPlayer = null
                }
                
                // Update states
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                
                // Remove notification
                stopForeground(true)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(NotificationManager.NOTIFICATION_ID)
                
                // Stop the service
                stopSelf()
                
                // Broadcast the stopped state to update UI
                sendPlaybackStateBroadcast(false)
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error in onStop", e)
            }
        }
        
        override fun onSkipToNext() {
            // Broadcast to let MusicViewModel handle next
            Log.d("MediaPlaybackService", "Skip to next received from notification")
            sendMediaActionBroadcast("ACTION_NEXT")
        }
        
        override fun onSkipToPrevious() {
            // Broadcast to let MusicViewModel handle previous
            Log.d("MediaPlaybackService", "Skip to previous received from notification")
            sendMediaActionBroadcast("ACTION_PREVIOUS")
        }
        
        override fun onSeekTo(pos: Long) {
            mediaPlayer?.seekTo(pos.toInt())
            updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
            updateNotification()
        }
    }
    
    // Binder for clients to access the service
    inner class MediaServiceBinder : Binder() {
        fun getService() = this@MediaPlaybackService
    }
    
    private val binder = MediaServiceBinder()
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize media session
            mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession").apply {
                setCallback(mediaSessionCallback)
                isActive = true
            }
            
            // Initialize notification manager
            notificationManager = NotificationManager(this)
            
            Log.d("MediaPlaybackService", "Service created successfully")
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error in onCreate", e)
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Call super.onStartCommand to satisfy Android's requirements
        super.onStartCommand(intent, flags, startId)
        
        Log.d("MediaPlaybackService", "onStartCommand, action: ${intent?.action}")
        
        // Handle media button events
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        when (intent?.action) {
            "START_FOREGROUND" -> {
                Log.d("MediaPlaybackService", "Starting in foreground mode")
                if (currentSong != null) {
                    // Update notification to start foreground
                    updateNotification()
                }
            }
            "ACTION_PLAY" -> mediaSessionCallback.onPlay()
            "ACTION_PAUSE" -> mediaSessionCallback.onPause()
            "ACTION_NEXT" -> mediaSessionCallback.onSkipToNext()
            "ACTION_PREVIOUS" -> mediaSessionCallback.onSkipToPrevious()
            "ACTION_STOP" -> {
                mediaSessionCallback.onStop()
                stopSelf()
            }
            "ACTION_TOGGLE_SHUFFLE" -> {
                sendMediaActionBroadcast("ACTION_TOGGLE_SHUFFLE")
            }
            "ACTION_TOGGLE_REPEAT" -> {
                sendMediaActionBroadcast("ACTION_TOGGLE_REPEAT")
            }
        }
        
        return START_STICKY // Return START_STICKY to ensure service restarts if killed
    }
    
    // Method to play a song
    fun playSong(song: Song) {
        hasCompletionListenerFired = false // Reset the flag for each new song
        try {
            Log.d("MediaPlaybackService", "Playing song: ${song.title} by ${song.artist}")
            currentSong = song
            
            // Release previous media player
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error releasing previous media player", e)
            }
            
            try {
                // Create a new media player
                val player = MediaPlayer()
                player.setDataSource(applicationContext, Uri.parse(song.uri))
                player.prepare()
                
                // Get duration BEFORE starting playback
                songDuration = player.duration
                Log.d("MediaPlaybackService", "Song duration: $songDuration ms")
                
                // Start playback
                player.start()
                
                // Set the completion listener
                player.setOnCompletionListener {
                    Log.d("MediaPlaybackService", "MediaPlayer onCompletion called")
                    try {
                        // Always send the completion broadcast
                        if (!hasCompletionListenerFired) {
                            hasCompletionListenerFired = true
                            Log.d("MediaPlaybackService", "Song completed: ${song.title}, isPlaying: $isPlaying")
                            // Always send the completion broadcast, don't check isPlaying
                            sendMediaActionBroadcast("ACTION_SONG_COMPLETED")
                            // Reset the flag after a delay
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(500)
                                hasCompletionListenerFired = false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MediaPlaybackService", "Error in completion listener", e)
                    }
                }
                
                // Set error listener  
                player.setOnErrorListener { _, what, extra ->
                    Log.e("MediaPlaybackService", "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
                
                // Now set the media player and update state
                mediaPlayer = player
                isPlaying = true
                
                // Update metadata
                updateMetadata(song)
                
                // Update playback state
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                
                // Start position updates
                startPositionUpdates()
                
                // Show notification and start foreground service
                updateNotification()
                
                // Make sure we're in foreground mode
                try {
                    val notification = notificationManager.getNotification(
                        song, isPlaying, mediaSession, currentPosition, songDuration,
                        shuffleState, repeatMode)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NotificationManager.NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NotificationManager.NOTIFICATION_ID, notification)
                    }
                    Log.d("MediaPlaybackService", "Started foreground service successfully")
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Error starting foreground", e)
                }
                
                // Broadcast that we're playing
                sendPlaybackStateBroadcast(true)
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error initializing media player", e)
            }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error playing song", e)
        }
    }
    
    // Method to toggle play/pause
    fun togglePlayPause() {
        if (mediaPlayer == null) return
        
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            stopPositionUpdates()
            sendPlaybackStateBroadcast(false)
        } else {
            mediaPlayer?.start()
            isPlaying = true
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            startPositionUpdates()
            sendPlaybackStateBroadcast(true)
        }
        
        updateNotification()
    }
    
    // Method to seek to a position
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
    }
    
    // Method to play next song
    fun playNext(song: Song) {
        playSong(song)
    }
    
    // Method to play previous song
    fun playPrevious(song: Song) {
        playSong(song)
    }
    
    // Method to get the media player for progress updates
    fun getMediaPlayer(): MediaPlayer? {
        return mediaPlayer
    }
    
    // Get current position
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    // Get duration
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }
    
    // Add a method to stop the service properly
    fun stopService() {
        Log.d("MediaPlaybackService", "stopService called")
        try {
            // Stop playback
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
            }
            
            // Update states
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            
            // Remove notification
            stopForeground(true)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(NotificationManager.NOTIFICATION_ID)
            
            // Broadcast the stopped state
            sendPlaybackStateBroadcast(false)
            
            // Stop the service
            stopSelf()
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error in stopService", e)
        }
    }
    
    // Send broadcast about playback state changes
    private fun sendPlaybackStateBroadcast(isPlaying: Boolean) {
        Log.d("MediaPlaybackService", "Sending playback state broadcast: isPlaying=$isPlaying")
        val intent = Intent("com.example.purrytify.PLAYBACK_STATE_CHANGED")
        intent.putExtra("isPlaying", isPlaying)
        sendBroadcast(intent)
    }
    
    // Send broadcast for media actions (next/previous/shuffle/repeat)
    private fun sendMediaActionBroadcast(action: String) {
        Log.d("MediaPlaybackService", "Sending broadcast: $action")
        val intent = Intent("com.example.purrytify.MEDIA_ACTION")
        intent.putExtra("action", action)
        sendBroadcast(intent)
    }
    
    // Start updates for playback position
    private fun startPositionUpdates() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    mediaPlayer?.let {
                        // Always update position, not just when playing
                        val newPosition = it.currentPosition
                        withContext(Dispatchers.Main) {
                            currentPosition = newPosition
                        }
                        // Only update notification occasionally to avoid flicker
                        if (it.isPlaying && newPosition % 1000 < 100) { // Update ~every second
                            withContext(Dispatchers.Main) {
                                updateNotification()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Error in position update", e)
                }
                delay(100) // Update more frequently for smoother progress
            }
        }
    }
    
    // Stop updates for playback position
    private fun stopPositionUpdates() {
        updateJob?.cancel()
        updateJob = null
    }
    
    // Update metadata for the current song
    private fun updateMetadata(song: Song) {
        Log.d("MediaPlaybackService", "Updating metadata - Duration: $songDuration")
        val builder = MediaMetadataCompat.Builder().apply {
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.coverUri)
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, songDuration.toLong())
        }
        
        mediaSession.setMetadata(builder.build())
    }
    
    // Update playback state
    private fun updatePlaybackState(state: Int) {
        val builder = PlaybackStateCompat.Builder().apply {
            setState(
                state,
                mediaPlayer?.currentPosition?.toLong() ?: 0,
                1.0f
            )
            
            // Add available actions
            setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
        }
        
        mediaSession.setPlaybackState(builder.build())
    }
    
    // Update notification
    private fun updateNotification() {
        currentSong?.let { song ->
            val notification = notificationManager.getNotification(
                song,
                isPlaying,
                mediaSession,
                currentPosition,
                songDuration,
                shuffleState,
                repeatMode
            )
            
            try {
                // Make sure we set foreground with the notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NotificationManager.NOTIFICATION_ID, notification)
                }
                
                // Also notify through the system NotificationManager for extra visibility
                val systemNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                systemNotificationManager.notify(NotificationManager.NOTIFICATION_ID, notification)
                
                // Log successful notification creation
                Log.d("MediaPlaybackService", "Notification updated successfully: ${song.title}")
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error updating notification", e)
                e.printStackTrace()
            }
        } ?: run {
            Log.e("MediaPlaybackService", "Cannot update notification: currentSong is null")
        }
    }
    
    // Update shuffle state
    fun updateShuffleState(isShuffleOn: Boolean) {
        Log.d("MediaPlaybackService", "Updating shuffle state to: $isShuffleOn")
        shuffleState = isShuffleOn
        updateNotification()
    }
    
    // Update repeat mode
    fun updateRepeatMode(mode: Int) {
        Log.d("MediaPlaybackService", "Updating repeat mode to: $mode")
        repeatMode = mode
        updateNotification()
    }
    
    // Clean up
    override fun onDestroy() {
        Log.d("MediaPlaybackService", "Service onDestroy called")
        
        // Cancel all jobs
        updateJob?.cancel()
        
        // Stop and release media player
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error releasing media player", e)
        }
        mediaPlayer = null
        
        // Release media session
        mediaSession.release()
        
        // Stop foreground and remove notification
        stopForeground(true)
        
        // Clear the notification using system notification manager
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(NotificationManager.NOTIFICATION_ID)
        
        super.onDestroy()
    }
}