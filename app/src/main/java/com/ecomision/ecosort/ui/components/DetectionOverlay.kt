package com.ecomision.ecosort.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.ecomision.ecosort.camera.PreviewCoordinateMapper
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.ui.theme.EcoGreen
import com.ecomision.ecosort.ui.theme.WarningAmber

@Composable
fun DetectionOverlay(
    detections: List<DetectionCandidate>,
    imageWidth: Int,
    imageHeight: Int,
    selectedCandidateId: Long?,
    onCandidateTapped: (DetectionCandidate) -> Unit
) {
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .pointerInput(detections, overlaySize, imageWidth, imageHeight) {
                detectTapGestures { tap ->
                    val candidate = PreviewCoordinateMapper.hitTest(
                        x = tap.x,
                        y = tap.y,
                        detections = detections,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        viewWidth = overlaySize.width.toFloat(),
                        viewHeight = overlaySize.height.toFloat()
                    )
                    if (candidate != null) onCandidateTapped(candidate)
                }
            }
    ) {
        detections.forEach { detection ->
            val mapped = PreviewCoordinateMapper.imageToViewRect(
                imageRect = detection.boundingBox,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                viewWidth = size.width,
                viewHeight = size.height
            )
            val color = if (detection.id == selectedCandidateId) EcoGreen else WarningAmber
            drawRoundRect(
                color = color,
                topLeft = Offset(mapped.left, mapped.top),
                size = Size(mapped.width(), mapped.height()),
                style = Stroke(width = if (detection.id == selectedCandidateId) 7f else 4f, cap = StrokeCap.Round)
            )

            val labelText = detection.labels.firstOrNull()?.text ?: "Residuo"
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    labelText,
                    mapped.left + 16f,
                    (mapped.top - 14f).coerceAtLeast(40f),
                    labelPaint
                )
            }
        }
    }
}
