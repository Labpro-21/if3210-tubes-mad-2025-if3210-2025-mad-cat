package com.example.purrytify.data

import android.content.Context
import android.util.Log
import com.example.purrytify.ui.screens.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*

object MixStorageHelper {
    private val gson = Gson()
    private const val DAILY_MIX_FILE = "daily_mix.json"
    private const val FAVORITES_MIX_FILE = "favorites_mix.json"
    private const val DATE_FORMAT = "yyyy-MM-dd"
    private val updateTimes = mutableMapOf<String, LocalDateTime>()

    private fun getToday(): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.format(Date())
    }    
    fun saveMix(context: Context, mixName: String, songs: List<Song>) {
        val fileName = getFileName(mixName)
        val file = File(context.cacheDir, fileName)
        val now = LocalDateTime.now()
        val data = MixData(getToday(), songs, now.toString())
        file.writeText(gson.toJson(data))
        
        updateTimes[mixName] = now
        
        val sharedPrefs = context.getSharedPreferences("mix_preferences", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("${mixName}_update_time", now.toString()).apply()
        
        Log.d("RecommendationStorage", "Saved $mixName mix at $now")
    }

    fun loadMix(context: Context, mixName: String): List<Song>? {
        val fileName = getFileName(mixName)
        val file = File(context.cacheDir, fileName)
        if (!file.exists()) return null

        val json = file.readText()
        val type = object : TypeToken<MixData>() {}.type
        val mixData: MixData = try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e("RecommendationStorage", "Error parsing mix data", e)
            return null
        }
        
        if (mixData.date == getToday()) {
            try {
                if (mixData.updateTime != null) {
                    updateTimes[mixName] = LocalDateTime.parse(mixData.updateTime)
                } else {
                    val sharedPrefs = context.getSharedPreferences("mix_preferences", Context.MODE_PRIVATE)
                    val timeString = sharedPrefs.getString("${mixName}_update_time", null)
                    if (timeString != null) {
                        updateTimes[mixName] = LocalDateTime.parse(timeString)
                    } else {
                        updateTimes[mixName] = LocalDateTime.now()
                    }
                }
            } catch (e: Exception) {
                Log.e("RecommendationStorage", "Error parsing update time", e)
                updateTimes[mixName] = LocalDateTime.now()
            }
            
            return mixData.songs
        }
        
        return null
    }    
    private fun getFileName(mixName: String): String {
        return when (mixName) {
            "Your Daily Mix" -> DAILY_MIX_FILE
            "Favorites Mix" -> FAVORITES_MIX_FILE
            else -> "mix_unknown.json"
        }
    }
    
    private data class MixData(
        val date: String,
        val songs: List<Song>,
        val updateTime: String? = null
    )
}
