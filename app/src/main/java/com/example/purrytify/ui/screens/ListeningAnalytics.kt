package com.example.purrytify.ui.screens

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.purrytify.data.preferences.TokenManager

object ListeningAnalytics {
    // Track time listened in seconds
    private var totalTimeListened = MutableStateFlow(0L)
    private val _timeListened = MutableStateFlow(0L) 
    val timeListened: StateFlow<Long> = _timeListened.asStateFlow()
    
    // Track artist play counts
    private val artistPlayCounts = mutableMapOf<String, Int>()
    private val _topArtist = MutableStateFlow(Pair("", 0))
    val topArtist: StateFlow<Pair<String, Int>> = _topArtist.asStateFlow()
    
    // Track song play counts and duration listened (in seconds)
    val songPlayCounts = mutableMapOf<String, Int>()
    val songListeningDurations = mutableMapOf<String, Long>()
    private val _topSong = MutableStateFlow(Triple("", 0, 0L))
    val topSong: StateFlow<Triple<String, Int, Long>> = _topSong.asStateFlow()
    
    // Track consecutive days for songs
    private val songLastPlayed = mutableMapOf<String, LocalDate>()
    private val songStreakDays = mutableMapOf<String, Int>()
    private val _streakSong = MutableStateFlow(Triple("", "", 0))
    val streakSong: StateFlow<Triple<String, String, Int>> = _streakSong.asStateFlow()
    
    // Track currently playing song
    private var currentlyPlayingSong: Song? = null
    private var playbackStartTime: Long = 0L
    private var isPlaying = false
    
    // For real-time updates
    private var timerJob: Job? = null
    
    // Flag to prevent multiple loads
    private var lastLoadedEmail: String? = null
    private var lastContext: Context? = null
    private var lastUserEmail: String? = null

