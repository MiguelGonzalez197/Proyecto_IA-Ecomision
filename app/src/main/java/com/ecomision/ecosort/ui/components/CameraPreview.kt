package com.ecomision.ecosort.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ecomision.ecosort.camera.CameraFrameAnalyzer
import com.ecomision.ecosort.model.DetectionCandidate
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    hasPermission: Boolean,
    analyzer: CameraFrameAnalyzer,
    detections: List<DetectionCandidate>,
    imageWidth: Int,
    imageHeight: Int,
    selectedCandidateId: Long?,
    onCandidateTapped: (DetectionCandidate) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            isTapToFocusEnabled = true
        }
    }

    DisposableEffect(hasPermission, lifecycleOwner) {
        if (hasPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraController.bindToLifecycle(lifecycleOwner)
            cameraController.setImageAnalysisAnalyzer(cameraExecutor, analyzer)
        }
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = cameraController
                    }
                },
                update = { view ->
                    view.controller = cameraController
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFF5FBF7),
                                Color(0xFFE7F3EC)
                            )
                        )
                    )
            )
        }

        DetectionOverlay(
            detections = detections,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            selectedCandidateId = selectedCandidateId,
            onCandidateTapped = onCandidateTapped
        )
    }
}
