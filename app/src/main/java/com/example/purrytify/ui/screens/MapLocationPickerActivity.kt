package com.example.purrytify.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.example.purrytify.ui.theme.PurrytifyTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.*

class MapLocationPickerActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private var savedInstanceState: Bundle? = null
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions.entries.all { it.value }
        if (locationGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission is required to use the map", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.savedInstanceState = savedInstanceState
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setContent {
            PurrytifyTheme {
                MapScreen(
                    onLocationSelected = { countryCode ->
                        val resultIntent = Intent().apply {
                            putExtra("selected_country_code", countryCode)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    savedInstanceState = savedInstanceState,
                    onMapViewCreated = { mv ->
                        mapView = mv
                    },
                    onMapReady = { map ->
                        googleMap = map
                        checkLocationPermission()
                    }
                )
            }
        }
    }
    
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            getCurrentLocation()
        }
    }
    
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        try {
            googleMap?.isMyLocationEnabled = true
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10f))
                }
            }
        } catch (e: Exception) {
            Log.e("MapActivity", "Error getting location", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }
    
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }
    
    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }
    
    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onLocationSelected: (String) -> Unit,
    onCancel: () -> Unit,
    savedInstanceState: Bundle?,
    onMapViewCreated: (MapView) -> Unit,
    onMapReady: (GoogleMap) -> Unit
) {
    val context = LocalContext.current
    var selectedCountryCode by remember { mutableStateOf("") }
    var selectedCountryName by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Location on Map") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
        ) {
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        onCreate(savedInstanceState)
                        onMapViewCreated(this)
                        getMapAsync { googleMap ->
                            onMapReady(googleMap)
                            
                            googleMap.setOnMapClickListener { latLng ->
                                googleMap.clear()
                                
                                googleMap.addMarker(
                                    MarkerOptions()
                                        .position(latLng)
                                        .title("Selected Location")
                                )
                                
                                getCountryFromCoordinates(context, latLng.latitude, latLng.longitude) { code, name ->
                                    selectedCountryCode = code
                                    selectedCountryName = name
                                    showConfirmation = true
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (showConfirmation) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E1E)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Selected Country",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = selectedCountryName,
                            color = Color.White
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showConfirmation = false }
                            ) {
                                Text(
                                    "Cancel",
                                    color = Color.Gray
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Button(
                                onClick = { onLocationSelected(selectedCountryCode) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6CCB64)
                                )
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCountryFromCoordinates(
    context: Context,
    latitude: Double,
    longitude: Double,
    onResult: (code: String, name: String) -> Unit
) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                if (addresses.isNotEmpty() && addresses[0].countryCode != null) {
                    val countryCode = addresses[0].countryCode
                    val countryName = addresses[0].countryName
                    onResult(countryCode, countryName)
                } else {
                    onResult("ID", "Indonesia")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty() && addresses[0].countryCode != null) {
                val countryCode = addresses[0].countryCode
                val countryName = addresses[0].countryName
                onResult(countryCode, countryName)
            } else {
                onResult("ID", "Indonesia")
            }
        }
    } catch (e: Exception) {
        Log.e("MapScreen", "Error getting country", e)
        onResult("ID", "Indonesia")
    }
}