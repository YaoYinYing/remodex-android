package com.remodex.mobile.ui.parity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val ZXING_FALLBACK_MIN_INTERVAL_MS = 180L
private val QR_DECODE_HINTS = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE
)

@Composable
fun PairingQrScannerSurface(
    modifier: Modifier = Modifier,
    onScan: (String, resetScanLock: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var hasCameraPermission by rememberSaveable { mutableStateOf(false) }
    var isCheckingPermission by rememberSaveable { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        isCheckingPermission = false
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            hasCameraPermission = true
            isCheckingPermission = false
        } else {
            isCheckingPermission = false
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        when {
            isCheckingPermission -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            hasCameraPermission -> {
                QrCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onScan = onScan
                )
                ScannerOverlay()
            }
            else -> {
                CameraPermissionCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    canRequestAgain = activity?.let {
                        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                            it,
                            Manifest.permission.CAMERA
                        )
                    } ?: false
                )
            }
        }
    }
}

@Composable
private fun QrCameraPreview(
    modifier: Modifier = Modifier,
    onScan: (String, resetScanLock: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onScanState by rememberUpdatedState(onScan)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val scannerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val scanLock = remember { AtomicBoolean(false) }
    val lastZxingFallbackAt = remember { AtomicLong(0L) }

    DisposableEffect(lifecycleOwner) {
        val listener = Runnable {
            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@Runnable
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(scannerExecutor) { imageProxy ->
                if (scanLock.get()) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val fallbackFrame = extractLumaFrameForZxing(imageProxy)
                scanner.process(inputImage)
                    .addOnSuccessListener(scannerExecutor) { barcodes ->
                        val mlKitDecoded = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        val fallbackDecoded = if (mlKitDecoded.isNullOrBlank()) {
                            maybeDecodeWithZxingFallback(fallbackFrame, lastZxingFallbackAt)
                        } else {
                            null
                        }
                        val scannedText = mlKitDecoded ?: fallbackDecoded
                        if (!scannedText.isNullOrBlank() && scanLock.compareAndSet(false, true)) {
                            mainExecutor.execute {
                                onScanState(scannedText) {
                                    scanLock.set(false)
                                }
                            }
                        }
                    }
                    .addOnFailureListener(scannerExecutor) {
                        val fallbackDecoded = maybeDecodeWithZxingFallback(fallbackFrame, lastZxingFallbackAt)
                        if (!fallbackDecoded.isNullOrBlank() && scanLock.compareAndSet(false, true)) {
                            mainExecutor.execute {
                                onScanState(fallbackDecoded) {
                                    scanLock.set(false)
                                }
                            }
                        }
                    }
                    .addOnCompleteListener(scannerExecutor) {
                        imageProxy.close()
                    }
            }

            runCatching {
                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                runCatching { boundCamera.cameraControl.setZoomRatio(1.8f) }
            }
        }

        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            scannerExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun ScannerOverlay() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scan QR code from Remodex CLI",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(width = 2.dp, color = Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp))
        )
        Text(
            text = "Align the code within the frame",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = "Tip: for terminal QR on dark background, raise screen brightness.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.64f)
        )
    }
}

@Composable
private fun CameraPermissionCard(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    canRequestAgain: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera access needed",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = "Allow camera access to scan the secure pairing QR code from your Mac.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
        )
        if (canRequestAgain) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Allow Camera")
            }
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Settings")
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private data class LumaFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int
)

private fun maybeDecodeWithZxingFallback(frame: LumaFrame?, lastAttemptAt: AtomicLong): String? {
    if (frame == null) {
        return null
    }
    val now = System.currentTimeMillis()
    if (now - lastAttemptAt.get() < ZXING_FALLBACK_MIN_INTERVAL_MS) {
        return null
    }
    lastAttemptAt.set(now)
    return decodeQrWithInversionFallback(frame)
}

