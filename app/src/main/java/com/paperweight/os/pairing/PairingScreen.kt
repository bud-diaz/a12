package com.paperweight.os.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.paperweight.os.R
import java.util.concurrent.Executors

@Composable
fun PairingScreen(viewModel: PairingViewModel, onPaired: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state) {
        if (state is PairingState.Paired) onPaired()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !hasCameraPermission -> PairingMessage(stringResource(R.string.pairing_camera_permission_required))
            state is PairingState.Scanning -> QrScannerView(onQrCodeDetected = viewModel::onQrScanned)
            state is PairingState.Redeeming -> PairingMessage(stringResource(R.string.pairing_connecting), showProgress = true)
            state is PairingState.Error -> PairingErrorMessage(
                message = (state as PairingState.Error).message,
                onRetry = viewModel::retry,
            )
            else -> {}
        }
    }
}

@Composable
private fun QrScannerView(onQrCodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, QrCodeAnalyzer(onQrCodeDetected)) }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
            Text(
                text = stringResource(R.string.pairing_scan_instructions),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PairingMessage(message: String, showProgress: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProgress) CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PairingErrorMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.pairing_retry))
        }
    }
}
