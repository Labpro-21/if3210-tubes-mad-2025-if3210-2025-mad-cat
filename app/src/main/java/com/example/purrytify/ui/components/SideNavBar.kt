package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.purrytify.R
import com.example.purrytify.ui.viewmodel.MusicViewModel
import com.example.purrytify.ui.viewmodel.SongViewModel

@Composable
fun SideNavBar(
    navController: NavController,
    musicViewModel: MusicViewModel,
    songViewModel: SongViewModel,
    currentRoute: String = "home",
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SideNavItem(
                icon = R.drawable.nav_home,
                label = "Home",
                isSelected = currentRoute == "home",
                onClick = { if (currentRoute != "home") navController.navigate("home") }
            )

            SideNavItem(
                icon = R.drawable.nav_library,
                label = "Your Library",
                isSelected = currentRoute == "library",
                onClick = { if (currentRoute != "library") navController.navigate("library") }
            )

            SideNavItem(
                icon = R.drawable.nav_profile,
                label = "Profile",
                isSelected = currentRoute == "profile",
                onClick = { if (currentRoute != "profile") navController.navigate("profile") }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        MiniPlayer(
            musicViewModel = musicViewModel,
            songViewModel = songViewModel,
            onPlayerClick = onMiniPlayerClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SideNavItem(
    icon: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            tint = if (isSelected) Color.White else Color.Gray,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = label,
            style = TextStyle(
                color = if (isSelected) Color.White else Color.Gray,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Start
            )
        )
    }
} 