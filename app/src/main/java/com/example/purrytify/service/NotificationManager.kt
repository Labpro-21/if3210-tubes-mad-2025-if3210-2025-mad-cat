package com.example.purrytify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.example.purrytify.MainActivity
import com.example.purrytify.R
import com.example.purrytify.ui.screens.Song
import java.io.File

class NotificationManager(private val context: Context) {
    private val notificationManager: NotificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        const val CHANNEL_ID = "com.example.purrytify.MUSIC_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE = 101
    }
    
    init {
        try {
            // Create notification channel for Android O and higher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_HIGH // High importance to ensure it shows on lock screen
                ).apply {
                    description = "Controls for the currently playing music"
                    setShowBadge(true) // Show badge on app icon
                    lightColor = Color.GREEN
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                Log.d("NotificationManager", "Notification channel created successfully")
            }
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error creating notification channel", e)
        }
    }
    
    // Get album art as bitmap from the song
    private fun getAlbumArt(coverUri: String): Bitmap {
        return try {
            if (coverUri.isNotEmpty() && File(coverUri).exists()) {
                BitmapFactory.decodeFile(coverUri)
            } else {
                // Default cover art
                BitmapFactory.decodeResource(context.resources, R.drawable.default_album_art)
            }
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error loading album art", e)
            // Ensure we have a default image as fallback
            try {
                BitmapFactory.decodeResource(context.resources, R.drawable.default_album_art)
            } catch (e2: Exception) {
                Log.e("NotificationManager", "Error loading default album art", e2)
                // Create a simple colored bitmap as last resort
                val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.DKGRAY)
                bitmap
            }
        }
    }
    
    // Create notification for the currently playing song
    fun getNotification(
        song: Song,
        isPlaying: Boolean,
        mediaSession: MediaSessionCompat,
        position: Int,
        duration: Int,
        isShuffleOn: Boolean = false,
        repeatMode: Int = 0
    ): Notification {
        // Create a pending intent to launch the app when notification is clicked
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "OPEN_PLAYER_SCREEN" // Custom action to open the player screen
        }
        
        Log.d("NotificationManager", "Creating notification for ${song.title} with coverUri ${song.coverUri}")
        
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Get album art
        val albumArt = getAlbumArt(song.coverUri)
        
        // Create the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle(song.title)
            setContentText(song.artist)
            setSmallIcon(R.drawable.ic_stat_music_note) // Small icon for the status bar
            setLargeIcon(albumArt) // Album art as large icon
            setContentIntent(pendingContentIntent) // Intent when notification body is clicked
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            setShowWhen(false) // Don't show the time
            setOnlyAlertOnce(true) // Don't alert every time the notification updates
            
            // Add media controls
            addAction(getPreviousAction(mediaSession))
            addAction(getPlayPauseAction(isPlaying, mediaSession))
            addAction(getNextAction(mediaSession))
            
            // Add shuffle and repeat actions as contextual actions  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // On Android O+, we can add extra contextual actions
                addAction(getShuffleAction(isShuffleOn))
                addAction(getRepeatAction(repeatMode))
            }
            
            // Set the style to MediaStyle for a proper media notification
            setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Show prev, play/pause, next in compact view
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(getStopAction(mediaSession))
            )
            
            // Set color
            setColorized(true)
            color = ContextCompat.getColor(context, R.color.colorPrimary)
            
            // Priority settings
            priority = NotificationCompat.PRIORITY_MAX
            
            // Make it ongoing only when playing
            // This will allow the notification to be swiped away when paused
            setOngoing(isPlaying)
            
            // Set delete intent for when the notification is dismissed
            setDeleteIntent(getStopAction(mediaSession))
        }
        
        return builder.build()
    }
    
    // Get previous action
    private fun getPreviousAction(mediaSession: MediaSessionCompat): NotificationCompat.Action {
        val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        )
        
        return NotificationCompat.Action.Builder(
            R.drawable.ic_skip_previous,
            "Previous",
            prevIntent
        ).build()
    }
    
    // Get play/pause action
    private fun getPlayPauseAction(isPlaying: Boolean, mediaSession: MediaSessionCompat): NotificationCompat.Action {
        val playPauseIntent = if (isPlaying) {
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_PAUSE
            )
        } else {
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_PLAY
            )
        }
        
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        
        return NotificationCompat.Action.Builder(
            icon,
            if (isPlaying) "Pause" else "Play",
            playPauseIntent
        ).build()
    }
    
    // Get next action
    private fun getNextAction(mediaSession: MediaSessionCompat): NotificationCompat.Action {
        val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        )
        
        return NotificationCompat.Action.Builder(
            R.drawable.ic_skip_next,
            "Next",
            nextIntent
        ).build()
    }
    
    // Get stop action (for cancel button)
    private fun getStopAction(mediaSession: MediaSessionCompat): PendingIntent {
        return MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_STOP
        )
    }
    
    // Get shuffle action
    private fun getShuffleAction(isShuffleOn: Boolean): NotificationCompat.Action {
        val shuffleIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = "ACTION_TOGGLE_SHUFFLE"
        }
        
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            shuffleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val icon = if (isShuffleOn) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        
        return NotificationCompat.Action.Builder(
            icon,
            "Shuffle",
            pendingIntent
        ).build()
    }
    
    // Get repeat action
    private fun getRepeatAction(repeatMode: Int): NotificationCompat.Action {
        val repeatIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = "ACTION_TOGGLE_REPEAT"
        }
        
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            repeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val icon = when (repeatMode) {
            0 -> R.drawable.ic_repeat // OFF
            1 -> R.drawable.ic_repeat_all // ALL
            2 -> R.drawable.ic_repeat_one // ONE
            else -> R.drawable.ic_repeat
        }
        
        return NotificationCompat.Action.Builder(
            icon,
            "Repeat",
            pendingIntent
        ).build()
    }
    
    // Show notification for the currently playing song
    fun showNotification(
        song: Song,
        isPlaying: Boolean,
        mediaSession: MediaSessionCompat,
        position: Int,
        duration: Int,
        isShuffleOn: Boolean = false,
        repeatMode: Int = 0
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            getNotification(song, isPlaying, mediaSession, position, duration, isShuffleOn, repeatMode)
        )
    }
    
    // Clear notification
    fun clearNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
