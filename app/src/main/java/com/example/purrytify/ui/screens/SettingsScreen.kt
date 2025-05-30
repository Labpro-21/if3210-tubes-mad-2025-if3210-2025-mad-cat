package com.example.purrytify.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.MainActivity
import com.example.purrytify.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.launch
import com.example.purrytify.ui.screens.ListeningAnalytics
import com.example.purrytify.service.auth.TokenRefreshService
import android.content.Intent

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, musicViewModel: MusicViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val tokenManager = remember { TokenManager(context) }
    val coroutineScope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    val gradientColors = listOf(
        Color(0xFF075053),
        Color(0xFF052728),
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
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                ModernSettingItem(
                    title = "Logout",
                    icon = Icons.Default.ExitToApp,
                    onClick = { showLogoutDialog = true }
                )

                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "App Version 1.0.0",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    ),
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .padding(start = 8.dp)
                )
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = {
                    Text(
                        "Logout",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to logout?",
                        style = TextStyle(fontSize = 16.sp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                musicViewModel.stopAndClearCurrentSong()
                                
                                val stopIntent = android.content.Intent(context, com.example.purrytify.service.MediaPlaybackService::class.java)
                                stopIntent.action = "ACTION_STOP"
                                context.startService(stopIntent)
                                
                                kotlinx.coroutines.delay(100)
                                
                                val intent = android.content.Intent(context, com.example.purrytify.service.MediaPlaybackService::class.java)
                                context.stopService(intent)
                                
                                val tokenRefreshIntent = Intent(context, TokenRefreshService::class.java)
                                context.stopService(tokenRefreshIntent)
                                Log.d("SettingsScreen", "Stopped TokenRefreshService")

                                val email = tokenManager.getEmail()
                                if (email != null) {
                                    Log.d("SettingsScreen", "Saving data for user $email before logout")
                                    ListeningAnalytics.resetLastLoadedEmail()
                                } else {
                                    Log.d("SettingsScreen", "No email found for current user during logout")
                                }
                                
                                tokenManager.clearTokens()

                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                            showLogoutDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            "Yes, Logout",
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showLogoutDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF2A2A2A),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun ModernSettingItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}