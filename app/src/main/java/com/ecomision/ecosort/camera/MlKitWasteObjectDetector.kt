package com.ecomision.ecosort.camera

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.DetectionLabel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await

class MlKitWasteObjectDetector : WasteObjectDetector {

    private val detector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    override suspend fun detect(imageProxy: ImageProxy): FrameAnalysisSnapshot {
        val mediaImage = imageProxy.image
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (mediaImage == null) {
            return FrameAnalysisSnapshot(
                imageWidth = 0,
                imageHeight = 0,
                detections = emptyList(),
                bitmap = null,
                timestampMs = System.currentTimeMillis()
            )
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val uprightBitmap = imageProxy.toUprightBitmap()
        val detectedObjects = detector.process(inputImage).await()
        val candidates = detectedObjects.mapIndexed { index, item ->
            val labels = item.labels.map {
                DetectionLabel(
                    text = it.text,
                    confidence = it.confidence
                )
            }
            DetectionCandidate(
                id = (item.trackingId ?: index).toLong(),
                trackingId = item.trackingId,
                boundingBox = RectF(item.boundingBox),
                labels = labels,
                confidence = labels.maxOfOrNull { it.confidence } ?: 0.45f
            )
        }

        return FrameAnalysisSnapshot(
            imageWidth = uprightBitmap?.width ?: 0,
            imageHeight = uprightBitmap?.height ?: 0,
            detections = candidates,
            bitmap = uprightBitmap,
            timestampMs = System.currentTimeMillis()
        )
    }
}
