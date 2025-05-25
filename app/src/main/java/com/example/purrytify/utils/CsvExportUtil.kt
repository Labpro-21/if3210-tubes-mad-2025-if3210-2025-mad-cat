package com.example.purrytify.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExportUtil {
    
    fun exportAnalyticsToCsv(
        context: Context,
        username: String,
        timeListened: String,
        topSongs: List<Triple<String, String, Int>>,
        topArtists: List<Pair<String, Int>>,
        shouldShare: Boolean = true,
        shouldDownload: Boolean = true
    ): Boolean {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "purrytify_analytics_$timestamp.csv"

            // Create file for download or cache
            val csvFile: File = if (shouldDownload && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - use app's external files directory
                val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Purrytify")
                if (!appDir.exists()) appDir.mkdirs()
                File(appDir, fileName)
            } else if (shouldDownload) {
                // Pre-Android 10 - use public Downloads
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                File(downloadsDir, fileName)
            } else {
                // Just for sharing - use cache
                File(context.cacheDir, fileName)
            }

            // Write CSV content
            FileWriter(csvFile).use { writer ->
                // Header
                writer.append("Purrytify Listening Analytics Report\n")
                writer.append("User: $username\n")
                writer.append("Generated: ${SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date())}\n")
                writer.append("\n")
                
                // Listening time
                writer.append("Total Listening Time\n")
                writer.append("$timeListened\n")
                writer.append("\n")
                
                // Top Songs
                writer.append("Top Songs\n")
                writer.append("Rank,Song Title,Artist,Play Count\n")
                
                if (topSongs.isEmpty()) {
                    writer.append("No songs played yet\n")
                } else {
                    topSongs.take(10).forEachIndexed { index, song ->
                        writer.append("${index + 1},\"${song.first}\",\"${song.second}\",${song.third}\n")
                    }
                }
                
                writer.append("\n")
                
                // Top Artists
                writer.append("Top Artists\n")
                writer.append("Rank,Artist Name,Songs Played\n")
                
                if (topArtists.isEmpty()) {
                    writer.append("No artists played yet\n")
                } else {
                    topArtists.take(10).forEachIndexed { index, artist ->
                        writer.append("${index + 1},\"${artist.first}\",${artist.second}\n")
                    }
                }
                
                writer.append("\n")
                writer.append("Thank you for using Purrytify!\n")
            }

            // Handle download and sharing
            when {
                shouldDownload && shouldShare -> {
                    Toast.makeText(context, "CSV saved: ${csvFile.absolutePath}", Toast.LENGTH_LONG).show()
                    shareFile(context, csvFile)
                }
                shouldDownload -> {
                    Toast.makeText(context, "CSV downloaded: ${csvFile.name}", Toast.LENGTH_LONG).show()
                    openFile(context, csvFile)
                }
                shouldShare -> {
                    shareFile(context, csvFile)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "CSV export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun openFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val chooser = Intent.createChooser(intent, "Open CSV with")
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "No CSV viewer found. File saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "File saved but cannot open: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "My Purrytify Listening Analytics")
                putExtra(Intent.EXTRA_TEXT, "Check out my music listening statistics from Purrytify! 🎵")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val chooser = Intent.createChooser(shareIntent, "Share Analytics CSV")
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "No sharing app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Sharing failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
