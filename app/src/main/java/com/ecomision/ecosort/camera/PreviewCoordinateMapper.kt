package com.ecomision.ecosort.camera

import android.graphics.PointF
import android.graphics.RectF
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.DetectionLabel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object PreviewCoordinateMapper {

    fun imageToViewRect(
        imageRect: RectF,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float
    ): RectF {
        val scale = max(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val dx = (viewWidth - scaledWidth) / 2f
        val dy = (viewHeight - scaledHeight) / 2f
        return RectF(
            imageRect.left * scale + dx,
            imageRect.top * scale + dy,
            imageRect.right * scale + dx,
            imageRect.bottom * scale + dy
        )
    }

    fun viewToImagePoint(
        x: Float,
        y: Float,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float
    ): PointF {
        val scale = max(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val dx = (viewWidth - scaledWidth) / 2f
        val dy = (viewHeight - scaledHeight) / 2f
        return PointF(
            ((x - dx) / scale).coerceIn(0f, imageWidth.toFloat()),
            ((y - dy) / scale).coerceIn(0f, imageHeight.toFloat())
        )
    }

    fun hitTest(
        x: Float,
        y: Float,
        detections: List<DetectionCandidate>,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float
    ): DetectionCandidate? {
        if (imageWidth == 0 || imageHeight == 0) return null
        val point = viewToImagePoint(x, y, imageWidth, imageHeight, viewWidth, viewHeight)
        val nearest = detections.minByOrNull {
            val centerX = it.boundingBox.centerX()
            val centerY = it.boundingBox.centerY()
            (centerX - point.x).toDouble().pow(2.0) + (centerY - point.y).toDouble().pow(2.0)
        }
        val contained = detections
            .filter { candidate ->
                val padding = max(18f, min(candidate.boundingBox.width(), candidate.boundingBox.height()) * 0.12f)
                RectF(candidate.boundingBox).apply { inset(-padding, -padding) }.contains(point.x, point.y)
            }
            .maxByOrNull { it.confidence }
        if (contained != null) return contained

        if (nearest != null) {
            val distance = sqrt(
                ((nearest.boundingBox.centerX() - point.x).toDouble().pow(2.0) +
                    (nearest.boundingBox.centerY() - point.y).toDouble().pow(2.0))
            ).toFloat()
            val tolerance = max(
                min(nearest.boundingBox.width(), nearest.boundingBox.height()) * 0.85f,
                40f
            )
            if (distance <= tolerance) {
                return nearest
            }
        }

        return createTapCandidate(
            point = point,
            detections = detections,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    private fun createTapCandidate(
        point: PointF,
        detections: List<DetectionCandidate>,
        imageWidth: Int,
        imageHeight: Int
    ): DetectionCandidate {
        val averageWidth = detections.map { it.boundingBox.width() }.average().toFloat()
        val averageHeight = detections.map { it.boundingBox.height() }.average().toFloat()
        val baseWidth = if (averageWidth > 0f) averageWidth else imageWidth * 0.22f
        val baseHeight = if (averageHeight > 0f) averageHeight else imageHeight * 0.22f
        val width = baseWidth.coerceIn(imageWidth * 0.14f, imageWidth * 0.48f)
        val height = baseHeight.coerceIn(imageHeight * 0.14f, imageHeight * 0.48f)
        val left = (point.x - width / 2f).coerceIn(0f, imageWidth.toFloat() - width)
        val top = (point.y - height / 2f).coerceIn(0f, imageHeight.toFloat() - height)
        val syntheticId = -(
            point.x.toLong() * 31L +
                point.y.toLong() * 17L +
                imageWidth.toLong() * 13L +
                imageHeight.toLong() +
                10_000L
            )

        return DetectionCandidate(
            id = syntheticId,
            trackingId = null,
            boundingBox = RectF(left, top, left + width, top + height),
            labels = listOf(
                DetectionLabel(
                    text = "Area seleccionada",
                    confidence = 0.26f
                )
            ),
            confidence = 0.26f
        )
    }
}
