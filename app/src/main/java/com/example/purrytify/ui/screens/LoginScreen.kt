package com.example.purrytify.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.purrytify.R
import com.example.purrytify.data.api.LoginRequest
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.network.ConnectivityObserver
import com.example.purrytify.data.network.NetworkConnectivityObserver
import com.example.purrytify.data.preferences.TokenManager
import com.example.purrytify.ui.screens.ListeningAnalytics
import com.example.purrytify.ui.viewmodel.NetworkViewModel
import com.example.purrytify.ui.viewmodel.NetworkViewModelFactory
import com.example.purrytify.ui.viewmodel.SongViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LoginScreen(navController: NavController, songViewModel: SongViewModel) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showErrorPopup by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val connectivityObserver = remember { NetworkConnectivityObserver(context) }
    val networkViewModel: NetworkViewModel = viewModel(
        factory = NetworkViewModelFactory(connectivityObserver)
    )
    val status by networkViewModel.status.collectAsState()
    val isOnline = status == ConnectivityObserver.Status.Available

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank()) {
            showErrorPopup = true
            delay(4000)
            showErrorPopup = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (!isOnline) {
            ErrorScreen(pageName = "Login")
        } else {
            // Album art collage background
            Image(
                painter = painterResource(id = R.drawable.purrytify_login_top),
                contentDescription = "Album Art Collage",
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.4f),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f, fill = true))

                Image(
                    painter = painterResource(id = R.drawable.purrytify_logo),
                    contentDescription = "Purrytify Logo",
                    modifier = Modifier
                        .size(160.dp)
                        .padding(bottom = 4.dp)
                )

                Text(
                    text = "Millions of Songs.",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Only on Purrytify.",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 40.dp)
                )

                // Email field
                Text(
                    text = "Email",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 8.dp, start = 4.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Email", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        disabledContainerColor = Color(0xFF1E1E1E),
                        unfocusedBorderColor = Color.Gray,
                        focusedBorderColor = Color(0xFF1DB954)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // Password field
                Text(
                    text = "Password",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 8.dp, start = 4.dp, top = 8.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password", color = Color.Gray) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Color.Gray
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        disabledContainerColor = Color(0xFF1E1E1E),
                        unfocusedBorderColor = Color.Gray,
                        focusedBorderColor = Color(0xFF1DB954)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // Login button
                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            isLoading = true
                            errorMessage = ""
                            coroutineScope.launch {
                                try {
                                    if (!email.endsWith("@std.stei.itb.ac.id")) {
                                        errorMessage =
                                            "Email should be in format: {NIM}@std.stei.itb.ac.id"
                                        isLoading = false
                                        return@launch
                                    }

                                    Log.d("LoginScreen", "Attempting login with email: $email")
                                    val request = LoginRequest(email, password)

                                    try {
                                        val response = RetrofitClient.apiService.login(request)
                                        Log.d(
                                            "LoginScreen",
                                            "Response received: ${response.code()}"
                                        )

                                        if (response.isSuccessful) {
                                            response.body()?.let { loginResponse ->
                                                Log.d(
                                                    "LoginScreen",
                                                    "Login response: $loginResponse"
                                                )

                                                if (loginResponse.accessToken.isNullOrEmpty()) {
                                                    Log.e(
                                                        "LoginScreen",
                                                        "Server returned empty token"
                                                    )
                                                    errorMessage =
                                                        "Invalid authentication response. Please try again."
                                                    isLoading = false
                                                    return@let
                                                }

                                                Log.d(
                                                    "LoginScreen",
                                                    "Got token: ${loginResponse.accessToken.take(10)}..."
                                                )
                                                tokenManager.saveToken(loginResponse.accessToken)

                                                loginResponse.refreshToken?.let {
                                                    Log.d(
                                                        "LoginScreen",
                                                        "Got refresh token: ${it.take(10)}..."
                                                    )
                                                    tokenManager.saveRefreshToken(it)
                                                }
                                                tokenManager.saveEmail(email)
                                                songViewModel.updateUserEmail(email)
                                                
                                                ListeningAnalytics.loadFromPreferences(context, email)
                                                ListenedSongsTracker.loadListenedSongs(email, context)
                                                
                                                Log.d("LoginScreen", "Successfully loaded user analytics data for $email")
                                                
                                                navController.navigate("home") {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            } ?: run {
                                                Log.e(
                                                    "LoginScreen",
                                                    "Body was null despite successful response"
                                                )
                                                errorMessage = "Invalid response from server."
                                            }
                                        } else {
                                            when (response.code()) {
                                                401 -> errorMessage =
                                                    "Invalid credentials. Check your email and password."

                                                403 -> errorMessage =
                                                    "Access forbidden. Please try again later."

                                                404 -> errorMessage =
                                                    "Login service not found. Please contact support."

                                                500 -> errorMessage =
                                                    "Server error. Please try again later."

                                                else -> {
                                                    val errorBody = response.errorBody()?.string()
                                                        ?: "Unknown error"
                                                    Log.e(
                                                        "LoginScreen",
                                                        "Login failed: ${response.code()}, Error: $errorBody"
                                                    )
                                                    errorMessage =
                                                        "Login failed: ${response.code()}"
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("LoginScreen", "Network exception", e)
                                        errorMessage = "Network error: ${e.localizedMessage}"
                                    }
                                } catch (e: Exception) {
                                    Log.e("LoginScreen", "General exception", e)
                                    errorMessage = "Error: ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            errorMessage = "Please enter both email and password."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DB954),
                        disabledContainerColor = Color(0xFF1DB954).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Log In",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Bottom spacer
                Spacer(modifier = Modifier.height(50.dp))

                // Bottom handle
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .height(4.dp)
                        .width(134.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Animated Error Popup
            AnimatedVisibility(
                visible = showErrorPopup,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                ErrorPopup(message = errorMessage)
            }
        }
    }
}

@Composable
fun ErrorPopup(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Surface(
            color = Color(0xFF4B1515),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = Color(0xFFE57373),
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp)
                )

                Text(
                    text = message,
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 14.sp,
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}