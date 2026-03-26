package com.ecomision.ecosort.camera

import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.DetectionLabel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await

class MlKitWasteObjectDetector : WasteObjectDetector {

    private val fallbackDetector = FallbackWasteRegionDetector()

    private val detector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    @androidx.camera.core.ExperimentalGetImage
    override suspend fun detect(imageProxy: ImageProxy): FrameAnalysisSnapshot {
        val mediaImage = imageProxy.image
        val rotation = imageProxy.imageInfo.rotationDegrees
        val uprightBitmap = imageProxy.toUprightBitmap()
        if (mediaImage == null || uprightBitmap == null) {
            return FrameAnalysisSnapshot(
                imageWidth = uprightBitmap?.width ?: 0,
                imageHeight = uprightBitmap?.height ?: 0,
                detections = emptyList(),
                bitmap = uprightBitmap,
                timestampMs = System.currentTimeMillis(),
                sceneHint = "No pude leer este frame. Reencuadra o toca directamente el residuo."
            )
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val detectionResult = runCatching { detector.process(inputImage).await() }
        val mlKitError = detectionResult.exceptionOrNull()
        if (mlKitError != null) {
            Log.w(TAG, "ML Kit object detector failed; using flexible fallback only.", mlKitError)
        }
        val mlKitCandidates = detectionResult.getOrDefault(emptyList())
            .mapIndexedNotNull { index, item ->
                val labels = item.labels.map {
                    DetectionLabel(
                        text = it.text,
                        confidence = it.confidence
                    )
                }
                val boundingBox = RectF(item.boundingBox)
                if (!isUsefulDetection(boundingBox, uprightBitmap.width, uprightBitmap.height)) {
                    null
                } else {
                    DetectionCandidate(
                        id = (item.trackingId ?: index).toLong(),
                        trackingId = item.trackingId,
                        boundingBox = boundingBox,
                        labels = if (labels.isEmpty()) {
                            listOf(
                                DetectionLabel(
                                    text = "Objeto detectado",
                                    confidence = 0.42f
                                )
                            )
                        } else {
                            labels
                        },
                        confidence = (labels.maxOfOrNull { it.confidence } ?: 0.44f).coerceIn(0.18f, 0.98f)
                    )
                }
            }
        val fallbackResult = fallbackDetector.detect(uprightBitmap, mlKitCandidates)
        val candidates = mergeDetections(
            primary = mlKitCandidates,
            fallback = fallbackResult.detections
        )

        return FrameAnalysisSnapshot(
            imageWidth = uprightBitmap.width,
            imageHeight = uprightBitmap.height,
            detections = candidates,
            bitmap = uprightBitmap,
            timestampMs = System.currentTimeMillis(),
            sceneHint = buildSceneHint(
                candidates = candidates,
                fallbackHint = fallbackResult.sceneHint,
                mlKitError = mlKitError
            )
        )
    }

    private fun isUsefulDetection(
        boundingBox: RectF,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {
        val frameArea = imageWidth * imageHeight.toFloat()
        if (frameArea <= 0f) return false
        val areaRatio = (boundingBox.width() * boundingBox.height()) / frameArea
        val minSide = minOf(boundingBox.width(), boundingBox.height())
        return areaRatio in 0.004f..0.92f && minSide >= 18f
    }

    private fun mergeDetections(
        primary: List<DetectionCandidate>,
        fallback: List<DetectionCandidate>
    ): List<DetectionCandidate> {
        val merged = primary.toMutableList()
        fallback.forEach { proposal ->
            val overlapsExisting = merged.any { current ->
                intersectionOverUnion(current.boundingBox, proposal.boundingBox) > 0.34f
            }
            if (!overlapsExisting) {
                merged += proposal
            }
        }
        return merged
            .sortedByDescending { it.confidence }
            .take(6)
    }

    private fun buildSceneHint(
        candidates: List<DetectionCandidate>,
        fallbackHint: String,
        mlKitError: Throwable?
    ): String {
        return when {
            candidates.isNotEmpty() && mlKitError != null ->
                "Detector flexible activo. $fallbackHint"

            candidates.isNotEmpty() ->
                fallbackHint

            mlKitError != null ->
                "El detector principal no respondio. Toca directamente el residuo para intentar clasificarlo."

            else ->
                fallbackHint
        }
    }

    private fun intersectionOverUnion(
        first: RectF,
        second: RectF
    ): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)
        val intersectionWidth = (right - left).coerceAtLeast(0f)
        val intersectionHeight = (bottom - top).coerceAtLeast(0f)
        val intersectionArea = intersectionWidth * intersectionHeight
        if (intersectionArea <= 0f) return 0f

        val firstArea = first.width().coerceAtLeast(0f) * first.height().coerceAtLeast(0f)
        val secondArea = second.width().coerceAtLeast(0f) * second.height().coerceAtLeast(0f)
        val unionArea = (firstArea + secondArea - intersectionArea).coerceAtLeast(1f)
        return (intersectionArea / unionArea).coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "MlKitWasteDetector"
    }
}
