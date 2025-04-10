package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NetworkPopup(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(top = 8.dp, bottom = 20.dp)
    ) {
        Text(
            text = "Purritify is offline",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}