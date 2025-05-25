import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.data.PlayHistoryTracker
import com.example.purrytify.ui.components.AdaptiveNavigation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.purrytify.ui.screens.Song
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import com.example.purrytify.ui.viewmodel.HomeViewModel
import com.example.purrytify.ui.viewmodel.HomeViewModelFactory
import com.example.purrytify.ui.components.ChartsSection
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import java.io.File
import com.example.purrytify.data.preferences.UserProfileManager
import com.example.purrytify.data.preferences.UserProfile
import androidx.compose.runtime.mutableStateOf
import com.example.purrytify.data.api.RetrofitClient

@Composable
fun HomeScreen(
    navController: NavController,
    musicViewModel: MusicViewModel = viewModel(),
    songViewModel: SongViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(context))
    val tokenManager = remember { TokenManager(context) }
    val userProfileManager = remember { UserProfileManager(context) }
    val userEmail = tokenManager.getEmail() ?: ""

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var userCountryCode by remember { mutableStateOf("ID") }

    LaunchedEffect(currentRoute) {
        if (currentRoute == "home" || currentRoute == null) {
            try {
                val response = RetrofitClient.apiService.getProfile()
                if (response.isSuccessful) {
                    response.body()?.let { profile ->
                        val localProfile = UserProfile(
                            email = profile.email,
                            name = profile.username ?: "",
                            age = 0,
                            gender = "",
                            country = profile.location ?: "ID",
                            profileImageUrl = profile.profilePhoto
                        )
                        userProfileManager.saveUserProfile(localProfile)
                        userProfile = localProfile
                        userCountryCode = profile.location ?: "ID"
                        Log.d("HomeScreen", "Fetched fresh profile with country: ${profile.location}")
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error fetching profile", e)
                // Fall back to local cache if network fails
                userProfile = userProfileManager.getUserProfile(userEmail)
                userCountryCode = userProfile?.country ?: "ID"
            }
        }
    }
    
    val userCountryName = homeViewModel.getSupportedCountries()[userCountryCode] ?: "Indonesia"

    // Log the country code being used
    LaunchedEffect(userCountryCode) {
        Log.d("HomeScreen", "Current user country code: $userCountryCode for user: $userEmail")
    }

    val scope = rememberCoroutineScope()

    val allSongs = songViewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())

    val currentSong by musicViewModel.currentSong.collectAsState()
    var recentlyPlayedSongs by remember {
        mutableStateOf<List<Song>>(PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager))
    }

    var isLoading by remember { mutableStateOf(true) }

    var previousSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(userEmail) {
        Log.d("HomeScreen", "Fetching recently played songs for: $userEmail")
        recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager)
        Log.d("HomeScreen", "Recently played songs loaded: ${recentlyPlayedSongs.map { it.title }}")
    }

    LaunchedEffect(currentSong, userEmail) {
        if (currentSong != null && currentSong != previousSong) {
            PlayHistoryTracker.addSongToHistory(userEmail, currentSong!!, tokenManager, context)
            recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager)
            previousSong = currentSong
        }

        if (recentlyPlayedSongs.isEmpty() && allSongs.value.isNotEmpty()) {
            val initialSongs = allSongs.value.shuffled().take(5)
            initialSongs.forEach {
                PlayHistoryTracker.addSongToHistory(userEmail, it, tokenManager, context)
            }
            recentlyPlayedSongs = PlayHistoryTracker.getRecentlyPlayedSongs(userEmail, tokenManager)
        }

        isLoading = false
    }

    LaunchedEffect(allSongs.value) {
        if (allSongs.value.isNotEmpty()) {
            isLoading = false
        }

        songViewModel.logDatabaseContents()
    }

    AdaptiveNavigation(
        navController = navController,
        musicViewModel = musicViewModel,
        songViewModel = songViewModel,
        currentRoute = "home",
        onMiniPlayerClick = onNavigateToPlayer
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF1DB954)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212)),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Home",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    navController.navigate("qr_scanner")
                                }
                        )
                    }
                }
                
                // Charts section
                item {
                    ChartsSection(
                        onGlobalClick = {
                            navController.navigate("top_charts/global")
                        },
                        onCountryClick = {
                            navController.navigate("top_charts/$userCountryCode/$userCountryName")
                        },
                chartTitle = "Top 10 Country",
                        countryName = userCountryName,
                        countryCode = userCountryCode,
                        isCountrySupported = homeViewModel.isCountrySupported(userCountryCode)
                    )
                }

                // Top Mixes section
                item {
                    TopMixesSection(
                        likedSongs = allSongs.value,
                        recentlyPlayedSongs = recentlyPlayedSongs,
                        userCountryCode = userCountryCode,
                        onPlaylistClick = { mixName ->
                            navController.navigate("mix_playlist/$mixName")
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item {
                    Text(
                        text = "New songs",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp)
                    )
                }

                item {
                    if (allSongs.value.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No songs in your library yet",
                                style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                            )
                        }
                    } else {
                        val newSongs = allSongs.value.reversed()

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.height(180.dp)
                        ) {
                            items(newSongs) { song ->
                                NewSongItem(
                                    song = song,
                                    onClick = {
                                        scope.launch {
                                            musicViewModel.playSong(song, context)
                                            onNavigateToPlayer()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Text(
                        text = "Recently played",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp)
                    )
                }

                if (recentlyPlayedSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recently played songs",
                                style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                            )
                        }
                    }
                } else {
                    items(recentlyPlayedSongs.size) { index ->
                        val song = recentlyPlayedSongs[index]
                        RecentlySongItem(
                            song = song,
                            onClick = {
                                scope.launch {
                                    musicViewModel.playSong(song, context)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewSongItem(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        val imageModel = when {
            song.coverUri.startsWith("http://") || song.coverUri.startsWith("https://") -> {
                // For online songs with URLs
                song.coverUri
            }
            song.coverUri.isNotEmpty() && File(song.coverUri).exists() -> {
                // For local songs with file paths
                File(song.coverUri)
            }
            else -> {
                // Fallback placeholder
                "https://example.com/placeholder.jpg"
            }
        }

        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = song.title,
            style = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = song.artist,
            style = TextStyle(
                color = Color.Gray,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RecentlySongItem(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModel = when {
            song.coverUri.startsWith("http://") || song.coverUri.startsWith("https://") -> {
                // For online songs
                song.coverUri
            }
            song.coverUri.isNotEmpty() && File(song.coverUri).exists() -> {
                // For local songs with file paths
                File(song.coverUri)
            }
            else -> {
                // Fallback placeholder
                null
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = song.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = song.artist,
                style = TextStyle(
                    color = Color.Gray,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TopMixesSection(
    likedSongs: List<Song>,
    recentlyPlayedSongs: List<Song>,
    userCountryCode: String,
    onPlaylistClick: (String) -> Unit
) {
    // Get context and view models
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(context))
    
    // Mix names
    val mixOneName = "Your Daily Mix"
    val mixTwoName = "Favorites Mix"
    
    // Daily Mix: Fetch global and country top songs
    val globalTopSongs = homeViewModel.globalTopSongs.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val countryTopSongs = homeViewModel.countryTopSongs.collectAsStateWithLifecycle(initialValue = emptyList()).value

    LaunchedEffect(userCountryCode) {
        Log.d("TopMixesSection", "Country code changed to: $userCountryCode, fetching new songs")
        homeViewModel.fetchGlobalTopSongs()
        homeViewModel.fetchCountryTopSongs(userCountryCode)
    }

    // Convert online songs to local
    val globalSongs = remember(globalTopSongs) {
        globalTopSongs.map { onlineSong ->
            Song(
                title = onlineSong.title,
                artist = onlineSong.artist,
                coverUri = onlineSong.artworkUrl,
                uri = onlineSong.audioUrl,
                duration = onlineSong.duration
            )
        }
    }
    
    val countrySongs = remember(countryTopSongs) {
        countryTopSongs.map { onlineSong ->
            Song(
                title = onlineSong.title,
                artist = onlineSong.artist,
                coverUri = onlineSong.artworkUrl,
                uri = onlineSong.audioUrl,
                duration = onlineSong.duration
            )
        }
    }
    
    // Daily mix (15 songs max)
    val dailyMixSongs = remember(globalSongs, countrySongs, recentlyPlayedSongs) {
        val alreadyListenedTitles = recentlyPlayedSongs.map { "${it.title}_${it.artist}" }.toSet()
        val unheardGlobalSongs = globalSongs
            .filter { song -> 
                !alreadyListenedTitles.contains("${song.title}_${song.artist}")
            }
            .take(8)
        val unheardCountrySongs = countrySongs
            .filter { song -> 
                !alreadyListenedTitles.contains("${song.title}_${song.artist}") &&
                !unheardGlobalSongs.any { it.title == song.title && it.artist == song.artist }
            }
            .take(7)
        val combinedList = (unheardGlobalSongs + unheardCountrySongs).take(15)
        if (combinedList.size < 15) {
            val additionalSongs = likedSongs
                .filter { song ->
                    !alreadyListenedTitles.contains("${song.title}_${song.artist}") &&
                    !combinedList.any { it.title == song.title && it.artist == song.artist }
                }
                .shuffled()
                .take(15 - combinedList.size)
                
            combinedList + additionalSongs
        } else {
            combinedList
        }
    }
                        
    // Favorites Mix: combination of liked songs and frequently played songs
    val favoritesSongs = likedSongs.take(7).toMutableList()
    if (favoritesSongs.size < 7) {
        val additionalSongs = recentlyPlayedSongs.shuffled()
            .filter { recent -> !favoritesSongs.any { it.title == recent.title && it.artist == recent.artist } }
            .take(7 - favoritesSongs.size)
        favoritesSongs.addAll(additionalSongs)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Top Mixes",
            style = TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(180.dp)
        ) {
            // Daily Mix Item
            item {
                PlaylistItem(
                    name = mixOneName,
                    description = "Fresh tunes",
                    songCount = dailyMixSongs.size,
                    coverColors = listOf(Color(0xFF1DB954), Color(0xFF191414)),
                    onClick = { onPlaylistClick(mixOneName) }
                )
            }
            
            // Favorites Mix Item
            item {
                PlaylistItem(
                    name = mixTwoName,
                    description = "Songs You Love",
                    songCount = favoritesSongs.size,
                    coverColors = listOf(Color(0xFF9C27B0), Color(0xFF3F51B5)),
                    onClick = { onPlaylistClick(mixTwoName) }
                )
            }
        }
    }
}

@Composable
fun PlaylistItem(
    name: String,
    description: String,
    songCount: Int,
    coverColors: List<Color>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = coverColors,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(130f, 130f)
                    )
                )
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = name,
            style = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = description,
            style = TextStyle(
                color = Color.Gray,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "$songCount songs",
            style = TextStyle(
                color = Color.Gray,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}