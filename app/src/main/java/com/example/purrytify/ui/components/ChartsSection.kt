package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun ChartsSection(
    onGlobalClick: () -> Unit,
    onCountryClick: () -> Unit,
    countryName: String,
    countryCode: String,
    isCountrySupported: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = "Charts",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top 50 Global Card
            ChartCard(
                title = "Top 50",
                subtitle = "GLOBAL",
                onClick = onGlobalClick,
                gradientColors = listOf(
                    Color(0xFF5FADC2),
                    Color(0xFF3C5C9C)
                ),
                modifier = Modifier.weight(1f)
            )
            
            // Top 50 Country Card
            ChartCard(
                title = if (isCountrySupported) "Top 50" else "Not Available",
                subtitle = countryName.uppercase(),
                onClick = if (isCountrySupported) onCountryClick else null,
                gradientColors = if (isCountrySupported) {
                    listOf(
                        Color(0xFFE16970),
                        Color(0xFFB73C46)
                    )
                } else {
                    listOf(
                        Color(0xFF666666),
                        Color(0xFF333333)
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = isCountrySupported
            )
        }
        
        if (!isCountrySupported) {
            Text(
                text = "Top songs not available for $countryName",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ChartCard(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(colors = gradientColors)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
