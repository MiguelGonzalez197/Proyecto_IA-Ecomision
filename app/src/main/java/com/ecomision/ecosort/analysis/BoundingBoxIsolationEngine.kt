package com.ecomision.ecosort.analysis

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

data class IsolatedObject(
    val bitmap: Bitmap,
    val cropRect: Rect
)

class BoundingBoxIsolationEngine {

    fun isolate(source: Bitmap, boundingBox: RectF, paddingRatio: Float = 0.12f): IsolatedObject {
        val padX = boundingBox.width() * paddingRatio
        val padY = boundingBox.height() * paddingRatio

        val left = (boundingBox.left - padX).coerceAtLeast(0f).roundToInt()
        val top = (boundingBox.top - padY).coerceAtLeast(0f).roundToInt()
        val right = (boundingBox.right + padX).coerceAtMost(source.width.toFloat()).roundToInt()
        val bottom = (boundingBox.bottom + padY).coerceAtMost(source.height.toFloat()).roundToInt()

        val safeWidth = (right - left).coerceAtLeast(1)
        val safeHeight = (bottom - top).coerceAtLeast(1)
        val crop = Bitmap.createBitmap(source, left, top, safeWidth, safeHeight)
        return IsolatedObject(
            bitmap = crop,
            cropRect = Rect(left, top, right, bottom)
        )
    }
}

