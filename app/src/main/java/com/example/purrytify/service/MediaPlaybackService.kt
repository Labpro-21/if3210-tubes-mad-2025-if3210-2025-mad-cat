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
import java.net.URL
import android.graphics.BitmapFactory
import android.graphics.Bitmap

class MediaPlaybackService : LifecycleService() {
    private var shuffleState = false
    private var repeatMode = 0 // 0 = OFF, 1 = ALL, 2 = ONE
    private var hasCompletionListenerFired = false
    
    // Service timeout management
    private var serviceStartTime: Long = 0
    private val SERVICE_TIMEOUT = 30 * 60 * 1000L // 30 minutes timeout
    private var timeoutCheckJob: Job? = null
    
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var isPlaying = false
    private var updateJob: Job? = null
    private var currentPosition = 0
    private var songDuration = 0
    
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            mediaPlayer?.let {
                it.start()
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                updateNotification()
                startPositionUpdates()
                sendPlaybackStateBroadcast(true)
            }
        }
        
        override fun onPause() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    
                    // Update notification to show pause state
                    updateNotification()
                    
                    stopPositionUpdates()
                    sendPlaybackStateBroadcast(false)
                    
                    // Stop foreground but keep the notification visible (dismissible)
                    // Using STOP_FOREGROUND_DETACH keeps notification visible but dismissible
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(Service.STOP_FOREGROUND_DETACH)
                    } else {
                        stopForeground(false)
                    }
                }
            }
        }
        
        override fun onStop() {
            Log.d("MediaPlaybackService", "onStop called")
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                    mediaPlayer = null
                }
                
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                
                stopForeground(true)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(NotificationManager.NOTIFICATION_ID)
                
                stopSelf()
                
                sendPlaybackStateBroadcast(false)
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error in onStop", e)
            }
        }
        
        override fun onSkipToNext() {
            Log.d("MediaPlaybackService", "Skip to next received from notification")
            sendMediaActionBroadcast("ACTION_NEXT")
        }
        
        override fun onSkipToPrevious() {
            Log.d("MediaPlaybackService", "Skip to previous received from notification")
            sendMediaActionBroadcast("ACTION_PREVIOUS")
        }
        
        override fun onSeekTo(pos: Long) {
            mediaPlayer?.seekTo(pos.toInt())
            updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
            updateNotification()
        }
    }
    
    inner class MediaServiceBinder : Binder() {
        fun getService() = this@MediaPlaybackService
    }
    
    private val binder = MediaServiceBinder()
    
    override fun onCreate() {
        super.onCreate()
        
        serviceStartTime = System.currentTimeMillis()
        
        try {
            mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession").apply {
                setCallback(mediaSessionCallback)
                isActive = true
            }
            
            notificationManager = NotificationManager(this)
            
            // Start timeout check
            startTimeoutCheck()
            
            Log.d("MediaPlaybackService", "Service created successfully")
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error in onCreate", e)
        }
    }
    
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
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        Log.d("MediaPlaybackService", "onStartCommand, action: ${intent?.action}")
        
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        when (intent?.action) {
            "START_FOREGROUND" -> {
                Log.d("MediaPlaybackService", "Starting in foreground mode")
                if (currentSong != null) {
                    updateNotification()
                }
            }
            "ACTION_PLAY" -> mediaSessionCallback.onPlay()
            "ACTION_PAUSE" -> mediaSessionCallback.onPause()
            "ACTION_NEXT" -> mediaSessionCallback.onSkipToNext()
            "ACTION_PREVIOUS" -> mediaSessionCallback.onSkipToPrevious()
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
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    
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
            "ACTION_TOGGLE_SHUFFLE" -> {
                sendMediaActionBroadcast("ACTION_TOGGLE_SHUFFLE")
            }
            "ACTION_TOGGLE_REPEAT" -> {
                sendMediaActionBroadcast("ACTION_TOGGLE_REPEAT")
            }
        }
        
        return START_STICKY
    }
    
    fun playSong(song: Song) {
        hasCompletionListenerFired = false
        try {
            Log.d("MediaPlaybackService", "Playing song: ${song.title} by ${song.artist}")
            currentSong = song
            
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error releasing previous media player", e)
            }
            
            try {
                val player = MediaPlayer()
                player.setDataSource(applicationContext, Uri.parse(song.uri))
                player.prepare()
                songDuration = player.duration
                Log.d("MediaPlaybackService", "Song duration: $songDuration ms")
                
                player.start()
                
                player.setOnCompletionListener {
                    Log.d("MediaPlaybackService", "MediaPlayer onCompletion called")
                    try {
                        if (!hasCompletionListenerFired) {
                            hasCompletionListenerFired = true
                            Log.d("MediaPlaybackService", "Song completed: ${song.title}, isPlaying: $isPlaying")
                            sendMediaActionBroadcast("ACTION_SONG_COMPLETED")
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(500)
                                hasCompletionListenerFired = false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MediaPlaybackService", "Error in completion listener", e)
                    }
                }
                
                player.setOnErrorListener { _, what, extra ->
                    Log.e("MediaPlaybackService", "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
                
                mediaPlayer = player
                isPlaying = true

                updateMetadata(song)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                startPositionUpdates()
                updateNotification()
                
                // Load album art asynchronously for online songs
                if (song.coverUri.startsWith("http://") || song.coverUri.startsWith("https://")) {
                    loadAlbumArtAsync(song)
                }
                
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
                
                sendPlaybackStateBroadcast(true)
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error initializing media player", e)
            }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error playing song", e)
        }
    }
    
    private fun loadAlbumArtAsync(song: Song) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Small delay to ensure notification shows immediately with default art
                delay(100)
                
                val url = URL(song.coverUri)
                val connection = url.openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                if (bitmap != null && currentSong?.title == song.title) {
                    // Update notification with the loaded artwork
                    withContext(Dispatchers.Main) {
                        updateNotification()
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error loading album art asynchronously", e)
            }
        }
    }
    
    fun togglePlayPause() {
        if (mediaPlayer == null) return
        
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            stopPositionUpdates()
            sendPlaybackStateBroadcast(false)
            
            // Stop foreground but keep the notification visible (dismissible)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(false)
            }
        } else {
            mediaPlayer?.start()
            isPlaying = true
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            startPositionUpdates()
            sendPlaybackStateBroadcast(true)
        }
        
        // Always update notification when toggling play/pause
        updateNotification()
    }
    
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
    }
    
    fun playNext(song: Song) {
        playSong(song)
    }
    
    fun playPrevious(song: Song) {
        playSong(song)
    }
    
    fun getMediaPlayer(): MediaPlayer? {
        return mediaPlayer
    }
    
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }
    
    fun stopService() {
        Log.d("MediaPlaybackService", "stopService called")
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
            }
            
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            
            stopForeground(true)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(NotificationManager.NOTIFICATION_ID)
            
            sendPlaybackStateBroadcast(false)
            
            stopSelf()
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error in stopService", e)
        }
    }
    
    private fun sendPlaybackStateBroadcast(isPlaying: Boolean) {
        Log.d("MediaPlaybackService", "Sending playback state broadcast: isPlaying=$isPlaying")
        val intent = Intent("com.example.purrytify.PLAYBACK_STATE_CHANGED")
        intent.putExtra("isPlaying", isPlaying)
        sendBroadcast(intent)
    }
    
    private fun sendMediaActionBroadcast(action: String) {
        Log.d("MediaPlaybackService", "Sending broadcast: $action")
        val intent = Intent("com.example.purrytify.MEDIA_ACTION")
        intent.putExtra("action", action)
        sendBroadcast(intent)
    }
    
    private fun startPositionUpdates() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    mediaPlayer?.let {
                        val newPosition = it.currentPosition
                        withContext(Dispatchers.Main) {
                            currentPosition = newPosition
                        }
                        if (it.isPlaying && newPosition % 1000 < 100) {
                            withContext(Dispatchers.Main) {
                                updateNotification()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Error in position update", e)
                }
                delay(100)
            }
        }
    }
    
    private fun stopPositionUpdates() {
        updateJob?.cancel()
        updateJob = null
    }
    
    private fun updateMetadata(song: Song) {
        Log.d("MediaPlaybackService", "Updating metadata - Duration: $songDuration")
        val builder = MediaMetadataCompat.Builder().apply {
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            
            // For URLs, use the URL directly. For local files, use file:// prefix
            val albumArtUri = when {
                song.coverUri.startsWith("http://") || song.coverUri.startsWith("https://") -> {
                    song.coverUri
                }
                song.coverUri.isNotEmpty() -> {
                    "file://" + song.coverUri
                }
                else -> ""
            }
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, albumArtUri)
            
            // Optional: Also try to set a bitmap for better compatibility
            if (song.coverUri.isNotEmpty() && !song.coverUri.startsWith("http")) {
                try {
                    val bitmap = BitmapFactory.decodeFile(song.coverUri)
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Error loading local album art bitmap", e)
                }
            }
            
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, songDuration.toLong())
        }
        
        mediaSession.setMetadata(builder.build())
    }
    
    private fun updatePlaybackState(state: Int) {
        val builder = PlaybackStateCompat.Builder().apply {
            setState(
                state,
                mediaPlayer?.currentPosition?.toLong() ?: 0,
                1.0f
            )
            
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
    
    fun updateNotification() {
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
                if (isPlaying) {
                    // When playing, update as foreground service
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NotificationManager.NOTIFICATION_ID, notification)
                    }
                } else {
                    // When paused, just update the notification normally to keep it dismissible
                    val systemNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    systemNotificationManager.notify(NotificationManager.NOTIFICATION_ID, notification)
                }
                
                Log.d("MediaPlaybackService", "Notification updated successfully: ${song.title}, playing: $isPlaying")
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error updating notification", e)
                e.printStackTrace()
            }
        } ?: run {
            Log.e("MediaPlaybackService", "Cannot update notification: currentSong is null")
        }
    }
    
    fun updateShuffleState(isShuffleOn: Boolean) {
        Log.d("MediaPlaybackService", "Updating shuffle state to: $isShuffleOn")
        shuffleState = isShuffleOn
        updateNotification()
    }
    
    fun updateRepeatMode(mode: Int) {
        Log.d("MediaPlaybackService", "Updating repeat mode to: $mode")
        repeatMode = mode
        updateNotification()
    }
    
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
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error in onDestroy", e)
        }
        
        super.onDestroy()
    }
    
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
}
