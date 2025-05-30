package com.example.purrytify.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import androidx.camera.core.ImageProxy
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onSongScanned: (Int) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScanning by remember { mutableStateOf(true) }
    var scanStatusMessage by remember { mutableStateOf("Position QR code within the frame") }
    var scannedText by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Camera Access Required",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "We need camera permission to scan QR codes",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DB954)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(48.dp)
                ) {
                    Text(
                        "Grant Permission",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        QRScanner(
            isScanning = isScanning,
            onQRCodeScanned = { qrCode ->
                if (isScanning) {
                    isScanning = false
                    scannedText = qrCode
                    scanStatusMessage = "Processing..."

                    when {
                        qrCode.startsWith("purrytify://song/") -> {
                            val songIdString = qrCode.removePrefix("purrytify://song/")
                            val songId = songIdString.toIntOrNull()
                            if (songId != null && songId > 0) {
                                scanStatusMessage = "QR Code found! Loading song..."
                                onSongScanned(songId)
                            } else {
                                scanStatusMessage = "Invalid QR code format"
                                Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    isScanning = true
                                    scanStatusMessage = "Position QR code within the frame"
                                    scannedText = null
                                }
                            }
                        }
                        qrCode.startsWith("https://purrytify.com/open/song/") -> {
                            val songIdString = qrCode.removePrefix("https://purrytify.com/open/song/")
                            val songId = songIdString.toIntOrNull()
                            if (songId != null && songId > 0) {
                                scanStatusMessage = "QR Code found! Loading song..."
                                onSongScanned(songId)
                            } else {
                                scanStatusMessage = "Invalid QR code format"
                                Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    isScanning = true
                                    scanStatusMessage = "Position QR code within the frame"
                                    scannedText = null
                                }
                            }
                        }
                        else -> {
                            scanStatusMessage = "Not a Purrytify QR code"
                            Toast.makeText(context, "This is not a valid Purrytify QR code", Toast.LENGTH_LONG).show()
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                isScanning = true
                                scanStatusMessage = "Position QR code within the frame"
                                scannedText = null
                            }
                        }
                    }
                }
            }
        )

        ScanningOverlay(
            modifier = Modifier.fillMaxSize(),
            isScanning = isScanning
        )

        TopAppBar(
            title = {
                Text(
                    "Scan QR Code",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0x80000000),
                navigationIconContentColor = Color.White,
                titleContentColor = Color.White
            ),
            modifier = Modifier.background(Color.Transparent)
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xE6000000)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = scanStatusMessage,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Make sure the QR code is clearly visible within the frame",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                scannedText?.let { text ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Scanned QR Code:",
                        color = Color(0xFF1DB954),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                handleScannedTextClick(context, text)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = text,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy or Open",
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tap to copy or open link",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun handleScannedTextClick(context: Context, text: String) {
    when {
        text.startsWith("http://") || text.startsWith("https://") -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
                context.startActivity(intent)
                Toast.makeText(context, "Opening link in browser", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                copyToClipboard(context, text)
                Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        text.startsWith("purrytify://") -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
                intent.setPackage(context.packageName) // Ensure it opens in our app
                context.startActivity(intent)
                Toast.makeText(context, "Opening in Purrytify", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                copyToClipboard(context, text)
                Toast.makeText(context, "Deep link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        else -> {
            copyToClipboard(context, text)
            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("QR Code", text)
    clipboard.setPrimaryClip(clip)
}

@Composable
fun ScanningOverlay(
    modifier: Modifier = Modifier,
    isScanning: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanning_line"
    )

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val frameSize = minOf(canvasWidth, canvasHeight) * 0.7f
        val frameLeft = (canvasWidth - frameSize) / 2
        val frameTop = (canvasHeight - frameSize) / 2
        val frameRight = frameLeft + frameSize
        val frameBottom = frameTop + frameSize

        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = Size(canvasWidth, frameTop)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, frameBottom),
            size = Size(canvasWidth, canvasHeight - frameBottom)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, frameTop),
            size = Size(frameLeft, frameSize)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(frameRight, frameTop),
            size = Size(canvasWidth - frameRight, frameSize)
        )

        val cornerLength = 50f
        val cornerStroke = 6f
        val cornerColor = Color(0xFF1DB954)

        drawLine(
            color = cornerColor,
            start = Offset(frameLeft, frameTop + cornerLength),
            end = Offset(frameLeft, frameTop),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(frameLeft, frameTop),
            end = Offset(frameLeft + cornerLength, frameTop),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )

        drawLine(
            color = cornerColor,
            start = Offset(frameRight - cornerLength, frameTop),
            end = Offset(frameRight, frameTop),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(frameRight, frameTop),
            end = Offset(frameRight, frameTop + cornerLength),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )

        drawLine(
            color = cornerColor,
            start = Offset(frameLeft, frameBottom - cornerLength),
            end = Offset(frameLeft, frameBottom),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(frameLeft, frameBottom),
            end = Offset(frameLeft + cornerLength, frameBottom),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )

        drawLine(
            color = cornerColor,
            start = Offset(frameRight - cornerLength, frameBottom),
            end = Offset(frameRight, frameBottom),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(frameRight, frameBottom - cornerLength),
            end = Offset(frameRight, frameBottom),
            strokeWidth = cornerStroke,
            cap = StrokeCap.Round
        )

        if (isScanning) {
            val lineY = frameTop + (frameSize * animatedProgress)
            drawLine(
                color = cornerColor.copy(alpha = 0.8f),
                start = Offset(frameLeft + 20f, lineY),
                end = Offset(frameRight - 20f, lineY),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)),
                cap = StrokeCap.Round
            )
        }

        val gridColor = Color.White.copy(alpha = 0.3f)
        val gridStroke = 1f

        for (i in 1..2) {
            val x = frameLeft + (frameSize * i / 3)
            drawLine(
                color = gridColor,
                start = Offset(x, frameTop + 20f),
                end = Offset(x, frameBottom - 20f),
                strokeWidth = gridStroke
            )
        }

        for (i in 1..2) {
            val y = frameTop + (frameSize * i / 3)
            drawLine(
                color = gridColor,
                start = Offset(frameLeft + 20f, y),
                end = Offset(frameRight - 20f, y),
                strokeWidth = gridStroke
            )
        }
    }
}

@Composable
fun QRScanner(
    isScanning: Boolean = true,
    onQRCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (isScanning) {
                                processImage(imageProxy) { qrCode ->
                                    onQRCodeScanned(qrCode)
                                }
                            }
                            imageProxy.close()
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("QRScanner", "Use case binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(context))
        }
    )
}

private fun processImage(
    imageProxy: ImageProxy,
    onQRCodeFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onQRCodeFound(value)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener { exc ->
                Log.e("QRScanner", "Barcode scanning failed", exc)
            }
    }
}