private fun decodeQrWithInversionFallback(frame: LumaFrame): String? {
    val source = PlanarYUVLuminanceSource(
        frame.bytes,
        frame.width,
        frame.height,
        0,
        0,
        frame.width,
        frame.height,
        false
    )

    val sources = buildList<LuminanceSource> {
        add(source)
        add(source.invert())
        if (source.isRotateSupported) {
            val rotated = source.rotateCounterClockwise()
            add(rotated)
            add(rotated.invert())
        }
    }

    for (candidate in sources) {
        val decoded = decodeWithBinarizers(candidate)
        if (!decoded.isNullOrBlank()) {
            return decoded
        }
    }
    return null
}

private fun decodeWithBinarizers(source: LuminanceSource): String? {
    val reader = MultiFormatReader().apply {
        setHints(QR_DECODE_HINTS)
    }

    val attempts = listOf(
        BinaryBitmap(HybridBinarizer(source)),
        BinaryBitmap(GlobalHistogramBinarizer(source))
    )

    for (bitmap in attempts) {
        try {
            val result = reader.decodeWithState(bitmap)
            return result.text
        } catch (_: NotFoundException) {
            // Keep trying additional transforms for low-contrast terminal QR renders.
        } catch (_: Throwable) {
            // Ignore malformed intermediate decodes.
        } finally {
            reader.reset()
        }
    }
    return null
}

private fun extractLumaFrameForZxing(imageProxy: ImageProxy): LumaFrame? {
    if (imageProxy.format != ImageFormat.YUV_420_888 || imageProxy.planes.isEmpty()) {
        return null
    }
    val yPlane = imageProxy.planes[0]
    val width = imageProxy.width
    val height = imageProxy.height
    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride
    val raw = yPlane.buffer.duplicate()
    if (!raw.hasRemaining() || width <= 0 || height <= 0) {
        return null
    }

    val rawBytes = ByteArray(raw.remaining())
    raw.get(rawBytes)

    val luma = ByteArray(width * height)
    for (y in 0 until height) {
        val rowOffset = y * rowStride
        val rowOutputOffset = y * width
        for (x in 0 until width) {
            val sourceIndex = rowOffset + (x * pixelStride)
            if (sourceIndex in rawBytes.indices) {
                luma[rowOutputOffset + x] = rawBytes[sourceIndex]
            }
        }
    }

    return rotateLumaFrame(
        LumaFrame(bytes = luma, width = width, height = height),
        imageProxy.imageInfo.rotationDegrees
    )
}

private fun rotateLumaFrame(frame: LumaFrame, rotationDegrees: Int): LumaFrame {
    return when (((rotationDegrees % 360) + 360) % 360) {
        90 -> rotate90(frame)
        180 -> rotate180(frame)
        270 -> rotate270(frame)
        else -> frame
    }
}

private fun rotate90(frame: LumaFrame): LumaFrame {
    val output = ByteArray(frame.width * frame.height)
    val outputWidth = frame.height
    val outputHeight = frame.width
    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val sourceIndex = y * frame.width + x
            val destX = frame.height - 1 - y
            val destY = x
            output[destY * outputWidth + destX] = frame.bytes[sourceIndex]
        }
    }
    return LumaFrame(output, outputWidth, outputHeight)
}

private fun rotate180(frame: LumaFrame): LumaFrame {
    val output = ByteArray(frame.width * frame.height)
    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val sourceIndex = y * frame.width + x
            val destX = frame.width - 1 - x
            val destY = frame.height - 1 - y
            output[destY * frame.width + destX] = frame.bytes[sourceIndex]
        }
    }
    return LumaFrame(output, frame.width, frame.height)
}

private fun rotate270(frame: LumaFrame): LumaFrame {
    val output = ByteArray(frame.width * frame.height)
    val outputWidth = frame.height
    val outputHeight = frame.width
    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val sourceIndex = y * frame.width + x
            val destX = y
            val destY = frame.width - 1 - x
            output[destY * outputWidth + destX] = frame.bytes[sourceIndex]
        }
    }
    return LumaFrame(output, outputWidth, outputHeight)
}
