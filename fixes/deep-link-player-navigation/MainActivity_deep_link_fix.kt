// This is a partial fix for MainActivity.kt to properly handle deep link navigation
// The main issues are:
// 1. Navigation is happening too early before the NavHost is ready
// 2. We need to ensure the song is loaded without playing
// 3. We need to set the online playlist to avoid errors

// Changes needed in MainActivity.kt:

// 1. Remove the startDestination manipulation for deep links
// 2. Handle navigation in setContent after NavHost is ready
// 3. Use loadSongWithoutPlaying instead of playSong for deep links

// Here's the fixed code for the deep link handling parts:

// In onCreate method:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate called")

    // Handle deep links
    handleIntent(intent)

    // ... rest of onCreate code ...
}

// In setContent section, fix the LaunchedEffect for deep link handling:
LaunchedEffect(navController) {
    if (deepLinkPending && deepLinkSong != null) {
        Log.d(TAG, "Processing deep link in composition")
        
        // First ensure we have a playlist context
        val dummyPlaylist = listOf(deepLinkSong!!)
        musicViewModel.setOnlinePlaylist(dummyPlaylist, "deeplink")
        
        // Load song without playing - just display it in the player screen
        musicViewModel.loadSongWithoutPlaying(
            deepLinkSong!!, 
            context, 
            fromOnlinePlaylist = true, 
            onlineType = "deeplink",
            onlineSongId = deepLinkSongId
        )
        
        // Navigate to player after a small delay to ensure NavHost is ready
        delay(200)
        navController.navigate("player")
        
        deepLinkPending = false
        deepLinkSong = null
        deepLinkSongId = null
    }
}

// Fix the handleIntent method to properly set the deep link flag:
private fun handleIntent(intent: Intent?) {
    val data = intent?.data
    if (data != null) {
        var songId: Int? = null
        
        when {
            // Handle purrytify://song/{id}
            data.scheme == "purrytify" && data.host == "song" -> {
                songId = data.lastPathSegment?.toIntOrNull()
            }
            // Handle https://purrytify.com/open/song/{id}
            data.scheme == "https" && data.host == "purrytify.com" && data.path?.startsWith("/open/song") == true -> {
                songId = data.lastPathSegment?.toIntOrNull()
            }
        }
        
        if (songId != null) {
            Log.d(TAG, "Handling deep link for song ID: $songId")
            // Clear any previous deep link data
            deepLinkSong = null
            deepLinkPending = false
            deepLinkSongId = null
            
            // Load the song and navigate to player
            lifecycleScope.launch {
                try {
                    val trendingApi = RetrofitClient.getInstance(applicationContext).create(TrendingApiService::class.java)
                    val response = trendingApi.getSongById(songId)
                    if (response.isSuccessful) {
                        response.body()?.let { onlineSong ->
                            Log.d(TAG, "Successfully fetched song: ${onlineSong.title}")
                            
                            // Convert OnlineSong to Song
                            val song = Song(
                                title = onlineSong.title,
                                artist = onlineSong.artist,
                                coverUri = onlineSong.artworkUrl,
                                uri = onlineSong.audioUrl,
                                duration = onlineSong.duration
                            )
                            
                            // Store deep link data
                            deepLinkSong = song
                            deepLinkPending = true
                            deepLinkSongId = songId
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch song: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching song for deep link", e)
                }
            }
        }
    }
}

// Also need to handle onNewIntent for when app is already running:
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Log.d(TAG, "onNewIntent called")
    setIntent(intent)
    handleIntent(intent)
}
