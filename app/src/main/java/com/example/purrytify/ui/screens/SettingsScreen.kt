package com.example.purrytify.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.purrytify.data.preferences.PersistentTracker
import com.example.purrytify.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

// Extension function to find Activity from Context
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
    var showSuccessMessage by remember { mutableStateOf(false) }

    // Create a gradient background
    val gradientColors = listOf(
        Color(0xFF095256), // Dark teal top color
        Color(0xFF121212)  // Dark bottom color
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
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Settings content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Extend Session option
                SettingItem(
                    title = "Extend Session (5 minutes)",
                    iconVector = Icons.Default.Refresh,
                    onClick = { 
                        activity?.extendSession()
                        showSuccessMessage = true
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(2000)
                            showSuccessMessage = false
                        }
                    }
                )
                
                Divider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 1.dp)
                
                // Logout option
                SettingItem(
                    title = "Logout",
                    iconVector = Icons.Default.ExitToApp,
                    onClick = { showLogoutDialog = true }
                )

                // Add more settings items here as needed
                Divider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 1.dp)

                // Version information
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "App Version 1.0.0",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    ),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp)
                )
            }
        }

        // Success message
        if (showSuccessMessage) {
            Snackbar(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                containerColor = Color(0xFF1DB954),
                contentColor = Color.White,
            ) {
                Text("Session extended successfully")
            }
        }

        // Logout confirmation dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Stop music playback
                                musicViewModel?.stopAndClearCurrentSong()

                                // Clear tracking data
                                val persistentTracker = PersistentTracker(context)
                                persistentTracker.clearAllTracking()

                                // Clear tokens
                                tokenManager.clearTokens()

                                // Navigate to login screen
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                            showLogoutDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954)
                        )
                    ) {
                        Text("Yes, Logout")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showLogoutDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF2A2A2A),
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = title,
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp
            )
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_more),
            contentDescription = "More",
            tint = Color.White.copy(alpha = 0.5f)
        )
    }
}