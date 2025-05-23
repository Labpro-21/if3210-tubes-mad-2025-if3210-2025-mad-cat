package com.example.purrytify.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.purrytify.R
import com.itextpdf.text.*
import com.itextpdf.text.pdf.*
import com.itextpdf.text.pdf.draw.LineSeparator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExportUtil {
    private val TITLE_FONT = Font(Font.FontFamily.HELVETICA, 18f, Font.BOLD, BaseColor(108, 203, 100))
    private val HEADER_FONT = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor(108, 203, 100))
    private val NORMAL_FONT = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL, BaseColor.WHITE)
    private val SUB_FONT = Font(Font.FontFamily.HELVETICA, 10f, Font.ITALIC, BaseColor(200, 200, 200))

    fun exportAnalyticsToPdf(
        context: Context,
        username: String,
        timeListened: String,
        topSongs: List<Triple<String, String, Int>>,
        topArtists: List<Pair<String, Int>>,
        shouldShare: Boolean = true,
        shouldDownload: Boolean = true
    ): Boolean {
        try {
            val document = Document(PageSize.A4)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "purrytify_analytics_$timestamp.pdf"

            val pdfFile: File = if (shouldDownload) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                File(downloadsDir, fileName)
            } else {
                File(context.cacheDir, fileName)
            }

            val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            writer.pageEvent = BlackBackgroundPageEvent()

            document.open()

            // Set document properties
            document.addCreationDate()
            document.addAuthor("Purrytify")
            document.addCreator("Purrytify Music App")
            document.addTitle("Listening Analytics for $username")

            // App logo
            try {
                val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.purrytify_logo)
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val image = Image.getInstance(stream.toByteArray())
                image.scaleToFit(100f, 100f)
                image.alignment = Element.ALIGN_CENTER
                document.add(image)
            } catch (_: Exception) {}

            // Document Title
            val title = Paragraph("Purrytify Listening Analytics", TITLE_FONT)
            title.alignment = Element.ALIGN_CENTER
            title.spacingAfter = 15f
            document.add(title)

            val userInfo = Paragraph("Analytics for: $username", HEADER_FONT)
            userInfo.alignment = Element.ALIGN_CENTER
            userInfo.spacingAfter = 10f
            document.add(userInfo)

            val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = Paragraph("Generated on: ${dateFormat.format(Date())}", SUB_FONT)
            date.alignment = Element.ALIGN_CENTER
            date.spacingAfter = 20f
            document.add(date)

            document.add(Chunk(LineSeparator().apply {
                lineColor = BaseColor(108, 203, 100)
            }))

            document.add(Paragraph("\n"))

            // Listening time
            val timeSection = Paragraph("Total Listening Time", HEADER_FONT)
            timeSection.alignment = Element.ALIGN_LEFT
            document.add(timeSection)

            val timeValue = Paragraph(timeListened, NORMAL_FONT)
            timeValue.alignment = Element.ALIGN_LEFT
            timeValue.spacingAfter = 20f
            document.add(timeValue)

            // Top Songs
            val songsSection = Paragraph("Top Songs", HEADER_FONT)
            songsSection.alignment = Element.ALIGN_LEFT
            songsSection.spacingAfter = 10f
            document.add(songsSection)

            if (topSongs.isEmpty()) {
                document.add(Paragraph("No songs played yet.", SUB_FONT).apply {
                    spacingAfter = 20f
                })
            } else {
                val songsTable = PdfPTable(2)
                songsTable.widthPercentage = 100f
                songsTable.setWidths(floatArrayOf(3f, 1f))
                addTableHeader(songsTable, arrayOf("Song", "Plays"))

                topSongs.take(10).forEach {
                    addRow(songsTable, arrayOf(it.first, it.third.toString()))
                }
                songsTable.spacingAfter = 20f
                document.add(songsTable)
            }

            // Top Artists
            val artistsSection = Paragraph("Top Artists", HEADER_FONT)
            artistsSection.alignment = Element.ALIGN_LEFT
            artistsSection.spacingAfter = 10f
            document.add(artistsSection)

            if (topArtists.isEmpty()) {
                document.add(Paragraph("No artists played yet.", SUB_FONT).apply {
                    spacingAfter = 20f
                })
            } else {
                val artistsTable = PdfPTable(2)
                artistsTable.widthPercentage = 100f
                artistsTable.setWidths(floatArrayOf(3f, 1f))
                addTableHeader(artistsTable, arrayOf("Artist", "Total Songs"))

                topArtists.take(10).forEach {
                    addRow(artistsTable, arrayOf(it.first, it.second.toString()))
                }
                document.add(artistsTable)
            }

            document.add(Paragraph("\n"))
            document.add(Paragraph("Thank you for using Purrytify!", SUB_FONT).apply {
                alignment = Element.ALIGN_CENTER
            })

            document.close()

            val toastMessage = when {
                shouldDownload && shouldShare -> "PDF saved to Downloads and ready to share"
                shouldDownload -> "PDF saved to Downloads: $fileName"
                else -> "PDF ready to share"
            }
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()

            when {
                shouldShare -> offerToShareFile(context, pdfFile)
                shouldDownload -> offerToViewFile(context, pdfFile)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error exporting analytics: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun addTableHeader(table: PdfPTable, headers: Array<String>) {
        headers.forEach {
            val cell = PdfPCell(Phrase(it, HEADER_FONT)).apply {
                backgroundColor = BaseColor(30, 30, 30)
                horizontalAlignment = Element.ALIGN_CENTER
                verticalAlignment = Element.ALIGN_MIDDLE
                paddingTop = 8f
                paddingBottom = 8f
            }
            table.addCell(cell)
        }
    }

    private fun addRow(table: PdfPTable, cells: Array<String>) {
        cells.forEach {
            val cell = PdfPCell(Phrase(it, NORMAL_FONT)).apply {
                backgroundColor = BaseColor(45, 45, 45)
                horizontalAlignment = Element.ALIGN_CENTER
                verticalAlignment = Element.ALIGN_MIDDLE
                paddingTop = 5f
                paddingBottom = 5f
            }
            table.addCell(cell)
        }
    }

    private fun offerToViewFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "View Downloaded PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun offerToShareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share PDF via"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    class BlackBackgroundPageEvent : PdfPageEventHelper() {
        override fun onEndPage(writer: PdfWriter, document: Document) {
            val cb = writer.directContentUnder
            cb.saveState()
            cb.setColorFill(BaseColor.BLACK)
            cb.rectangle(0f, 0f, document.pageSize.width, document.pageSize.height)
            cb.fill()
            cb.restoreState()
        }
    }
}
