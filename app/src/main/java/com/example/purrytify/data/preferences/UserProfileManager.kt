package com.example.purrytify.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class UserProfile(
    val email: String?,
    val name: String,
    val age: Int,
    val gender: String,
    val country: String,
    val profileImageUrl: String? = null
)

class UserProfileManager(context: Context) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("user_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveUserProfile(profile: UserProfile) {
        android.util.Log.d("UserProfileManager", "Saving profile for ${profile.email} with country: ${profile.country}")
        val profileJson = gson.toJson(profile)
        sharedPreferences.edit()
            .putString(profile.email, profileJson)
            .apply()
        android.util.Log.d("UserProfileManager", "Profile saved successfully for ${profile.email}")
    }
    
    fun getUserProfile(email: String): UserProfile? {
        android.util.Log.d("UserProfileManager", "Getting profile for email: $email")
        // Clear any cached data to ensure we get the most recent profile
        val profileJson = sharedPreferences.getString(email, null)
        val profile = if (profileJson != null) {
            gson.fromJson(profileJson, UserProfile::class.java)
        } else {
            null
        }
        android.util.Log.d("UserProfileManager", "Retrieved profile for $email: country=${profile?.country}")
        return profile
    }
    
    fun updateUserCountry(email: String, countryCode: String) {
        val profile = getUserProfile(email)
        if (profile != null) {
            val updatedProfile = profile.copy(country = countryCode)
            saveUserProfile(updatedProfile)
        }
    }
    
    fun deleteUserProfile(email: String) {
        sharedPreferences.edit()
            .remove(email)
            .apply()
    }
    
    fun getAllProfiles(): List<UserProfile> {
        val allEntries = sharedPreferences.all
        val profiles = mutableListOf<UserProfile>()
        
        for ((_, value) in allEntries) {
            if (value is String) {
                try {
                    val profile = gson.fromJson(value, UserProfile::class.java)
                    profiles.add(profile)
                } catch (e: Exception) {
                    // Skip invalid entries
                }
            }
        }
        
        return profiles
    }
}
