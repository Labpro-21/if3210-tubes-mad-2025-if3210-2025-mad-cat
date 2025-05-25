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

    // Track song play counts
    private val songPlayCounts = mutableMapOf<String, Int>()

    // Track song cover image URLs
    private val songCoverUrls = mutableMapOf<String, String>()

    // Track currently playing
    private var _lastPlayedSongKey: String? = null

    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun initializeAnalytics(context: Context, email: String) {
        if (lastLoadedEmail == email) return

        // Reset counters first
        _timeListened.value = 0

        // Load data from preferences
        loadFromPreferences(context, email)

        ListenedSongsTracker.loadListenedSongs(email, context)

        // Log current state after init
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

    private var sessionStartTime: Long = 0
    fun startPlayback(song: Song, musicViewModel: MusicViewModel, context: Context, email: String) {
        val currentSong = musicViewModel.currentSong.value
        val songKey = "${email}_${song.title}_${song.artist}"

        if (song.coverUri.isNotEmpty()) {
            val isOnlineSong = song.uri.startsWith("http")
            
            songCoverUrls[songKey] = song.coverUri
            Log.d("ListeningAnalytics", "Saved cover URL for $songKey: ${song.coverUri} (online song: $isOnlineSong)")

            saveToPreferences(context, email)
        }

        if (_lastPlayedSongKey == songKey) {
            Log.d("ListeningAnalytics", "Same song detected, not incrementing play count: $songKey")
            startPlaybackTracking(musicViewModel, context, email)
            return
        }

        _lastPlayedSongKey = songKey

        currentlyPlayingSong = currentSong
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
        updateTopSongAndArtist()
        
        // Log the current top song for verification
        val (topSongTitle, topSongCount, topSongDuration) = _topSong.value
        Log.d("ListeningAnalytics", "Current top song after update: $topSongTitle (played $topSongCount times)")

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
    }    fun getSongCoverUrl(title: String, artist: String): String? {
        val songKey = "${title}_${artist}"
        val coverUrl = songCoverUrls[songKey]
        
        if (coverUrl != null) {
            Log.d("ListeningAnalytics", "Found cover URL for $songKey: $coverUrl")
        } else {
            Log.d("ListeningAnalytics", "No cover URL found for $songKey")
        }
        
        return coverUrl
    }    private fun updateTopSongAndArtist() {
        // Prioritize play counts first to immediately reflect newly played songs
        if (songPlayCounts.isNotEmpty()) {
            val topSongByPlayCount = songPlayCounts.maxByOrNull { it.value }
            topSongByPlayCount?.let { (songKey, count) ->
                val duration = songListeningDurations[songKey] ?: 0L
                val songTitle = songKey.split("_").first()
                _topSong.value = Triple(songTitle, count, duration)
                Log.d("ListeningAnalytics", "Updated top song to: $songTitle (played $count times, listened for ${formatDuration(duration)})")
            }
        } else if (songListeningDurations.isNotEmpty()) {
            // Fallback to durations if somehow we have durations but no play counts
            val topSongByDuration = songListeningDurations.maxByOrNull { it.value }
            val (songKey, duration) = topSongByDuration!!
            val count = songPlayCounts[songKey] ?: 0
            val songTitle = songKey.split("_").first()
            _topSong.value = Triple(songTitle, count, duration)
        }        

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
        val streakSongs = songStreakDays.filter { it.value >= 2 }
                                       .entries
                                       .sortedWith(
                                           compareByDescending<Map.Entry<String, Int>> { it.value }
                                           .thenBy { it.key }
                                       )

        if (streakSongs.isNotEmpty()) {
            val (songKey, streakDays) = streakSongs.first()
            val parts = songKey.split("_")
            if (parts.size >= 3) {
                val songTitle = parts[1]  // Skip email, ambil title
                val artistName = parts[2]  // Ambil artist
                _streakSong.value = Triple(songTitle, artistName, streakDays)
                Log.d("ListeningAnalytics", "Updated streak song to: $songTitle by $artistName ($streakDays days)")
            } else {
                _streakSong.value = Triple("", "", 0)
            }
        } else {
            _streakSong.value = Triple("", "", 0)
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
        val coverUrlData = songCoverUrls.entries.joinToString("|") { 
            "${it.key}:${it.value}"
        }
        tokenManager.saveString("${email}_cover_urls", coverUrlData)

        // Save Sound Capsule data
        val topSongsData = getAllSongPlayData().joinToString("|") { "${it.first}:${it.second}:${it.third}:${it.fourth}" }
        tokenManager.saveString("${email}_sound_capsule_top_songs", topSongsData)

        val topArtistsData = getAllArtistsData().joinToString("|") { "${it.first}:${it.second}" }
        tokenManager.saveString("${email}_sound_capsule_top_artists", topArtistsData)

        val timeListenedData = _timeListened.value.toString()
        tokenManager.saveString("${email}_sound_capsule_time_listened", timeListenedData)

        Log.d("ListeningAnalytics", "Saved ${songCoverUrls.size} cover URLs to preferences")
        
        // Log some examples for debugging
        if (songCoverUrls.isNotEmpty()) {
            songCoverUrls.entries.take(3).forEach { (key, url) ->
                Log.d("ListeningAnalytics", "Sample saved cover URL for $key: $url")
            }
        }
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

        // Clear existing data
        songStreakDays.clear()
        songLastPlayed.clear()
        
        val tokenManager = TokenManager(context)
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        if (lastLoadDate != today) {
            _timeListened.value = 0

            lastLoadDate = today
            Log.d("ListeningAnalytics", "New day detected, resetting daily counter")
        }

        // Load time listened
        val timeStr = tokenManager.getString("${email}_time_listened")
        timeStr?.toLongOrNull()?.let {
            totalTimeListened.value = it
            if (_timeListened.value == 0L) {
                val todayTimeStr = tokenManager.getString("${email}_listened_${today}")
                todayTimeStr?.toLongOrNull()?.let { todayTime ->
                    _timeListened.value = todayTime
                    Log.d("ListeningAnalytics", "Loaded today's time listened: $todayTime seconds")
                }
            }
            Log.d("ListeningAnalytics", "Loaded total time listened: $it seconds")
        }

        val dailyDataStr = tokenManager.getString("${email}_daily_listening")
        dailyDataStr?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val date = parts[0]
                val seconds = parts[1].toLongOrNull() ?: 0L
                dailyListeningMap[date] = seconds
            }
        }

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

        artistPlayCountsByMonth.clear()

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
                }
            }
        }
        
        songPlayCounts.clear()

        val songCountData = tokenManager.getString("${email}_song_counts")
        songCountData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val song = parts[0]
                val count = parts[1].toIntOrNull() ?: 0
                songPlayCounts[song] = count
            }
        }

        val coverUrlData = tokenManager.getString("${email}_cover_urls")
        coverUrlData?.split("|")?.forEach {
            val colonIndex = it.indexOf(":")
            if (colonIndex > 0) {
                val songKey = it.substring(0, colonIndex)
                val coverUrl = it.substring(colonIndex + 1)
                songCoverUrls[songKey] = coverUrl
                Log.d("ListeningAnalytics", "Loaded cover URL for $songKey: $coverUrl")
            }
        }

        // Load Sound Capsule data
        val topSongsData = tokenManager.getString("${email}_sound_capsule_top_songs")
        topSongsData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 4) {
                val title = parts[0]
                val artist = parts[1]
                val playCount = parts[2].toIntOrNull() ?: 0
                val coverUrl = parts[3]
                songPlayCounts["${title}_${artist}"] = playCount
                songCoverUrls["${title}_${artist}"] = coverUrl
            }
        }

        val topArtistsData = tokenManager.getString("${email}_sound_capsule_top_artists")
        topArtistsData?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val artist = parts[0]
                val playCount = parts[1].toIntOrNull() ?: 0
                val currentMonth = getCurrentMonthKey()
                val monthMap = artistPlayCountsByMonth.getOrDefault(currentMonth, mutableMapOf())
                monthMap[artist] = playCount
                artistPlayCountsByMonth[currentMonth] = monthMap
            }
        }

        val timeListenedData = tokenManager.getString("${email}_sound_capsule_time_listened")
        timeListenedData?.toLongOrNull()?.let {
            _timeListened.value = it
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

    fun getAllSongListeningData(): List<Triple<String, Int, Long>> {
        return songListeningDurations.map { (songKey, duration) ->
            val title = songKey.split("_").firstOrNull() ?: songKey
            val count = songPlayCounts[songKey] ?: 0
            Triple(title, count, duration)
        }.sortedByDescending { it.third }
    }    

    fun getAllSongPlayData(): List<Quad<String, String, Int, String?>> {
        return songPlayCounts.map { (songKey, count) ->
            val parts = songKey.split("_")
            val title = if (parts.size >= 2) parts[1] else songKey
            val artist = if (parts.size >= 3) parts[2] else "Unknown"
            val coverUrl = songCoverUrls[songKey]
            
            // Log for debugging
            if (coverUrl != null) {
                Log.d("ListeningAnalytics", "Getting cover for $title by $artist: $coverUrl")
            }
            
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

    fun getArtistCoverUrl(artist: String): String? {
        val songKeys = songPlayCounts.keys.filter {
            val parts = it.split("_")
            val songArtist = parts.getOrNull(1) ?: ""
            songArtist == artist
        }
        
        val possibleCovers = songKeys.mapNotNull { songKey -> 
            val coverUrl = songCoverUrls[songKey]
            if (coverUrl != null && coverUrl.isNotEmpty()) {
                Log.d("ListeningAnalytics", "Found cover for artist $artist: $coverUrl")
                coverUrl
            } else null 
        }
        
        return if (possibleCovers.isNotEmpty()) {
            val randomCover = possibleCovers.random()
            Log.d("ListeningAnalytics", "Selected random cover for $artist: $randomCover")
            randomCover
        } else {
            Log.d("ListeningAnalytics", "No covers found for artist $artist")
            null
        }
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
        val streak: Int?,
        val topSongCoverUrl: String? = null,
        val topArtistCoverUrl: String? = null,
        val streakSong: String? = null,
        val streakSongCoverUrl: String? = null
    )   
    
    fun getMonthlyAnalytics(): Map<String, MonthlyAnalytics> {
        val monthlyAnalytics = mutableMapOf<String, MonthlyAnalytics>()

        val currentYear = LocalDate.now().year
        for (month in 1..12) {
            val monthName = LocalDate.of(currentYear, month, 1).month.name.lowercase()
                .replaceFirstChar { it.uppercase() }

            val timeListened = dailyListeningMap.filterKeys {
                LocalDate.parse(it).monthValue == month
            }.values.sum()

            val topSongData = songPlayCounts.filterKeys {
                val date = songLastPlayed[it] ?: return@filterKeys false
                date.monthValue == month
            }.maxByOrNull { it.value }

            val topSong = topSongData?.key?.split("_")?.getOrNull(1) // Skip email, ambil title
            val topSongCoverUrl = topSongData?.key?.let { key -> 
                val parts = key.split("_")
                if (parts.size >= 3) {
                    getSongCoverUrl(parts[1], parts[2]) // title, artist
                } else null
            }

            val monthKey = "${currentYear}-${month.toString().padStart(2, '0')}"
            val monthArtistCounts = artistPlayCountsByMonth[monthKey] ?: mutableMapOf()
            val topArtist = monthArtistCounts.maxByOrNull { it.value }?.key
            val topArtistCoverUrl = topArtist?.let { getArtistCoverUrl(it) }            // Get streak data consistently sorted by both streak value and song key
            val streakEntries = songStreakDays.filterKeys {
                val date = songLastPlayed[it] ?: return@filterKeys false
                date.monthValue == month
            }.entries.sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key } // Secondary sort by song key for consistency
            )
            
            // Get the streak value and song details
            val streak = if (streakEntries.isNotEmpty()) streakEntries.first().value else null
            val streakSongKey = if (streakEntries.isNotEmpty()) streakEntries.first().key else null
            
            // Extract streak song info and its cover URL
            val streakSong = streakSongKey?.split("_")?.firstOrNull()
            val streakSongCoverUrl = streakSongKey?.let { songCoverUrls[it] }
            
            if (streak != null && streak > 0 && streakSong != null) {
                Log.d("ListeningAnalytics", "Month $monthName has streak: $streak days for song: $streakSong (cover: $streakSongCoverUrl)")
            }            // Exclude months where all data is null or zero
            if (timeListened > 0 || topSong != null || topArtist != null || streak != null) {
                monthlyAnalytics[monthName] = MonthlyAnalytics(
                    timeListened = if (timeListened > 0) formatDuration(timeListened) else null,
                    topSong = topSong,
                    topArtist = topArtist,
                    streak = streak,
                    topSongCoverUrl = topSongCoverUrl,
                    topArtistCoverUrl = topArtistCoverUrl,
                    streakSong = streakSong,
                    streakSongCoverUrl = streakSongCoverUrl
                )
            }
        }

        return monthlyAnalytics
    }   

    fun resetLastLoadedEmail() {
        // Make sure we save any pending analytics data before resetting
        if (lastLoadedEmail != null && lastContext != null) {
            Log.d("ListeningAnalytics", "Saving analytics data before logout for: $lastLoadedEmail")
            saveToPreferences(lastContext!!, lastLoadedEmail!!)
        }
        
        // Reset tracking variable but keep the data in memory
        lastLoadedEmail = null
        Log.d("ListeningAnalytics", "Reset lastLoadedEmail to force reload on next login")
    }

    // Get top song cover URL
    fun getTopSongCoverUrl(): String? {
        val topSongs = getAllSongPlayData()
        if (topSongs.isNotEmpty()) {
            val topSong = topSongs.first() // First item is the most played song
            val title = topSong.first
            val artist = topSong.second
            val coverUrl = topSong.fourth
            
            Log.d("ListeningAnalytics", "Top Song Cover URL for $title by $artist: $coverUrl")
            return coverUrl
        }
        return null
    }
    
    // Get top artist cover URL
    fun getTopArtistCoverUrl(): String? {
        val topArtists = getAllArtistsData()
        if (topArtists.isNotEmpty()) {
            val topArtist = topArtists.first().first // First artist is the most played one
            return getArtistCoverUrl(topArtist)
        }
        return null
    }

    fun getStreakSongInfo(email: String): Triple<String, String, Int> {
        val userStreaks = songStreakDays.filterKeys { it.startsWith("${email}_") }
        
        val maxStreak = userStreaks.maxByOrNull { it.value }
        
        if (maxStreak != null) {
            val parts = maxStreak.key.split("_")
            val songTitle = parts.getOrNull(1) ?: ""
            val artistName = parts.getOrNull(2) ?: "Unknown"
            return Triple(songTitle, artistName, maxStreak.value)
        }
        
        return Triple("", "", 0)
    }
}