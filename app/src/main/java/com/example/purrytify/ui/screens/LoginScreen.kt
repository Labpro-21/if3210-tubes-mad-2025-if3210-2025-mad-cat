package com.example.purrytify.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.purrytify.R
import com.example.purrytify.data.api.LoginRequest
import com.example.purrytify.data.api.RetrofitClient
import com.example.purrytify.data.preferences.TokenManager
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LoginScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
    ) {
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
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(300.dp))

            Image(
                painter = painterResource(id = R.drawable.purrytify_logo),
                contentDescription = "Purrytify Logo",
                modifier = Modifier
                    .size(170.dp)
                    .padding(bottom = 16.dp)
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
                    focusedBorderColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

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
                    focusedBorderColor = Color.White
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        isLoading = true
                        errorMessage = ""
                        coroutineScope.launch {
                            try {
                                if (!email.endsWith("@std.stei.itb.ac.id")) {
                                    errorMessage = "Email should be in format: {NIM}@std.stei.itb.ac.id"
                                    isLoading = false
                                    return@launch
                                }

                                Log.d("LoginScreen", "Attempting login with email: $email")

                                val request = LoginRequest(email, password)

                                try {
                                    val response = RetrofitClient.apiService.login(request)
                                    Log.d("LoginScreen", "Response received: ${response.code()}")

                                    val rawResponseString = response.raw().toString()
                                    Log.d("LoginScreen", "Raw response: $rawResponseString")

                                    if (response.isSuccessful) {
                                        response.body()?.let { loginResponse ->
                                            Log.d("LoginScreen", "Login response: $loginResponse")

                                            if (loginResponse.token.isNullOrEmpty()) {
                                                Log.e("LoginScreen", "Server returned empty token")

                                                // For development purposes only
                                                Log.w(
                                                    "LoginScreen",
                                                    "Using temporary token for development"
                                                )
                                                tokenManager.saveToken("dev_temp_token")

                                                navController.navigate("home") {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            } else {
                                                Log.d(
                                                    "LoginScreen",
                                                    "Got token: ${loginResponse.token.take(10)}..."
                                                )
                                                tokenManager.saveToken(loginResponse.token)

                                                loginResponse.refreshToken?.let {
                                                    Log.d(
                                                        "LoginScreen",
                                                        "Got refresh token: ${it.take(10)}..."
                                                    )
                                                    tokenManager.saveRefreshToken(it)
                                                }

                                                navController.navigate("home") {
                                                    popUpTo("login") { inclusive = true }
                                                }
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
                                                errorMessage = "Login failed: ${response.code()}"
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
                    containerColor = Color(0xFF1DB954)
                ),
                shape = RoundedCornerShape(28.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
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

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFFE57373),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .height(4.dp)
                    .width(134.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}