package com.example.purrytify.ui.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.purrytify.ui.components.BottomNavBar
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel

@Composable
fun TopArtistsScreen(
    navController: NavController,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val artistData = remember { ListeningAnalytics.getAllArtistsData() }
    val artistCount = artistData.size
    val context = LocalContext.current
    var showResetConfirmation by remember { mutableStateOf(false) }

    val gradientColors = listOf(
        Color(0xFF095256),
        Color(0xFF121212)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Top Artists",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("You listened to ")
                        withStyle(
                            style = SpanStyle(
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("$artistCount different artists")
                        }
                        append(" this month")
                    },
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(bottom = 56.dp)
            ) {
                itemsIndexed(artistData) { index, (artist, playCount) ->
                    val songsByArtist = ListeningAnalytics.getSongsByArtist(artist)

                    TopArtistItem(
                        index = index + 1,
                        artist = artist,
                        playCount = playCount,
                        songCount = songsByArtist.size
                    )

                    Divider(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { navController.navigate("time_listened") }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Listening Time",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { navController.navigate("top_songs") }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Top Songs",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1DB954))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Top Artists",
                    style = TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        BottomNavBar(
            navController = navController,
            musicViewModel = musicViewModel,
            songViewModel = songViewModel,
            currentRoute = "top_artists",
            onMiniPlayerClick = onNavigateToPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Reset confirmation dialog
        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { showResetConfirmation = false },
                title = {
                    Text(
                        text = "Reset Data",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to reset all listening statistics? This action cannot be undone.",
                        color = Color.White
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Reset all analytics data
                            val userEmail = songViewModel.currentUserEmail.value
                            if (userEmail.isNotEmpty()) {
                                ListeningAnalytics.resetAllData(context, userEmail)
                                Toast.makeText(context, "Listening statistics reset", Toast.LENGTH_SHORT).show()
                                // Refresh the current screen
                                navController.navigate("home") {
                                    popUpTo("home")
                                }
                                navController.navigate("top_artists")
                            }
                            showResetConfirmation = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showResetConfirmation = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242)
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun TopArtistItem(
    index: Int,
    artist: String,
    playCount: Int,
    songCount: Int
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format("%02d", index),
            style = TextStyle(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.width(40.dp)
        )

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = artist,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "$songCount songs",
                style = TextStyle(
                    color = Color.Gray,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}