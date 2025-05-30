package com.example.purrytify.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.purrytify.R
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.repository.TrendingRepository
import com.example.purrytify.di.NetworkModule
import com.example.purrytify.ui.viewmodel.HomeViewModel
import com.example.purrytify.data.models.ProfileResponse
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.data.preferences.UserProfile
import com.example.purrytify.data.preferences.UserProfileManager
import com.example.purrytify.ui.utils.isLandscape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EditProfileScreen(
    navController: NavController,
    profileData: ProfileResponse?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var location by remember { mutableStateOf(profileData?.location ?: "") }
    var isUploading by remember { mutableStateOf(false) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let {
                imageUri = it
            }
        }
    }

    val mapsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("selected_country_code")?.let { countryCode ->
                location = countryCode
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions.entries.all { it.value }
        if (locationGranted) {
            getCurrentLocation(context, fusedLocationClient) { countryCode ->
                location = countryCode
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraImageUri = createImageUri(context)
            cameraImageUri?.let { cameraLauncher.launch(it) }
            showPhotoOptions = false
        }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
            showPhotoOptions = false
        }
    }

    val gradientColors = listOf(
        Color(0xFF095256),
        Color(0xFF121212)
    )
    
    val isLandscapeMode = isLandscape()
    val scrollState = rememberScrollState()

    
    
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(if (isLandscapeMode) 16.dp else 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Edit Profile",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(if (isLandscapeMode) 16.dp else 32.dp))
            
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = if (isLandscapeMode) 16.dp else 24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isLandscapeMode) 120.dp else 150.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6CCB64))
                        .clickable { showPhotoOptions = true }
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            error = painterResource(id = R.drawable.default_profile),
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("http://34.101.226.132:3000/uploads/profile-picture/${profileData?.profilePhoto}")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            error = painterResource(id = R.drawable.default_profile),
                            placeholder = painterResource(id = R.drawable.default_profile)
                        )
                    }
                }

                IconButton(
                    onClick = { showPhotoOptions = true },
                    modifier = Modifier
                        .size(if (isLandscapeMode) 32.dp else 40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Change Photo",
                        tint = Color.Black,
                        modifier = Modifier.size(if (isLandscapeMode) 16.dp else 20.dp)
                    )
                }
            }

            
            Spacer(modifier = Modifier.height(if (isLandscapeMode) 12.dp else 16.dp))
            
            Text(
                text = "Location",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isLandscapeMode) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isLandscapeMode) 48.dp else 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = getCountryNameFromCode(context, location) ?: location.ifEmpty { "No location selected" },
                        color = Color.White,
                        style = TextStyle(fontSize = 16.sp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            getCurrentLocation(
                                context,
                                fusedLocationClient
                            ) { countryCode ->
                                location = countryCode
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .size(if (isLandscapeMode) 48.dp else 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF095256))
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Get Current Location",
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(if (isLandscapeMode) 16.dp else 24.dp))
            
            if (isLandscapeMode) {
                // In landscape, stack buttons vertically to save horizontal space
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val intent = Intent(context, MapLocationPickerActivity::class.java)
                            mapsLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF095256)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Use Google Maps",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            val intent = Intent(context, CountrySelectionActivity::class.java).apply {
                                putExtra("current_country_code", location)
                            }
                            mapsLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF095256)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Select From List",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val intent = Intent(context, MapLocationPickerActivity::class.java)
                            mapsLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF095256)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = "Use Google Maps",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            val intent = Intent(context, CountrySelectionActivity::class.java).apply {
                                putExtra("current_country_code", location)
                            }
                            mapsLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF095256)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = "Select From List",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            
            Spacer(modifier = Modifier.height(if (isLandscapeMode) 16.dp else 0.dp))
            if (!isLandscapeMode) {
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isUploading = true
                        try {
                            updateProfile(
                                context = context,
                                location = location,
                                imageUri = imageUri,
                                onSuccess = {
                                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                },
                                onError = { message ->
                                    Toast.makeText(context, "Error: $message", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } finally {
                            isUploading = false
                        }
                    }
                },
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6CCB64)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscapeMode) 48.dp else 56.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = "Save Changes",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            
            if (isLandscapeMode) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showPhotoOptions) {
            AlertDialog(
                containerColor = Color(0xFF1E1E1E),
                onDismissRequest = { showPhotoOptions = false },
                title = {
                    Text(
                        "Choose Option",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    cameraImageUri = createImageUri(context)
                                    cameraImageUri?.let { cameraLauncher.launch(it) }
                                    showPhotoOptions = false
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF095256)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Take Photo", color = Color.White)
                        }

                        Button(
                            onClick = {
                                val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }

                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        galleryPermission
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    galleryLauncher.launch("image/*")
                                    showPhotoOptions = false
                                } else {
                                    galleryPermissionLauncher.launch(galleryPermission)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF095256)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Choose from Gallery", color = Color.White)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { showPhotoOptions = false }
                    ) {
                        Text("Cancel", color = Color(0xFF6CCB64))
                    }
                }
            )
        }

        if (showCountryDialog) {
            val countries = listOf(
                "ID" to "Indonesia",
                "US" to "United States",
                "GB" to "United Kingdom",
                "SG" to "Singapore",
                "MY" to "Malaysia",
                "AU" to "Australia",
                "JP" to "Japan",
                "CN" to "China"
            )

            AlertDialog(
                onDismissRequest = { showCountryDialog = false },
                title = { Text("Select Country", color = Color.White) },
                text = {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(countries) { (code, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        location = code
                                        showCountryDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )

                                if (code == location) {
                                    Text(
                                        text = "✓",
                                        color = Color(0xFF6CCB64),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCountryDialog = false }) {
                        Text("Cancel", color = Color(0xFF6CCB64))
                    }
                }
            )
        }
    }
}

