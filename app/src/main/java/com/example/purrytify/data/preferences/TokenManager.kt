package com.example.purrytify.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.purrytify.ui.screens.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.crypto.AEADBadTagException

class TokenManager(private val context: Context) {
    private val TAG = "TokenManager"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs: SharedPreferences = createEncryptedSharedPreferences()

    private fun createEncryptedSharedPreferences(): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                "purrytify_token_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences", e)
            when (e) {
                is AEADBadTagException, 
                is javax.crypto.AEADBadTagException -> {
                    Log.w(TAG, "Encryption error detected, clearing corrupted preferences and retrying")
                    clearCorruptedPreferences()
                    createEncryptedSharedPreferencesRetry()
                }
                else -> {
                    Log.w(TAG, "Falling back to regular SharedPreferences due to encryption issues")
                    context.getSharedPreferences("purrytify_token_prefs_fallback", Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun clearCorruptedPreferences() {
        try {
            // Clear the corrupted encrypted shared preferences file
            val prefsFile = context.getSharedPreferences("purrytify_token_prefs", Context.MODE_PRIVATE)
            prefsFile.edit().clear().apply()
            
            // Also try to delete the actual file
            val prefsPath = context.filesDir.parent + "/shared_prefs/purrytify_token_prefs.xml"
            val file = java.io.File(prefsPath)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted corrupted preferences file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing corrupted preferences", e)
        }
    }

    private fun createEncryptedSharedPreferencesRetry(): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                "purrytify_token_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Retry failed, using fallback SharedPreferences", e)
            context.getSharedPreferences("purrytify_token_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveString(key: String, value: String) {
        try {
            sharedPrefs.edit().putString(key, value).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving string for key: $key", e)
        }
    }

    fun getString(key: String): String? {
        return try {
            sharedPrefs.getString(key, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting string for key: $key", e)
            null
        }
    }

    fun saveRecentlyPlayed(email: String, songs: List<Song>) {
        try {
            val json = Gson().toJson(songs)
            sharedPrefs.edit().putString("recently_played_$email", json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recently played for email: $email", e)
        }
    }

    fun getRecentlyPlayed(email: String): List<Song> {
        return try {
            val json = sharedPrefs.getString("recently_played_$email", null) ?: return emptyList()
            val type = object : TypeToken<List<Song>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recently played for email: $email", e)
            emptyList()
        }
    }

    fun saveToken(token: String?) {
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "Attempted to save null or empty token")
            return
        }
        try {
            Log.d(TAG, "Saving token: ${token.take(10)}...")
            sharedPrefs.edit().putString("jwt_token", token).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving token", e)
        }
    }

    fun saveRefreshToken(refreshToken: String?) {
        if (refreshToken.isNullOrEmpty()) {
            Log.w(TAG, "Attempted to save null or empty refresh token")
            return
        }
        try {
            Log.d(TAG, "Saving refresh token: ${refreshToken.take(10)}...")
            sharedPrefs.edit().putString("refreshToken", refreshToken).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving refresh token", e)
        }
    }

    fun getToken(): String? {
        return try {
            val token = sharedPrefs.getString("jwt_token", null)
            if (token != null) {
                Log.d(TAG, "Retrieved token: ${token.take(10)}...")
            } else {
                Log.d(TAG, "No token found")
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error getting token", e)
            null
        }
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
        try {
            Log.d("TokenManager", "Saving email: $email")
            sharedPrefs.edit().putString("user_email", email).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving email", e)
        }
    }

    fun getEmail(): String? {
        return try {
            val email = sharedPrefs.getString("user_email", null)
            Log.d("TokenManager", "getEmail() called. Retrieved email: $email")
            email
        } catch (e: Exception) {
            Log.e(TAG, "Error getting email", e)
            null
        }
    }

    fun forceReset() {
        try {
            Log.w(TAG, "Force resetting all preferences")
            clearCorruptedPreferences()
            
            val fallbackPrefs = context.getSharedPreferences("purrytify_token_prefs_fallback", Context.MODE_PRIVATE)
            fallbackPrefs.edit().clear().apply()
            
            Log.d(TAG, "Force reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during force reset", e)
        }
    }
}