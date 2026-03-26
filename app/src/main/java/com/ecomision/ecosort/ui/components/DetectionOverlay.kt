package com.ecomision.ecosort.ui.components

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.ecomision.ecosort.camera.PreviewCoordinateMapper
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.ui.theme.EcoGreenLight
import com.ecomision.ecosort.ui.theme.WarningAmber

private data class OverlayCandidate(
    val detection: DetectionCandidate,
    val viewRect: RectF
)

@Composable
fun DetectionOverlay(
    detections: List<DetectionCandidate>,
    imageWidth: Int,
    imageHeight: Int,
    selectedCandidateId: Long?,
    interactionEnabled: Boolean,
    onCandidateTapped: (DetectionCandidate) -> Unit
) {
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val overlayCandidates = remember(detections, imageWidth, imageHeight, overlaySize) {
        if (imageWidth <= 0 || imageHeight <= 0 || overlaySize == IntSize.Zero) {
            emptyList()
        } else {
            detections.map { detection ->
                OverlayCandidate(
                    detection = detection,
                    viewRect = PreviewCoordinateMapper.imageToViewRect(
                        imageRect = detection.boundingBox,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        viewWidth = overlaySize.width.toFloat(),
                        viewHeight = overlaySize.height.toFloat()
                    )
                )
            }
        }
    }
    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD
            )
        }
    }
    val tapModifier = if (interactionEnabled && imageWidth > 0 && imageHeight > 0) {
        Modifier.pointerInput(detections, overlaySize, imageWidth, imageHeight) {
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
    } else {
        Modifier
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .then(tapModifier)
    ) {
        val hasSelection = selectedCandidateId != null
        overlayCandidates.forEach { item ->
            val selected = item.detection.id == selectedCandidateId
            val strokeColor = if (selected) EcoGreenLight else WarningAmber
            val alpha = when {
                selected -> 1f
                hasSelection -> 0.34f
                else -> 0.84f
            }
            drawRoundRect(
                color = strokeColor.copy(alpha = 0.12f * alpha),
                topLeft = Offset(item.viewRect.left, item.viewRect.top),
                size = Size(item.viewRect.width(), item.viewRect.height()),
                cornerRadius = CornerRadius(28f, 28f)
            )
            drawRoundRect(
                color = strokeColor.copy(alpha = alpha),
                topLeft = Offset(item.viewRect.left, item.viewRect.top),
                size = Size(item.viewRect.width(), item.viewRect.height()),
                cornerRadius = CornerRadius(28f, 28f),
                style = Stroke(
                    width = if (selected) 6f else 4f,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(24f, 14f),
                        phase = 0f
                    )
                )
            )
            drawTouchHint(
                rect = item.viewRect,
                selected = selected,
                color = strokeColor.copy(alpha = if (selected) 0.98f else 0.88f),
                labelPaint = labelPaint
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTouchHint(
    rect: RectF,
    selected: Boolean,
    color: Color,
    labelPaint: Paint
) {
    val label = if (selected) "Clasificando..." else "Toca para clasificar"
    val bubbleHeight = 36f
    val bubbleTop = (rect.top - bubbleHeight - 10f).coerceAtLeast(22f)
    val labelWidth = labelPaint.measureText(label)
    val bubbleWidth = (labelWidth + 32f).coerceAtLeast(146f)

    drawRoundRect(
        color = color,
        topLeft = Offset(rect.left, bubbleTop),
        size = Size(bubbleWidth, bubbleHeight),
        cornerRadius = CornerRadius(18f, 18f)
    )
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(
            label,
            rect.left + 16f,
            bubbleTop + 24f,
            labelPaint
        )
    }
}