    fun startPlayback(song: Song) {
        // Cancel any existing timer
        timerJob?.cancel()
        
        // Update currently playing song
        currentlyPlayingSong = song
        playbackStartTime = System.currentTimeMillis()
        isPlaying = true
        
        // Update song play count
        val songKey = "${song.title}_${song.artist}"
        val currentCount = songPlayCounts.getOrDefault(songKey, 0)
        songPlayCounts[songKey] = currentCount + 1
        
        // Update artist play count
        val artist = song.artist
        val currentArtistCount = artistPlayCounts.getOrDefault(artist, 0)
        artistPlayCounts[artist] = currentArtistCount + 1
        
        // Update top song and artist
        updateTopSongAndArtist()
        
        // Update streak
        val today = LocalDate.now()
        val lastPlayed = songLastPlayed[songKey]
        
        if (lastPlayed != null) {
            val daysBetween = lastPlayed.until(today).days
            
            if (daysBetween == 1) {
                // Continued streak
                songStreakDays[songKey] = (songStreakDays[songKey] ?: 1) + 1
            } else if (daysBetween > 1) {
                // Reset streak
                songStreakDays[songKey] = 1
            }
        } else {
            // First time playing
            songStreakDays[songKey] = 1
        }
        
        songLastPlayed[songKey] = today
        
        // Update streak song
        updateStreakSong()
        
        // Start real-time timer to update time listened
        startTimer()
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            while (isPlaying && currentlyPlayingSong != null) {
                delay(1000)
                if (isPlaying) { // Double check isPlaying karena bisa berubah selama delay
                    totalTimeListened.value += 1
                    _timeListened.value = totalTimeListened.value
                    
                    // Add 1 second to the current song's listening duration
                    val songKey = "${currentlyPlayingSong!!.title}_${currentlyPlayingSong!!.artist}"
                    val currentDuration = songListeningDurations.getOrDefault(songKey, 0L)
                    val newDuration = currentDuration + 1
                    songListeningDurations[songKey] = newDuration
                    updateTopSongAndArtist()
                    
                    // Real-time save
                    lastContext?.let { ctx ->
                        lastUserEmail?.let { email ->
                            saveToPreferences(ctx, email)
                        }
                    }
                }
            }
        }
    }
    
    fun pausePlayback() {
        if (currentlyPlayingSong != null) {
            isPlaying = false
            timerJob?.cancel()
            
            if (playbackStartTime > 0) {
                val duration = System.currentTimeMillis() - playbackStartTime
                val durationInSeconds = duration / 1000 // Convert to seconds
                
                // Hanya update waktu untuk periode yang sudah berlalu
                if (durationInSeconds > 0) {
                    val songKey = "${currentlyPlayingSong!!.title}_${currentlyPlayingSong!!.artist}"
                    val currentDuration = songListeningDurations.getOrDefault(songKey, 0L)
                    songListeningDurations[songKey] = currentDuration + durationInSeconds
                    
                    totalTimeListened.value += durationInSeconds
                    _timeListened.value = totalTimeListened.value
                    
                    updateTopSongAndArtist()
                }
            }
            
            playbackStartTime = 0L
            
            // Real-time save
            lastContext?.let { ctx ->
                lastUserEmail?.let { email ->
                    saveToPreferences(ctx, email)
                }
            }
        }
    }
    
    fun resumePlayback() {
        if (currentlyPlayingSong != null) {
            isPlaying = true
            playbackStartTime = System.currentTimeMillis()
            startTimer()
        }
    }
    
    fun stopPlayback() {
        pausePlayback()
        isPlaying = false
        currentlyPlayingSong = null
        timerJob?.cancel()
        // Real-time save
        lastContext?.let { ctx ->
            lastUserEmail?.let { email ->
                saveToPreferences(ctx, email)
            }
        }
    }
    
    private fun updateTopSongAndArtist() {
        songListeningDurations.maxByOrNull { it.value }?.let { (songKey, duration) ->
            val count = songPlayCounts[songKey] ?: 0
            val songTitle = songKey.split("_").first()
            _topSong.value = Triple(songTitle, count, duration)
        }

        artistPlayCounts.maxByOrNull { it.value }?.let { (artist, count) ->
            _topArtist.value = Pair(artist, count)
        }
    }
    
    private fun updateStreakSong() {
        songStreakDays.filter { it.value >= 2 }.maxByOrNull { it.value }?.let { (songKey, streakDays) ->
            val songTitle = songKey.split("_").first()
            val artistName = songKey.split("_").last()
            _streakSong.value = Triple(songTitle, artistName, streakDays)
        }
    }
    
    // Persistence methods
    fun saveToPreferences(context: Context, email: String) {
        val tokenManager = TokenManager(context)
        
        // Save time listened
        tokenManager.saveString("${email}_time_listened", totalTimeListened.value.toString())
        
        // Save artist play counts
        val artistData = artistPlayCounts.entries.joinToString("|") { "${it.key}:${it.value}" }
        tokenManager.saveString("${email}_artist_counts", artistData)
        
        // Save song play counts
        val songData = songPlayCounts.entries.joinToString("|") { "${it.key}:${it.value}" }
        tokenManager.saveString("${email}_song_counts", songData)
        
        // Save song listening durations
        val durationData = songListeningDurations.entries.joinToString("|") { "${it.key}:${it.value}" }
        tokenManager.saveString("${email}_song_durations", durationData)
        
        // Save streak data
        val streakData = songStreakDays.entries.joinToString("|") { "${it.key}:${it.value}" }
        tokenManager.saveString("${email}_song_streaks", streakData)
        
        // Save last played dates
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val dateData = songLastPlayed.entries.joinToString("|") { 
            "${it.key}:${it.value.format(dateFormatter)}" 
        }
        tokenManager.saveString("${email}_last_played", dateData)
        
        Log.d("ListeningAnalytics", "Saved ${songListeningDurations.size} song durations to preferences")
    }
    
    fun loadFromPreferences(context: Context, email: String) {
        if (lastLoadedEmail == email) return // Only load once per user
        lastLoadedEmail = email
        lastContext = context
        lastUserEmail = email
        
        val tokenManager = TokenManager(context)
        
        // Load time listened
        val timeStr = tokenManager.getString("${email}_time_listened")
        timeStr?.toLongOrNull()?.let {
            totalTimeListened.value = it
            _timeListened.value = it
            Log.d("ListeningAnalytics", "Loaded total time listened: $it seconds")
        }
        
        // Load artist play counts
        val artistData = tokenManager.getString("${email}_artist_counts")
        artistData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val artist = parts[0]
                val count = parts[1].toIntOrNull() ?: 0
                artistPlayCounts[artist] = count
            }
        }
        
        // Load song play counts
        val songData = tokenManager.getString("${email}_song_counts")
        songData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val song = parts[0]
                val count = parts[1].toIntOrNull() ?: 0
                songPlayCounts[song] = count
            }
        }
        
        // Load song listening durations
        val durationData = tokenManager.getString("${email}_song_durations")
        var loadedDurationsCount = 0
        durationData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val song = parts[0]
                val duration = parts[1].toLongOrNull() ?: 0L
                songListeningDurations[song] = duration
                loadedDurationsCount++
                Log.d("ListeningAnalytics", "Loaded duration for $song: $duration seconds")
            }
        }
        Log.d("ListeningAnalytics", "Loaded $loadedDurationsCount song durations")
        
        // Load streak data
        val streakData = tokenManager.getString("${email}_song_streaks")
        streakData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val song = parts[0]
                val streak = parts[1].toIntOrNull() ?: 0
                songStreakDays[song] = streak
            }
        }
        
        // Load last played dates
        val dateData = tokenManager.getString("${email}_last_played")
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        dateData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val song = parts[0]
                try {
                    val date = LocalDate.parse(parts[1], dateFormatter)
                    songLastPlayed[song] = date
                } catch (e: Exception) {
                    // Skip invalid date
                }
            }
        }
        
        // Update derived values
        updateTopSongAndArtist()
        updateStreakSong()
        
        // Log top songs by duration
        val topSongs = songListeningDurations.toList()
            .sortedByDescending { it.second }
            .take(3)
        
        if (topSongs.isNotEmpty()) {
            Log.d("ListeningAnalytics", "===== TOP SONGS BY LISTENING TIME =====")
            topSongs.forEachIndexed { index, (song, time) ->
                val count = songPlayCounts[song] ?: 0
                val songName = song.split("_").firstOrNull() ?: song
                Log.d("ListeningAnalytics", "${index + 1}. $songName: ${formatDuration(time)} (played $count times)")
            }
        }
    }

    // Format time into hours and minutes
    fun formatTimeListened(): String {
        val hours = _timeListened.value / 3600
        val minutes = (_timeListened.value % 3600) / 60
        
        return if (hours > 0) {
            "$hours h $minutes min"
        } else {
            "$minutes min"
        }
    }

    fun formatDuration(durationInSeconds: Long?): String {
        val hours = durationInSeconds?.div(3600)
        val minutes = (durationInSeconds?.rem(3600))?.div(60)
        val seconds = durationInSeconds?.rem(60)
        
        return buildString {
            if (hours != null) {
                if (hours > 0) {
                    append("${hours}h ")
                }
            }
            // Always show minutes even if they're zero
            append("${minutes}m ")
            // Only show seconds if they're non-zero or if hours and minutes are both zero
            if (seconds != null) {
                if (seconds > 0 || (hours == 0L && minutes == 0L)) {
                    append("${seconds}s")
                }
            }
        }.trim()
    }

    // Get all song listening durations
    fun getAllSongListeningData(): List<Triple<String, Int, Long>> {
        return songListeningDurations.map { (songKey, duration) ->
            val title = songKey.split("_").firstOrNull() ?: songKey
            val count = songPlayCounts[songKey] ?: 0
            Triple(title, count, duration)
        }.sortedByDescending { it.third }
    }
}