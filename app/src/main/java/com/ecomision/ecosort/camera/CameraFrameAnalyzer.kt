package com.ecomision.ecosort.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraFrameAnalyzer(
    private val detector: WasteObjectDetector,
    private val onResult: (FrameAnalysisSnapshot) -> Unit
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var processing = false
    private var lastFrameMs = 0L
    private val minIntervalMs = 250L

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (processing || now - lastFrameMs < minIntervalMs) {
            imageProxy.close()
            return
        }

        processing = true
        lastFrameMs = now

        scope.launch {
            try {
                val snapshot = detector.detect(imageProxy)
                withContext(Dispatchers.Main) {
                    onResult(snapshot)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Frame analysis failed.", throwable)
                withContext(Dispatchers.Main) {
                    onResult(
                        FrameAnalysisSnapshot(
                            imageWidth = imageProxy.width,
                            imageHeight = imageProxy.height,
                            detections = emptyList(),
                            bitmap = null,
                            timestampMs = System.currentTimeMillis(),
                            sceneHint = "No pude analizar este frame. Si no ves contornos, toca directamente el residuo."
                        )
                    )
                }
            } finally {
                processing = false
                imageProxy.close()
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "CameraFrameAnalyzer"
    }
}
