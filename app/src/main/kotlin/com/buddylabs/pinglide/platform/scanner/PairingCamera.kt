package com.buddylabs.pinglide.platform.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutionException

@Composable
fun PairingCamera(modifier: Modifier, onCode: (String) -> Unit, onFailure: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val code = rememberUpdatedState(onCode)
    val failure = rememberUpdatedState(onFailure)
    val controller = remember(context) { LifecycleCameraController(context) }
    AndroidView(
        modifier = modifier,
        factory = { PreviewView(it).apply { this.controller = controller } }
    )
    DisposableEffect(controller, lifecycle) {
        val executor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        var active = true
        var lastHash = 0
        var lastTime = 0L
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(
                listOf(scanner),
                ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                executor
            ) { result ->
                val text = result.getValue(scanner)?.firstOrNull()?.rawValue
                if (active && text != null) {
                    val now = SystemClock.elapsedRealtime()
                    if (text.hashCode() != lastHash || now - lastTime >= 1500) {
                        lastHash = text.hashCode()
                        lastTime = now
                        code.value(text)
                    }
                }
            }
        )
        try {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                controller.bindToLifecycle(lifecycle)
            } else {
                failure.value()
            }
        } catch (_: IllegalStateException) {
            failure.value()
        } catch (_: SecurityException) {
            failure.value()
        } catch (_: IllegalArgumentException) {
            failure.value()
        }
        controller.initializationFuture.addListener({
            if (active) {
                try {
                    controller.initializationFuture.get()
                } catch (_: ExecutionException) {
                    failure.value()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    failure.value()
                }
            }
        }, executor)
        onDispose {
            active = false
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }
}
