package com.example.purrytify.ui.screens
import androidx.compose.ui.platform.LocalContext

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
import androidx.compose.material3.TextFieldDefaults
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

class MapLocationPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val currentLocation = intent.getStringExtra("current_location") ?: "ID"
        
        setContent {
            PurrytifyTheme {
                LocationPickerScreen(
                    currentLocation = currentLocation,
                    onCountrySelected = { countryCode ->
                        val resultIntent = Intent().apply {
                            putExtra("selected_country_code", countryCode)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    currentLocation: String,
    onCountrySelected: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    // List of common countries
    val countries = remember {
        listOf(
            "AF" to "Afghanistan",
            "AU" to "Australia",
            "BR" to "Brazil",
            "CA" to "Canada",
            "CN" to "China",
            "FR" to "France",
            "DE" to "Germany",
            "ID" to "Indonesia",
            "IT" to "Italy",
            "JP" to "Japan",
            "MY" to "Malaysia",
            "MX" to "Mexico",
            "NZ" to "New Zealand",
            "PH" to "Philippines",
            "RU" to "Russia",
            "SG" to "Singapore",
            "KR" to "South Korea",
            "ES" to "Spain",
            "SE" to "Sweden",
            "CH" to "Switzerland",
            "TH" to "Thailand",
            "GB" to "United Kingdom",
            "US" to "United States",
            "VN" to "Vietnam"
        )
    }
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            countries
        } else {
            countries.filter { (code, name) ->
                name.contains(searchQuery, ignoreCase = true) || 
                code.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Your Location") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
        ) {
            // Search Bar
            // Search Bar
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Search country") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color(0xFF6CCB64),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF6CCB64),
                    unfocusedLabelColor = Color.Gray
                )
            )
            
            // Countries List
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCountries) { (code, name) ->
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
                        
                        if (code == currentLocation) {
                            Text(
                                text = "Selected",
                                style = TextStyle(
                                    color = Color(0xFF6CCB64),
                                    fontSize = 14.sp,
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
}