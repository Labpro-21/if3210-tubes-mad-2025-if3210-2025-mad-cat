package com.example.purrytify.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.purrytify.ui.utils.isLandscape
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel

@Composable
fun AdaptiveNavigation(
    navController: NavController,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    currentRoute: String = "home",
    onMiniPlayerClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val isLandscapeMode = isLandscape()
    
    if (isLandscapeMode) {
        Row(modifier = Modifier.fillMaxSize()) {
            SideNavBar(
                navController = navController,
                musicViewModel = musicViewModel,
                songViewModel = songViewModel,
                currentRoute = currentRoute,
                onMiniPlayerClick = onMiniPlayerClick
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                content()
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            
            BottomNavBar(
                navController = navController,
                musicViewModel = musicViewModel,
                songViewModel = songViewModel,
                currentRoute = currentRoute,
                onMiniPlayerClick = onMiniPlayerClick,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
} 