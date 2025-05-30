package com.example.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.purrytify.ui.components.AdaptiveNavigation
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.example.purrytify.utils.PdfExporter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimeListenedScreen(
    navController: NavController,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val email = "13522126@std.stei.itb.ac.id"
    
    LaunchedEffect(Unit) {
        if (musicViewModel.isPlaying.value) {
            ListeningAnalytics.startPlaybackTracking(musicViewModel, context, email)
        }
    }
    
    val isPlaying by musicViewModel.isPlaying.collectAsStateWithLifecycle()
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val currentSong = musicViewModel.currentSong.value
            if (currentSong != null) {
                ListeningAnalytics.startPlaybackTracking(musicViewModel, context, email)
            }
        } else {
            ListeningAnalytics.pausePlayback()
        }
    }
    
    val timeListened by ListeningAnalytics.timeListened.collectAsStateWithLifecycle()
    val formattedTimeListened = ListeningAnalytics.formatTimeListened()
    
    var refreshTrigger by remember { mutableStateOf(0) }
    
    val currentTimeMillis = System.currentTimeMillis()
    val dailyListeningData = remember(timeListened, refreshTrigger) { 
        ListeningAnalytics.getDailyListeningData() 
    }

    var showResetConfirmation by remember { mutableStateOf(false) }

    val gradientColors = listOf(
        Color(0xFF095256),
        Color(0xFF121212)
    )

    AdaptiveNavigation(
        navController = navController,
        musicViewModel = musicViewModel,
        songViewModel = songViewModel,
        currentRoute = "time_listened",
        onMiniPlayerClick = onNavigateToPlayer
    ) {
        Column(
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "Listening Time",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Total Time Listened",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp
                        )
                    )
                    
                    Text(
                        text = formattedTimeListened,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    
                    Text(
                        text = "This Month",
                        style = TextStyle(
                            color = Color(0xFF1DB954),
                            fontSize = 14.sp
                        )
                    )
                }
            }

            Text(
                text = "Daily Listening Activity",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        ListeningTimeChart(dailyListeningData)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val sortedDates = dailyListeningData.keys.sortedBy { 
                            LocalDate.parse(it, DateTimeFormatter.ISO_DATE)
                        }
                        
                        if (sortedDates.isNotEmpty()) {
                            val first = sortedDates.first()
                            val last = sortedDates.last()
                            
                            Text(
                                text = formatDateLabel(first),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            )
                            
                            Text(
                                text = "Last 7 Days",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            )
                            
                            Text(
                                text = formatDateLabel(last),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                val todayMinutes = dailyListeningData[todayDate] ?: 0
                
                DailyStatCard(
                    title = "Today",
                    minutes = todayMinutes,
                    color = Color(0xFF1DB954)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val yesterdayDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE)
                val yesterdayMinutes = dailyListeningData[yesterdayDate] ?: 0
                
                DailyStatCard(
                    title = "Yesterday",
                    minutes = yesterdayMinutes,
                    color = Color(0xFF2A9FD6)
                )
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
                    .background(Color(0xFF1DB954))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Listening Time",
                    style = TextStyle(
                        color = Color.Black,
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
                    .background(Color(0xFF1E1E1E))
                    .clickable { navController.navigate("top_artists") }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Top Artists",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun DailyStatCard(title: String, minutes: Long, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(6.dp))
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Text(
                    text = "Listening Activity",
                    style = TextStyle(
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                )
            }
            
            Text(
                text = formatMinutes(minutes),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun ListeningTimeChart(dailyData: Map<String, Long>) {
    val sortedData = dailyData.entries
        .sortedBy { LocalDate.parse(it.key, DateTimeFormatter.ISO_DATE) }
        .map { it.value }
    
    if (sortedData.isEmpty()) return
    
    val maxValue = sortedData.maxOrNull() ?: 1L
    
    val primaryColor = Color(0xFF1DB954)
    val chartLineColor = primaryColor
    val gradientStartColor = Color(0x401DB954)
    val gradientEndColor = Color(0x001DB954)
    
    val points = sortedData.mapIndexed { index, value ->
        value.toFloat() / maxValue.toFloat()
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            val pointCount = points.size
            val pointSpacing = remember { 1f / (pointCount - 1) }
            
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val height = size.height
                    val width = size.width
                    
                    val path = Path()
                    val chartPoints = mutableListOf<Offset>()
                    
                    points.forEachIndexed { index, normalizedValue ->
                        val x = (index * (width / (points.size - 1)))
                        val y = height - (height * normalizedValue)
                        
                        val point = Offset(x.toFloat(), y)
                        chartPoints.add(point)
                        
                        if (index == 0) {
                            path.moveTo(point.x, point.y)
                        } else {
                            path.lineTo(point.x, point.y)
                        }
                        
                        drawCircle(
                            color = chartLineColor,
                            radius = 6f,
                            center = point
                        )
                    }
                    
                    drawPath(
                        path = path,
                        color = chartLineColor,
                        style = Stroke(
                            width = 3f,
                            cap = StrokeCap.Round
                        )
                    )
                    
                    val fillPath = Path().apply {
                        moveTo(0f, height)
                        
                        chartPoints.forEachIndexed { index, offset ->
                            if (index == 0) {
                                lineTo(offset.x, offset.y)
                            } else {
                                lineTo(offset.x, offset.y)
                            }
                        }
                        
                        lineTo(width, height)
                        close()
                    }
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                gradientStartColor,
                                gradientEndColor
                            ),
                            startY = 0f,
                            endY = height
                        )
                    )
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(pointCount) {
                        Box(modifier = Modifier.size(4.dp)) {}
                    }
                }
            }
        }
    }
}

private fun formatDateLabel(isoDate: String): String {
    val date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_DATE)
    return date.format(DateTimeFormatter.ofPattern("MMM d"))
}

private fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    
    return if (hours > 0) {
        "${hours}h ${mins}m"
    } else {
        "${mins}m"
    }
}