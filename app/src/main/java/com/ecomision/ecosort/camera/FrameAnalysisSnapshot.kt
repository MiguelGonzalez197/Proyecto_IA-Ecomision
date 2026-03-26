package com.ecomision.ecosort.camera

import android.graphics.Bitmap
import com.ecomision.ecosort.model.DetectionCandidate

data class FrameAnalysisSnapshot(
    val imageWidth: Int,
    val imageHeight: Int,
    val detections: List<DetectionCandidate>,
    val bitmap: Bitmap?,
    val timestampMs: Long
)

