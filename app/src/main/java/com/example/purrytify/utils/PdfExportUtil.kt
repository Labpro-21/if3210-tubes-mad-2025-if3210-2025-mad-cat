package com.example.purrytify.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
        return try {
            val document = Document(PageSize.A4)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "purrytify_analytics_$timestamp.pdf"

            val pdfFile: File = if (shouldDownload && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Purrytify")
                if (!appDir.exists()) appDir.mkdirs()
                File(appDir, fileName)
            } else if (shouldDownload) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                File(downloadsDir, fileName)
            } else {
                File(context.cacheDir, fileName)
            }

            val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            writer.pageEvent = BlackBackgroundPageEvent()

            document.open()

            document.addCreationDate()
            document.addAuthor("Purrytify")
            document.addCreator("Purrytify Music App")
            document.addTitle("Listening Analytics for $username")

            try {
                val logoStream = context.resources.openRawResource(R.drawable.purrytify_logo)
                val image = Image.getInstance(logoStream.readBytes())
                image.scaleToFit(100f, 100f)
                image.alignment = Element.ALIGN_CENTER
                document.add(image)
                logoStream.close()
            } catch (e: Exception) {
                val logoText = Paragraph("🎵 PURRYTIFY", TITLE_FONT)
                logoText.alignment = Element.ALIGN_CENTER
                document.add(logoText)
            }

            val title = Paragraph("Listening Analytics Report", TITLE_FONT)
            title.alignment = Element.ALIGN_CENTER
            title.spacingAfter = 15f
            document.add(title)

            val userInfo = Paragraph("User: $username", HEADER_FONT)
            userInfo.alignment = Element.ALIGN_CENTER
            userInfo.spacingAfter = 10f
            document.add(userInfo)

            val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            val date = Paragraph("Generated on: ${dateFormat.format(Date())}", SUB_FONT)
            date.alignment = Element.ALIGN_CENTER
            date.spacingAfter = 20f
            document.add(date)

            document.add(Chunk(LineSeparator().apply {
                lineColor = BaseColor(108, 203, 100)
            }))

            document.add(Paragraph("\n"))

            val timeSection = Paragraph("📊 Total Listening Time", HEADER_FONT)
            timeSection.alignment = Element.ALIGN_LEFT
            timeSection.spacingAfter = 10f
            document.add(timeSection)

            val timeValue = Paragraph(timeListened, NORMAL_FONT)
            timeValue.alignment = Element.ALIGN_LEFT
            timeValue.spacingAfter = 25f
            document.add(timeValue)

            val songsSection = Paragraph("🎵 Your Top Songs", HEADER_FONT)
            songsSection.alignment = Element.ALIGN_LEFT
            songsSection.spacingAfter = 15f
            document.add(songsSection)

            if (topSongs.isEmpty()) {
                document.add(Paragraph("No songs played yet. Start listening to see your favorites!", SUB_FONT).apply {
                    spacingAfter = 25f
                })
            } else {
                val songsTable = PdfPTable(3)
                songsTable.widthPercentage = 100f
                songsTable.setWidths(floatArrayOf(1f, 4f, 1.5f))
                addTableHeader(songsTable, arrayOf("#", "Song Title", "Play Count"))

                topSongs.take(10).forEachIndexed { index, song ->
                    addRow(songsTable, arrayOf(
                        (index + 1).toString(),
                        song.first,
                        "${song.third} plays"
                    ))
                }
                songsTable.spacingAfter = 25f
                document.add(songsTable)
            }

            val artistsSection = Paragraph("🎤 Your Top Artists", HEADER_FONT)
            artistsSection.alignment = Element.ALIGN_LEFT
            artistsSection.spacingAfter = 15f
            document.add(artistsSection)

            if (topArtists.isEmpty()) {
                document.add(Paragraph("No artists played yet. Discover new music!", SUB_FONT).apply {
                    spacingAfter = 25f
                })
            } else {
                val artistsTable = PdfPTable(3)
                artistsTable.widthPercentage = 100f
                artistsTable.setWidths(floatArrayOf(1f, 4f, 1.5f))
                addTableHeader(artistsTable, arrayOf("#", "Artist Name", "Songs Played"))

                topArtists.take(10).forEachIndexed { index, artist ->
                    addRow(artistsTable, arrayOf(
                        (index + 1).toString(),
                        artist.first,
                        "${artist.second} songs"
                    ))
                }
                artistsTable.spacingAfter = 25f
                document.add(artistsTable)
            }

            document.add(Paragraph("\n"))
            document.add(Paragraph("Thank you for using Purrytify! 🐱🎵", SUB_FONT).apply {
                alignment = Element.ALIGN_CENTER
            })

            document.close()

            when {
                shouldDownload && shouldShare -> {
                    Toast.makeText(context, "PDF saved: ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                    shareFile(context, pdfFile)
                }
                shouldDownload -> {
                    Toast.makeText(context, "PDF downloaded: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                    openFile(context, pdfFile)
                }
                shouldShare -> {
                    shareFile(context, pdfFile)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun addTableHeader(table: PdfPTable, headers: Array<String>) {
        headers.forEach { headerText ->
            val cell = PdfPCell(Phrase(headerText, HEADER_FONT)).apply {
                backgroundColor = BaseColor(30, 30, 30)
                horizontalAlignment = Element.ALIGN_CENTER
                verticalAlignment = Element.ALIGN_MIDDLE
                paddingTop = 10f
                paddingBottom = 10f
                border = Rectangle.BOX
                borderColor = BaseColor(108, 203, 100)
                borderWidth = 1f
            }
            table.addCell(cell)
        }
    }

    private fun addRow(table: PdfPTable, cells: Array<String>) {
        cells.forEach { cellText ->
            val cell = PdfPCell(Phrase(cellText, NORMAL_FONT)).apply {
                backgroundColor = BaseColor(45, 45, 45)
                horizontalAlignment = Element.ALIGN_CENTER
                verticalAlignment = Element.ALIGN_MIDDLE
                paddingTop = 8f
                paddingBottom = 8f
                border = Rectangle.BOX
                borderColor = BaseColor(80, 80, 80)
                borderWidth = 0.5f
            }
            table.addCell(cell)
        }
    }

    private fun openFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val chooser = Intent.createChooser(intent, "Open PDF with")
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "No PDF viewer found. File saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
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
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "My Purrytify Listening Analytics")
                putExtra(Intent.EXTRA_TEXT, "Check out my music listening statistics from Purrytify! 🎵")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val chooser = Intent.createChooser(shareIntent, "Share Analytics PDF")
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
