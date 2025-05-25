package com.example.purrytify.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
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

    val gradientColors = listOf(Color(0xFF000000), Color(0xFF1B1B1B))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = gradientColors, startY = 0f, endY = 1200f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Text(
                text = "May 2025",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Text(
                text = buildAnnotatedString {
                    append("You listened to ")
                    withStyle(style = SpanStyle(color = Color(0xFF669BEC), fontWeight = FontWeight.Bold)) {
                        append("$artistCount different artists ")
                    }
                    append("this month.")
                },
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            LazyColumn(
                modifier = Modifier
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Divider(
                        color = Color.DarkGray.copy(alpha = 0.3f),
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
            listOf(
                "Listening Time" to "time_listened",
                "Top Songs" to "top_songs",
                "Top Artists" to null
            ).forEach { (label, route) ->
                val isActive = route == null
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF1DB954) else Color(0xFF1E1E1E))
                        .clickable(enabled = !isActive) { route?.let { navController.navigate(it) } }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) Color.Black else Color.White
                    )
                }
                if (label != "Top Artists") Spacer(modifier = Modifier.width(8.dp))
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
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun TopArtistItem(index: Int, artist: String, playCount: Int, songCount: Int) {
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF669BEC),
            modifier = Modifier.width(32.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "$songCount songs",
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
