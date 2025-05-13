package com.example.purrytify.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.purrytify.ui.screens.TopChartsScreen
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel

fun NavGraphBuilder.addTopChartsNavigation(
    navController: NavController,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    onNavigateToPlayer: () -> Unit
) {
    composable("top_charts/global") {
        TopChartsScreen(
            navController = navController,
            chartType = "global",
            countryName = null,
            musicViewModel = musicViewModel,
            songViewModel = songViewModel,
            onNavigateToPlayer = onNavigateToPlayer
        )
    }
    
    composable("top_charts/{countryCode}/{countryName}") { backStackEntry ->
        val countryCode = backStackEntry.arguments?.getString("countryCode") ?: "ID"
        val countryName = backStackEntry.arguments?.getString("countryName") ?: "Indonesia"
        
        TopChartsScreen(
            navController = navController,
            chartType = countryCode,
            countryName = countryName,
            musicViewModel = musicViewModel,
            songViewModel = songViewModel,
            onNavigateToPlayer = onNavigateToPlayer
        )
    }
}
