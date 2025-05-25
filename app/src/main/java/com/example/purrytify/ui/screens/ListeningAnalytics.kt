package com.example.purrytify.ui.screens
// Dipindahin ke data nanti
import android.content.Context
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.purrytify.ui.viewmodel.MusicViewModel

object ListeningAnalytics {
    // Track time listened in seconds
    private var totalTimeListened = MutableStateFlow(0L)
    private val _timeListened = MutableStateFlow(0L)
    val timeListened: StateFlow<Long> = _timeListened.asStateFlow()

    // Track daily listening data
    private val dailyListeningMap = mutableMapOf<String, Long>()

    // Track the date of the last load or reset
    private var lastLoadDate: String = ""

    private fun getCurrentMonthKey(): String {
        val now = LocalDate.now()
        return "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
    }

    // Track song play counts - map song keys to play count
    private val songPlayCounts = mutableMapOf<String, Int>()

    // Track song cover image URLs
    private val songCoverUrls = mutableMapOf<String, String>()

    // Track currently playing song to prevent duplicate counts
    private var _lastPlayedSongKey: String? = null

    // Helper class for returning 4 values
    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun initializeAnalytics(context: Context, email: String) {
        if (lastLoadedEmail == email) return

        // Reset counters first
        _timeListened.value = 0

        // Load data from preferences (this will handle proper daily reset)
        loadFromPreferences(context, email)

        ListenedSongsTracker.loadListenedSongs(email, context)

        // Log current state after initialization
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        Log.d(
            "ListeningAnalytics",
            "Analytics initialized. Today (${today}) time: ${_timeListened.value} seconds"
        )
    }    
    
    // Track artist play counts

    private val artistPlayCountsByMonth = mutableMapOf<String, MutableMap<String, Int>>()
    private val _topArtist = MutableStateFlow(Pair("", 0))
    val topArtist: StateFlow<Pair<String, Int>> = _topArtist.asStateFlow()

    // Track song duration listened (in seconds)
    val songListeningDurations = mutableMapOf<String, Long>()
    private val _topSong = MutableStateFlow(Triple("", 0, 0L))
    val topSong: StateFlow<Triple<String, Int, Long>> = _topSong.asStateFlow()

    // Track consecutive days for songs
    private val songLastPlayed = mutableMapOf<String, LocalDate>()
    private val songStreakDays = mutableMapOf<String, Int>()
    private val _streakSong = MutableStateFlow(Triple("", "", 0))
    val streakSong: StateFlow<Triple<String, String, Int>> = _streakSong.asStateFlow()

    // Flag to prevent multiple loads
    private var lastLoadedEmail: String? = null
    private var lastContext: Context? = null
    private var lastUserEmail: String? = null

    private var currentlyPlayingSong: Song? = null

    // Track current session start time
    private var sessionStartTime: Long = 0

    fun startPlayback(song: Song, musicViewModel: MusicViewModel, context: Context, email: String) {
        val currentSong = musicViewModel.currentSong.value

        // Create a song key that uniquely identifies this song
        val songKey = "${song.title}_${song.artist}"

        // Store cover URL if available
        if (song.coverUri.isNotEmpty()) {
            songCoverUrls[songKey] = song.coverUri
            Log.d("ListeningAnalytics", "Saved cover URL for $songKey: ${song.coverUri}")
        }

        // Check if this is the same song as last time to prevent duplicate counts on page refreshes
        if (_lastPlayedSongKey == songKey) {
            // Same song, just continue tracking without incrementing counters
            Log.d("ListeningAnalytics", "Same song detected, not incrementing play count: $songKey")
            startPlaybackTracking(musicViewModel, context, email)
            return
        }

        // Update tracking for new song
        _lastPlayedSongKey = songKey

        // Update currently playing song
        currentlyPlayingSong = currentSong
        // Update artist play count by month
        val artist = song.artist
        val currentMonth = getCurrentMonthKey()

        val monthMap = artistPlayCountsByMonth.getOrDefault(currentMonth, mutableMapOf())
        val currentArtistCount = monthMap.getOrDefault(artist, 0)
        monthMap[artist] = currentArtistCount + 1
        artistPlayCountsByMonth[currentMonth] = monthMap

        Log.d(
            "ListeningAnalytics",
            "Updated artist play count for $artist in month $currentMonth: ${currentArtistCount + 1}"
        )

        // Update play count for the song
        val currentPlayCount = songPlayCounts.getOrDefault(songKey, 0)
        songPlayCounts[songKey] = currentPlayCount + 1

        Log.d("ListeningAnalytics", "New song play: $songKey - count now ${currentPlayCount + 1}")

        logTopSongsByPlayCount()

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
            songStreakDays[songKey] = 1
        }

