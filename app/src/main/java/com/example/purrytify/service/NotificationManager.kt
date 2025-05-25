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
import android.media.session.MediaSession
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
import java.net.URL
import android.os.StrictMode
import android.os.Handler
import android.os.Looper

class NotificationManager(private val context: Context) {
    private val notificationManager: NotificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var cachedAlbumArt: Bitmap? = null
    private var lastLoadedUri: String? = null
    
    companion object {
        const val CHANNEL_ID = "com.example.purrytify.MUSIC_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE = 101
    }
    
    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Controls for the currently playing music"
                    setShowBadge(true)
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
    
    private fun getAlbumArt(coverUri: String): Bitmap {
        return try {
            if (coverUri == lastLoadedUri && cachedAlbumArt != null) {
                return cachedAlbumArt!!
            }
            
            val bitmap = when {
                coverUri.startsWith("http://") || coverUri.startsWith("https://") -> {
                    try {
                        var resultBitmap: Bitmap? = null
                        val thread = Thread {
                            try {
                                val url = URL(coverUri)
                                val connection = url.openConnection()
                                connection.connect()
                                val inputStream = connection.getInputStream()
                                resultBitmap = BitmapFactory.decodeStream(inputStream)
                                inputStream.close()
                            } catch (e: Exception) {
                                Log.e("NotificationManager", "Error loading album art from URL: $coverUri", e)
                            }
                        }
                        thread.start()
                        thread.join(3000)
                        
                        resultBitmap ?: getDefaultAlbumArt()
                    } catch (e: Exception) {
                        Log.e("NotificationManager", "Error loading album art from URL: $coverUri", e)
                        getDefaultAlbumArt()
                    }
                }
                coverUri.isNotEmpty() && File(coverUri).exists() -> {
                    BitmapFactory.decodeFile(coverUri)
                }
                else -> {
                    getDefaultAlbumArt()
                }
            }
            
            cachedAlbumArt = bitmap
            lastLoadedUri = coverUri
            bitmap
            
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error loading album art", e)
            getDefaultAlbumArt()
        }
    }
    
    private fun getDefaultAlbumArt(): Bitmap {
        return try {
            BitmapFactory.decodeResource(context.resources, R.drawable.default_album_art)
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error loading default album art", e)
            val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.DKGRAY)
            bitmap
        }
    }
    
    fun getNotification(
        song: Song,
        isPlaying: Boolean,
        mediaSession: MediaSessionCompat,
        position: Int,
        duration: Int,
        isShuffleOn: Boolean = false,
        repeatMode: Int = 0
    ): Notification {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "OPEN_PLAYER_SCREEN"
        }
        
        Log.d("NotificationManager", "Creating notification for ${song.title} with coverUri ${song.coverUri}")
        
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val albumArt = getAlbumArt(song.coverUri)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle(song.title)
            setContentText(song.artist)
            setSmallIcon(R.drawable.ic_stat_music_note)
            setLargeIcon(albumArt)
            setContentIntent(pendingContentIntent)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setShowWhen(false)
            setOnlyAlertOnce(true)
            
            if (!isPlaying) {
                setAutoCancel(false)
            }
            
            addAction(getPreviousAction(mediaSession))
            addAction(getPlayPauseAction(isPlaying, mediaSession))
            addAction(getNextAction(mediaSession))
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                addAction(getShuffleAction(isShuffleOn))
                addAction(getRepeatAction(repeatMode))
            }
            
            setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(getStopAction(mediaSession))
            )
            
            setColorized(true)
            color = ContextCompat.getColor(context, R.color.colorPrimary)
            priority = NotificationCompat.PRIORITY_MAX
            setOngoing(isPlaying)
            setDeleteIntent(getStopAction(mediaSession))
        }
        
        return builder.build()
    }
    
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

    private fun getStopAction(mediaSession: MediaSessionCompat): PendingIntent {
        return MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_STOP
        )
    }
    
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
    
    fun clearNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