/**
 * Creates image URI for camera intent
 */
private fun createImageUri(context: Context): Uri? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${timestamp}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
}

private fun getCurrentLocation(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    onLocationDetected: (String) -> Unit
) {
    try {
        val cancellationTokenSource = CancellationTokenSource()

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                location?.let {
                    getCountryCodeFromCoordinates(context, it.latitude, it.longitude, onLocationDetected)
                }
            }.addOnFailureListener { exception ->
                Log.e("EditProfileScreen", "Location error", exception)
                onLocationDetected("ID")
            }
        }
    } catch (e: Exception) {
        Log.e("EditProfileScreen", "Location permission error", e)
        onLocationDetected("ID")
    }
}

private fun getCountryCodeFromCoordinates(
    context: Context,
    latitude: Double,
    longitude: Double,
    onCountryCodeReceived: (String) -> Unit
) {
    val geocoder = Geocoder(context, Locale.getDefault())
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                if (addresses.isNotEmpty()) {
                    val countryCode = addresses[0].countryCode ?: "ID"
                    onCountryCodeReceived(countryCode)
                } else {
                    onCountryCodeReceived("ID")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val countryCode = addresses[0].countryCode ?: "ID"
                onCountryCodeReceived(countryCode)
            } else {
                onCountryCodeReceived("ID")
            }
        }
    } catch (e: Exception) {
        Log.e("EditProfileScreen", "Geocoder error", e)
        onCountryCodeReceived("ID")
    }
}

private fun getCountryNameFromCode(context: Context, countryCode: String): String? {
    return try {
        val locale = Locale("", countryCode)
        locale.displayCountry
    } catch (e: Exception) {
        null
    }
}

private suspend fun updateProfile(
    context: Context,
    location: String,
    imageUri: Uri?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val locationPart = location.toRequestBody("text/plain".toMediaTypeOrNull())

        val imagePart = imageUri?.let { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "profile_image.jpg")
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("profilePhoto", file.name, requestFile)
        }

        val response = RetrofitClient.apiService.updateProfile(
            location = locationPart,
            profilePhoto = imagePart
        )

        if (response.isSuccessful) {
            val tokenManager = TokenManager(context)
            val userEmail = tokenManager.getEmail() ?: ""
            val userProfileManager = UserProfileManager(context)

            val currentProfile = userProfileManager.getUserProfile(userEmail)
            if (currentProfile != null) {
                val updatedProfile = currentProfile.copy(country = location)
                userProfileManager.saveUserProfile(updatedProfile)
                Log.d("EditProfileScreen", "Updated local profile with country: $location")
            } else {
                val newProfile = UserProfile(
                    email = userEmail,
                    name = "",
                    age = 0,
                    gender = "",
                    country = location,
                    profileImageUrl = null
                )
                userProfileManager.saveUserProfile(newProfile)
                Log.d("EditProfileScreen", "Created new local profile with country: $location")
            }

            onSuccess()
        } else {
            onError("Server returned ${response.code()}")
        }
    } catch (e: Exception) {
        Log.e("EditProfileScreen", "Error updating profile", e)
        onError(e.localizedMessage ?: "Unknown error")
    }
}