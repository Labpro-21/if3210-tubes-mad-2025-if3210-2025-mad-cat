package com.example.purrytify.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.purrytify.ui.theme.PurrytifyTheme
import java.util.*

class CountrySelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val currentCountryCode = intent.getStringExtra("current_country_code") ?: "ID"
        
        setContent {
            PurrytifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    CountrySelectionScreen(
                        currentCountryCode = currentCountryCode,
                        onCountrySelected = { code ->
                            val resultIntent = Intent().apply {
                                putExtra("selected_country_code", code)
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectionScreen(
    currentCountryCode: String,
    onCountrySelected: (String) -> Unit,
    onCancel: () -> Unit
) {
    // List of common countries
    val commonCountries = listOf(
        "ID" to "Indonesia",
        "US" to "United States",
        "GB" to "United Kingdom",
        "SG" to "Singapore",
        "MY" to "Malaysia",
        "AU" to "Australia",
        "JP" to "Japan",
        "KR" to "South Korea",
        "CN" to "China",
        "IN" to "India",
        "CA" to "Canada",
        "DE" to "Germany",
        "FR" to "France",
        "IT" to "Italy",
        "ES" to "Spain"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Your Country") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF095256),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
        ) {
            items(commonCountries) { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCountrySelected(code) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (code == currentCountryCode) {
                        Text(
                            text = "✓",
                            style = TextStyle(
                                color = Color(0xFF6CCB64),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                
                Divider(
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
            }
        }
    }
}