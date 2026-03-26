package com.ecomision.ecosort.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toUprightBitmap(): Bitmap? {
    val nv21 = yuv420888ToNv21() ?: return null
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val output = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, output)
    val bytes = output.toByteArray()
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    if (imageInfo.rotationDegrees == 0) return decoded

    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

private fun ImageProxy.yuv420888ToNv21(): ByteArray? {
    val yPlane = planes.getOrNull(0) ?: return null
    val uPlane = planes.getOrNull(1) ?: return null
    val vPlane = planes.getOrNull(2) ?: return null

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)

    val uBytes = ByteArray(uSize)
    val vBytes = ByteArray(vSize)
    uBuffer.get(uBytes)
    vBuffer.get(vBytes)

    val chromaRowStride = uPlane.rowStride
    val chromaPixelStride = uPlane.pixelStride
    var offset = ySize

    for (row in 0 until height / 2) {
        val rowStart = row * chromaRowStride
        for (col in 0 until width / 2) {
            val index = rowStart + col * chromaPixelStride
            nv21[offset++] = vBytes[index]
            nv21[offset++] = uBytes[index]
        }
    }
    return nv21
}
