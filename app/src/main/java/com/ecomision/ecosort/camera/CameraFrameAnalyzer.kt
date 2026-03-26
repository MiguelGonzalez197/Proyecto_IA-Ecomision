package com.ecomision.ecosort.camera

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
            } catch (_: Throwable) {
            } finally {
                processing = false
                imageProxy.close()
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
