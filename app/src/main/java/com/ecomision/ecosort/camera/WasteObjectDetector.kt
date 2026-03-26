package com.ecomision.ecosort.camera

import androidx.camera.core.ImageProxy

interface WasteObjectDetector {
    suspend fun detect(imageProxy: ImageProxy): FrameAnalysisSnapshot
}

