package com.example.purrytify.ui.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.purrytify.MainActivity

/**
 * Broadcast receiver untuk menangani navigasi dari notifikasi
 */
class NavigationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NavigationReceiver", "Received intent: ${intent.action}")
        
        if (intent.action == "OPEN_PLAYER_SCREEN") {
            // Buat intent untuk membuka MainActivity dengan flag tertentu
            val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = "OPEN_PLAYER_SCREEN"
            }
            
            // Luncurkan MainActivity
            context.startActivity(openPlayerIntent)
        }
    }
}