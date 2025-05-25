package com.example.purrytify.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.example.purrytify.data.model.OnlineSong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSongDialog(
    songId: Int? = null,
    songTitle: String,
    songArtist: String,
    songUrl: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Validate songId
    val validSongId = songId ?: 0
    if (validSongId <= 0) {
        Log.w("ShareSongDialog", "Invalid song ID: $songId")
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Cannot share this song: Invalid song ID", Toast.LENGTH_LONG).show()
            onDismiss()
        }
        return
    }
    
    // Create deep link format for the app
    val deepLink = "purrytify://song/$validSongId"
    
    // For sharing via URL, create an HTTP URL that the backend should handle
    // This URL should redirect to the deep link when opened in a browser
    val shareableUrl = "https://purrytify.com/open/song/$validSongId"
    
    Log.d("ShareSongDialog", "Generated deep link: $deepLink")
    Log.d("ShareSongDialog", "Generated shareable URL: $shareableUrl")

    // Generate QR code
    LaunchedEffect(deepLink) {
        scope.launch {
            qrCodeBitmap = withContext(Dispatchers.IO) {
                generateQRCode(shareableUrl)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFF555555))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Share Song",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = songTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = songArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = androidx.compose.ui.graphics.Color(0xFFCCCCCC),
                    fontSize = 14.sp
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // QR Code
            Card(
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qrCodeBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } ?: CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color(0xFF1DB954)
                    )
                }
            }

            Text(
                text = "Scan this QR code to open the song",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = androidx.compose.ui.graphics.Color(0xFF888888),
                    fontSize = 12.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // URL Section
            Card(
            modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF2A2A2A)
            ),
            shape = RoundedCornerShape(12.dp)
            ) {
            Row(
            modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
            text = shareableUrl.take(50) + if (shareableUrl.length > 50) "..." else "",
            style = MaterialTheme.typography.bodyMedium.copy(
            color = androidx.compose.ui.graphics.Color(0xFFCCCCCC),
            fontSize = 14.sp
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1
            )

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Song Link", shareableUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy URL",
                            tint = androidx.compose.ui.graphics.Color(0xFFCCCCCC)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Share QR Code
                Button(
                    onClick = {
                        try {
                            qrCodeBitmap?.let { bitmap ->
                                shareQRCode(context, bitmap, songTitle, songArtist)
                            } ?: Toast.makeText(context, "QR code not ready yet", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Error sharing QR: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1DB954),
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = qrCodeBitmap != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share QR",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share QR", fontWeight = FontWeight.Medium)
                }

                // Share URL
                Button(
                    onClick = {
                        try {
                            shareURL(context, shareableUrl, songTitle, songArtist)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Error sharing URL: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1DB954),
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share URL",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share URL", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun generateQRCode(text: String): Bitmap? {
    return try {
        if (text.isEmpty()) {
            return null
        }
        
        val writer = QRCodeWriter()
        val size = 512
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun shareQRCode(context: Context, qrBitmap: Bitmap, songTitle: String, songArtist: String) {
    try {
        // Ensure directory exists
        val cachePath = File(context.cacheDir, "images")
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        
        val file = File(cachePath, "qr_code.png")
        
        FileOutputStream(file).use { stream ->
            qrBitmap.compress(CompressFormat.PNG, 100, stream)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Listen to $songTitle by $songArtist on Purrytify!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share QR Code")
        if (chooser.resolveActivity(context.packageManager) != null) {
            context.startActivity(chooser)
        } else {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to share QR code: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareURL(context: Context, url: String, songTitle: String, songArtist: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Listen to $songTitle by $songArtist on Purrytify!\n$url")
        }
        
        val chooser = Intent.createChooser(shareIntent, "Share Song")
        if (chooser.resolveActivity(context.packageManager) != null) {
            context.startActivity(chooser)
        } else {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to share URL: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
