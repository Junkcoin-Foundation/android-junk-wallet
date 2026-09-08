package junkwallet.ui.screens.send

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ObsidianSurface
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun QrScannerScreen(
    onResult: (address: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var hasScanned by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        val permissionState = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(scanResult) {
        val result = scanResult ?: return@LaunchedEffect
        onResult(result)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Scan QR Code",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHighEmphasis,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = androidx.camera.core.Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        if (!hasScanned) {
                                            @androidx.camera.core.ExperimentalGetImage
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val image = InputImage.fromMediaImage(
                                                    mediaImage,
                                                    imageProxy.imageInfo.rotationDegrees
                                                )
                                                val scanner = BarcodeScanning.getClient()
                                                scanner.process(image)
                                                    .addOnSuccessListener { barcodes ->
                                                        val raw = barcodes.firstOrNull()?.rawValue
                                                        if (raw != null && !hasScanned) {
                                                            hasScanned = true
                                                            scanResult = raw
                                                        }
                                                    }
                                                    .addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } else {
                                                imageProxy.close()
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (_: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                ScannerOverlay()

            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Camera permission required",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHighEmphasis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Grant camera access to scan QR codes",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            colors = CardDefaults.cardColors(
                containerColor = ObsidianSurface.copy(alpha = 0.92f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Point camera at a QR code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHighEmphasis,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Supports address, URI, JSON, and bridge formats",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = PrimaryCyan,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                CornerAccent(isTop = true, isStart = true)
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                CornerAccent(isTop = true, isStart = false)
            }
            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                CornerAccent(isTop = false, isStart = true)
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                CornerAccent(isTop = false, isStart = false)
            }
        }
    }
}

@Composable
private fun CornerAccent(isTop: Boolean, isStart: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .align(if (isTop) Alignment.TopStart else Alignment.BottomStart)
                .background(PrimaryCyan)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .align(if (isStart) Alignment.TopStart else Alignment.TopEnd)
                .background(PrimaryCyan)
        )
    }
}

private typealias BoxScope = androidx.compose.foundation.layout.BoxScope