        songLastPlayed[songKey] = today

        // Update streak song
        updateStreakSong()

        startPlaybackTracking(musicViewModel, context, email)
    }

    fun getSongCoverUrl(title: String, artist: String): String? {
        val songKey = "${title}_${artist}"
        return songCoverUrls[songKey]
    }

    private fun updateTopSongAndArtist() {
        // Try to find a top song by listening duration first
        val topSongByDuration = songListeningDurations.maxByOrNull { it.value }

        if (topSongByDuration != null) {
            val (songKey, duration) = topSongByDuration
            val count = songPlayCounts[songKey] ?: 0
            val songTitle = songKey.split("_").first()
            _topSong.value = Triple(songTitle, count, duration)
        } else if (songPlayCounts.isNotEmpty()) {
            // Fallback to play counts if no listening durations
            val topSongByPlayCount = songPlayCounts.maxByOrNull { it.value }
            topSongByPlayCount?.let { (songKey, count) ->
                val duration = songListeningDurations[songKey] ?: 0L
                val songTitle = songKey.split("_").first()
                _topSong.value = Triple(songTitle, count, duration)
            }
        }        
        
        // Get the current month's artist play counts
        val currentMonth = getCurrentMonthKey()
        val currentMonthArtistCounts = artistPlayCountsByMonth[currentMonth]

        if (currentMonthArtistCounts != null && currentMonthArtistCounts.isNotEmpty()) {
            currentMonthArtistCounts.maxByOrNull { it.value }?.let { (artist, count) ->
                _topArtist.value = Pair(artist, count)
                Log.d(
                    "ListeningAnalytics",
                    "Top artist for month $currentMonth: $artist with $count plays"
                )
            }
        } else {
            _topArtist.value = Pair("", 0)
            Log.d("ListeningAnalytics", "No artist data for month $currentMonth")
        }
    }

    private fun updateStreakSong() {
        songStreakDays.filter { it.value >= 2 }.maxByOrNull { it.value }
            ?.let { (songKey, streakDays) ->
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

        // Save today's listening data
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        tokenManager.saveString(
            "${email}_listened_${today}",
            dailyListeningMap.getOrDefault(today, 0L).toString()
        )
        // Save artist play counts
        val artistByMonthData =
            artistPlayCountsByMonth.entries.joinToString("|") { (month, artists) ->
                val artistsData = artists.entries.joinToString(",") { "${it.key}:${it.value}" }
                "$month=$artistsData"
            }
        tokenManager.saveString("${email}_artist_counts_by_month", artistByMonthData)
        Log.d(
            "ListeningAnalytics",
            "Saved artist play counts by month for ${artistPlayCountsByMonth.size} months"
        )

        // Save song play counts
        val songData = songPlayCounts.entries.joinToString("|") { "${it.key}:${it.value}" }
        tokenManager.saveString("${email}_song_counts", songData)

        // Save song listening durations
        val durationData =
            songListeningDurations.entries.joinToString("|") { "${it.key}:${it.value}" }
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

        // Save daily listening data map
        val dailyData = dailyListeningMap.entries.joinToString("|") {
            "${it.key}:${it.value}"
        }
        tokenManager.saveString("${email}_daily_listening", dailyData)

        // Save cover URLs
        val coverUrlData = songCoverUrls.entries.joinToString("|") { "${it.key}:${it.value}" }
        tokenManager.saveString("${email}_cover_urls", coverUrlData)

        Log.d("ListeningAnalytics", "Saved ${songCoverUrls.size} cover URLs to preferences")
        Log.d(
            "ListeningAnalytics",
            "Saved ${songListeningDurations.size} song durations to preferences"
        )
    }

    fun loadFromPreferences(context: Context, email: String) {
        if (lastLoadedEmail == email) return
        lastLoadedEmail = email
        lastContext = context
        lastUserEmail = email

        val tokenManager = TokenManager(context)
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        // Check if we need to reset the daily counter (new day)
        if (lastLoadDate != today) {
            // Reset today's listening time - we'll load it from preferences if available
            _timeListened.value = 0

            // Update last load date
            lastLoadDate = today
            Log.d("ListeningAnalytics", "New day detected, resetting daily counter")
        }

        // Load time listened
        val timeStr = tokenManager.getString("${email}_time_listened")
        timeStr?.toLongOrNull()?.let {
            totalTimeListened.value = it
            if (_timeListened.value == 0L) {
                // Check if we have today's data specifically
                val todayTimeStr = tokenManager.getString("${email}_listened_${today}")
                todayTimeStr?.toLongOrNull()?.let { todayTime ->
                    _timeListened.value = todayTime
                    Log.d("ListeningAnalytics", "Loaded today's time listened: $todayTime seconds")
                }
            }
            Log.d("ListeningAnalytics", "Loaded total time listened: $it seconds")
        }

        // Load daily listening data
        val dailyDataStr = tokenManager.getString("${email}_daily_listening")
        dailyDataStr?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val date = parts[0]
                val seconds = parts[1].toLongOrNull() ?: 0L
                dailyListeningMap[date] = seconds
            }
        }
        // Load artist play counts by month
        val artistByMonthData = tokenManager.getString("${email}_artist_counts_by_month")
        artistByMonthData?.split("|")?.forEach { monthEntry ->
            val monthParts = monthEntry.split("=")
            if (monthParts.size == 2) {
                val month = monthParts[0]
                val artistsData = monthParts[1]

                val monthMap = mutableMapOf<String, Int>()
                artistsData.split(",").forEach { artistEntry ->
                    val artistParts = artistEntry.split(":")
                    if (artistParts.size == 2) {
                        val artist = artistParts[0]
                        val count = artistParts[1].toIntOrNull() ?: 0
                        monthMap[artist] = count
                    }
                }

                artistPlayCountsByMonth[month] = monthMap
            }
        }

        // Log the loaded data
        val currentMonth = getCurrentMonthKey()
        val currentMonthArtists = artistPlayCountsByMonth[currentMonth]
        if (currentMonthArtists != null) {
            Log.d(
                "ListeningAnalytics",
                "Loaded ${currentMonthArtists.size} artists for current month $currentMonth"
            )
        } else {
            Log.d("ListeningAnalytics", "No artist data found for current month $currentMonth")
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

        // Load song play counts
        val songCountData = tokenManager.getString("${email}_song_counts")
        songCountData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val song = parts[0]
                val count = parts[1].toIntOrNull() ?: 0
                songPlayCounts[song] = count
            }
        }

        // Load cover URLs
        val coverUrlData = tokenManager.getString("${email}_cover_urls")
        coverUrlData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val songKey = parts[0]
                val coverUrl = parts[1]
                songCoverUrls[songKey] = coverUrl
                Log.d("ListeningAnalytics", "Loaded cover URL for $songKey: $coverUrl")
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
                Log.d(
                    "ListeningAnalytics",
                    "${index + 1}. $songName: ${formatDuration(time)} (played $count times)"
                )
            }
        }
    }

    // Format time into hours and minutes
    fun formatTimeListened(): String {
        val seconds = _timeListened.value
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60

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
            if (seconds != null) {
                if (seconds > 0 || (hours == 0L && minutes == 0L)) {
                    append("${minutes}m ")
                }
            }
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

    // Modify getAllSongPlayData to include cover URLs
    fun getAllSongPlayData(): List<Quad<String, String, Int, String?>> {
        return songPlayCounts.map { (songKey, count) ->
            val parts = songKey.split("_")
            val title = parts.firstOrNull() ?: songKey
            val artist = parts.getOrNull(1) ?: "Unknown"
            val coverUrl = songCoverUrls[songKey]
            Quad(title, artist, count, coverUrl)
        }.sortedByDescending { it.third } // Sort by play count
    } 

    fun getAllArtistsData(): List<Pair<String, Int>> {
        val currentMonth = getCurrentMonthKey()
        val monthArtistCounts = artistPlayCountsByMonth[currentMonth] ?: mutableMapOf()

        return monthArtistCounts.map { (artist, count) ->
            Pair(artist, count)
        }.sortedByDescending { it.second } // Sort by play count
    }

    // Get daily listening data
    fun getDailyListeningData(): Map<String, Long> {
        val today = LocalDate.now()
        val result = mutableMapOf<String, Long>()

        // Get data for the last 7 days
        for (i in 0..6) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ISO_DATE)

            val minutes = if (i == 0) {
                _timeListened.value / 60
            } else {
                (dailyListeningMap[dateStr] ?: 0L) / 60
            }

            if (i == 0) {
                Log.d(
                    "ListeningAnalytics",
                    "Today's minutes: $minutes (from ${_timeListened.value} seconds)"
                )
            }

            result[dateStr] = minutes
        }

        return result
    }

    private var playbackJob: Job? = null

    fun pausePlayback() {
        playbackJob?.cancel()
        Log.d("ListeningAnalytics", "Playback paused")

        if (sessionStartTime > 0) {
            val sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000
            Log.d("ListeningAnalytics", "Session duration: $sessionDuration seconds")
            sessionStartTime = 0
        }
    }

    fun startPlaybackTracking(musicViewModel: MusicViewModel, context: Context, email: String) {
        val currentSong = musicViewModel.currentSong.value

        // Set session start time
        sessionStartTime = System.currentTimeMillis()

        currentSong?.let { song ->
            val songKey = "${song.title}_${song.artist}"
            var secondCounter = 0

            playbackJob?.cancel()
            playbackJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

                    val startTime = System.currentTimeMillis()

                    while (musicViewModel.isPlaying.value) {
                        _timeListened.value += 1
                        totalTimeListened.value += 1

                        val todaySeconds = dailyListeningMap.getOrDefault(today, 0L) + 1
                        dailyListeningMap[today] = todaySeconds

                        songListeningDurations[songKey] =
                            songListeningDurations.getOrDefault(songKey, 0L) + 1

                        secondCounter++

                        if (secondCounter >= 300) {
                            saveToPreferences(context, email)
                            secondCounter = 0
                            Log.d("ListeningAnalytics", "Saved listening data after 5 minutes")
                        }

                        delay(1000)
                    }

                    val endTime = System.currentTimeMillis()
                    val actualListenedSeconds = (endTime - startTime) / 1000

                    sessionStartTime = 0

                    if (secondCounter > 0) {
                        saveToPreferences(context, email)
                        Log.d(
                            "ListeningAnalytics",
                            "Saved remaining listening data (${secondCounter}s)"
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ListeningAnalytics", "Error in playback tracking", e)
                }
            }
        }
    }

    // Log top songs by play count
    fun logTopSongsByPlayCount() {
        val topPlayedSongs = songPlayCounts.toList()
            .sortedByDescending { it.second }
            .take(5)

        if (topPlayedSongs.isNotEmpty()) {
            Log.d("ListeningAnalytics", "===== TOP SONGS BY PLAY COUNT =====")
            topPlayedSongs.forEachIndexed { index, (songKey, count) ->
                val songName = songKey.split("_").firstOrNull() ?: songKey
                val artist = songKey.split("_").getOrNull(1) ?: "Unknown"
                val duration = songListeningDurations[songKey] ?: 0L
                Log.d(
                    "ListeningAnalytics",
                    "${index + 1}. $songName by $artist: played $count times (total listening time: ${
                        formatDuration(duration)
                    })"
                )
            }
        } else {
            Log.d("ListeningAnalytics", "No song play data available yet")
        }
    }

    // Reset all analytics data
    fun resetAllData(context: Context, email: String) {
        _timeListened.value = 0
        totalTimeListened.value = 0
        songPlayCounts.clear()
        artistPlayCountsByMonth.clear()
        songListeningDurations.clear()
        songLastPlayed.clear()
        songStreakDays.clear()
        songCoverUrls.clear()

        // Reset the StateFlow objects explicitly to empty values
        _topSong.value = Triple("", 0, 0L)
        _topArtist.value = Pair("", 0)
        _streakSong.value = Triple("", "", 0)

        // Clear the last played song key to ensure next play is counted as new
        _lastPlayedSongKey = null

        lastLoadDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        val tokenManager = TokenManager(context)
        tokenManager.saveString("${email}_time_listened", "0")
        tokenManager.saveString("${email}_listened_${lastLoadDate}", "0")
        tokenManager.saveString("${email}_daily_listening", "")
        tokenManager.saveString("${email}_artist_counts_by_month", "")
        tokenManager.saveString("${email}_song_counts", "")
        tokenManager.saveString("${email}_song_durations", "")
        tokenManager.saveString("${email}_song_streaks", "")
        tokenManager.saveString("${email}_last_played", "")
        tokenManager.saveString("${email}_cover_urls", "")

        // Ensure UI gets updated immediately after reset
        updateTopSongAndArtist()
        updateStreakSong()

        Log.d("ListeningAnalytics", "All data reset!")
    }    // Get all songs by a specific artist for the current month

    fun getSongsByArtist(artist: String): List<String> {
        val currentMonth = getCurrentMonthKey()
        val monthArtists = artistPlayCountsByMonth[currentMonth]?.keys ?: emptySet()

        return if (artist in monthArtists) {
            songPlayCounts.filter { (songKey, _) ->
                val parts = songKey.split("_")
                val songArtist = parts.getOrNull(1) ?: ""
                songArtist == artist
            }.map { (songKey, _) ->
                songKey.split("_").firstOrNull() ?: songKey
            }
        } else {
            emptyList()
        }
    }

    // Monthly analytics data class
    data class MonthlyAnalytics(
        val timeListened: String?,
        val topSong: String?,
        val topArtist: String?,
        val streak: Int?
    )

    // Get monthly analytics data
    fun getMonthlyAnalytics(): Map<String, MonthlyAnalytics> {
        val monthlyAnalytics = mutableMapOf<String, MonthlyAnalytics>()

        val currentYear = LocalDate.now().year
        for (month in 1..12) {
            val monthName = LocalDate.of(currentYear, month, 1).month.name.lowercase()
                .replaceFirstChar { it.uppercase() }

            val timeListened = dailyListeningMap.filterKeys {
                LocalDate.parse(it).monthValue == month
            }.values.sum()

            val topSong = songPlayCounts.filterKeys {
                val date = songLastPlayed[it] ?: return@filterKeys false
                date.monthValue == month
            }.maxByOrNull { it.value }?.key?.split("_")?.firstOrNull()

            val monthKey = "${currentYear}-${month.toString().padStart(2, '0')}"
            val monthArtistCounts = artistPlayCountsByMonth[monthKey] ?: mutableMapOf()
            val topArtist = monthArtistCounts.maxByOrNull { it.value }?.key

            val streak = songStreakDays.filterKeys {
                val date = songLastPlayed[it] ?: return@filterKeys false
                date.monthValue == month
            }.maxByOrNull { it.value }?.value

            // Exclude months where all data is null or zero
            if (timeListened > 0 || topSong != null || topArtist != null || streak != null) {
                monthlyAnalytics[monthName] = MonthlyAnalytics(
                    timeListened = if (timeListened > 0) formatDuration(timeListened) else null,
                    topSong = topSong,
                    topArtist = topArtist,
                    streak = streak
                )
            }
        }

        return monthlyAnalytics
    }
}