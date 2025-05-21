package com.example.purrytify.utils

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import com.example.purrytify.R
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Utility class for exporting listening statistics to PDF
 */
class PdfExporter(private val context: Context) {

    private val paint = Paint().apply {
        color = Color.BLACK
        textSize = 12f
    }

    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val headerPaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
    }
    
    /**
     * Export listening statistics to PDF and share it
     * @param title The report title
     * @param totalTimeListened Formatted total time listened
     * @param dailyListeningData Map of daily listening data (ISO date -> minutes)
     */
    fun exportAndShareListeningStats(
        title: String,
        totalTimeListened: String, 
        dailyListeningData: Map<String, Long>
    ) {
        // Create PDF document
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        
        // Draw title
        canvas.drawText(title, 50f, 50f, titlePaint)
        
        // Draw current date
        val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        canvas.drawText("Generated on: $currentDate", 50f, 80f, paint)
        
        // Draw total time listened
        canvas.drawText("Total Time Listened:", 50f, 120f, headerPaint)
        canvas.drawText(totalTimeListened, 50f, 140f, paint)
        
        // Draw daily statistics
        canvas.drawText("Daily Listening Statistics:", 50f, 180f, headerPaint)
        
        var y = 200f
        val sortedData = dailyListeningData.entries
            .sortedByDescending { LocalDate.parse(it.key, DateTimeFormatter.ISO_DATE) }
        
        for ((date, minutes) in sortedData) {
            val formattedDate = LocalDate.parse(date, DateTimeFormatter.ISO_DATE)
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            val hours = minutes / 60
            val mins = minutes % 60
            val timeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            
            canvas.drawText("$formattedDate: $timeText", 50f, y, paint)
            y += 20f
        }
        
        // Finish page and document
        document.finishPage(page)
        
        // Save PDF to cache
        val cacheDir = context.cacheDir
        val pdfFile = File(cacheDir, "listening_stats.pdf")
        val fos = FileOutputStream(pdfFile)
        document.writeTo(fos)
        document.close()
        fos.close()
        
        // Get content URI via FileProvider
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        
        // Create share intent
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        // Start share activity
        context.startActivity(Intent.createChooser(intent, "Share Listening Stats"))
    }
}