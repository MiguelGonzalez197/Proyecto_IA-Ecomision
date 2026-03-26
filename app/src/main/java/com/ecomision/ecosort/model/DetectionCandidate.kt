package com.ecomision.ecosort.model

import android.graphics.RectF

data class DetectionLabel(
    val text: String,
    val confidence: Float
)

data class DetectionCandidate(
    val id: Long,
    val trackingId: Int?,
    val boundingBox: RectF,
    val labels: List<DetectionLabel>,
    val confidence: Float
)

