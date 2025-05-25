package com.example.purrytify.data.preferences

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.purrytify.ui.screens.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TokenManager(context: Context) {
    private val TAG = "TokenManager"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "purrytify_token_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? {
        return sharedPrefs.getString(key, null)
    }

    fun saveRecentlyPlayed(email: String, songs: List<Song>) {
        val json = Gson().toJson(songs)
        sharedPrefs.edit().putString("recently_played_$email", json).apply()
    }

    fun getRecentlyPlayed(email: String): List<Song> {
        val json = sharedPrefs.getString("recently_played_$email", null) ?: return emptyList()
        val type = object : TypeToken<List<Song>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun saveToken(token: String?) {
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "Attempted to save null or empty token")
            return
        }
        Log.d(TAG, "Saving token: ${token.take(10)}...")
        sharedPrefs.edit().putString("jwt_token", token).apply()
    }

    fun saveRefreshToken(refreshToken: String?) {
        if (refreshToken.isNullOrEmpty()) {
            Log.w(TAG, "Attempted to save null or empty refresh token")
            return
        }
        Log.d(TAG, "Saving refresh token: ${refreshToken.take(10)}...")
        sharedPrefs.edit().putString("refreshToken", refreshToken).apply()
    }

    fun getToken(): String? {
        val token = sharedPrefs.getString("jwt_token", null)
        if (token != null) {
            Log.d(TAG, "Retrieved token: ${token.take(10)}...")
        } else {
            Log.d(TAG, "No token found")
        }
        return token
    }

    fun getRefreshToken(): String? {
        return sharedPrefs.getString("refreshToken", null)
    }    
    
    fun clearTokens() {
        Log.d(TAG, "Clearing authentication tokens only")
        val editor = sharedPrefs.edit()
        editor.remove("jwt_token")
        editor.remove("refreshToken")
        val email = getEmail()
        editor.apply()
        
        // After tokens are cleared, re-save the email for reference
        if (email != null) {
            saveEmail(email)
        }
    }

    fun hasToken(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    fun updateToken(newToken: String?, newRefreshToken: String?) {
        if (!newToken.isNullOrEmpty()) {
            saveToken(newToken)
        }

        if (!newRefreshToken.isNullOrEmpty()) {
            saveRefreshToken(newRefreshToken)
        }
    }

    fun saveEmail(email: String) {
        Log.d("TokenManager", "Saving email: $email")
        sharedPrefs.edit().putString("user_email", email).apply()
    }

    fun getEmail(): String? {
        val email = sharedPrefs.getString("user_email", null)
        Log.d("TokenManager", "getEmail() called. Retrieved email: $email")
        return email
    }
}