package com.ecomision.ecosort.camera

import android.graphics.PointF
import android.graphics.RectF
import com.ecomision.ecosort.model.DetectionCandidate
import kotlin.math.max
import kotlin.math.pow

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
        return detections
            .sortedBy {
                val centerX = it.boundingBox.centerX()
                val centerY = it.boundingBox.centerY()
                (centerX - point.x).toDouble().pow(2.0) + (centerY - point.y).toDouble().pow(2.0)
            }
            .firstOrNull { it.boundingBox.contains(point.x, point.y) }
            ?: detections.minByOrNull {
                val centerX = it.boundingBox.centerX()
                val centerY = it.boundingBox.centerY()
                (centerX - point.x).toDouble().pow(2.0) + (centerY - point.y).toDouble().pow(2.0)
            }
    }
}

