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

@Composable
fun BottomNavBar(
    navController: NavController,
    currentRoute: String = "home",
    modifier: Modifier = Modifier  // Added modifier parameter with default value
) {
    Column(
        modifier = modifier  // Use the passed modifier here
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 12.dp)
    ) {
        // Navigation items row with increased horizontal spacing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp), // Increased horizontal spacing
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home Navigation Item
            NavItem(
                icon = R.drawable.nav_home,
                label = "Home",
                isSelected = currentRoute == "home",
                onClick = { if (currentRoute != "home") navController.navigate("home") }
            )

            // Library Navigation Item
            NavItem(
                icon = R.drawable.nav_library,
                label = "Your Library",
                isSelected = currentRoute == "library",
                onClick = { if (currentRoute != "library") navController.navigate("library") }
            )

            // Profile Navigation Item
            NavItem(
                icon = R.drawable.nav_profile,
                label = "Profile",
                isSelected = currentRoute == "profile",
                onClick = { if (currentRoute != "profile") navController.navigate("profile") }
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            tint = if (isSelected) Color.White else Color.Gray,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = TextStyle(
                color = if (isSelected) Color.White else Color.Gray,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        )
    }
}