package com.ecomision.ecosort.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ecomision.ecosort.ui.theme.EcoGreen
import com.ecomision.ecosort.ui.theme.EcoOutline
import com.ecomision.ecosort.ui.theme.EcoSurfaceAlt
import com.ecomision.ecosort.ui.theme.EcoSurfaceRaised
import com.ecomision.ecosort.ui.theme.EcoText
import com.ecomision.ecosort.ui.theme.EcoTextMuted
import com.ecomision.ecosort.ui.theme.PrimaryActionGradient
import com.ecomision.ecosort.ui.theme.SuccessGreen
import com.ecomision.ecosort.ui.theme.WarningAmber
import com.ecomision.ecosort.ui.theme.ErrorRed

@Composable
fun EcoWordmark(
    modifier: Modifier = Modifier,
    light: Boolean = false
) {
    val titleColor = if (light) Color.White else EcoText
    val signatureColor = if (light) Color.White.copy(alpha = 0.78f) else EcoTextMuted
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Ecomisión - Clasificador",
            style = MaterialTheme.typography.titleLarge,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "by fourwenteee",
            style = MaterialTheme.typography.labelMedium,
            color = signatureColor
        )
    }
}

@Composable
fun EcoPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = EcoSurfaceRaised.copy(alpha = 0.96f),
    borderColor: Color = EcoOutline.copy(alpha = 0.9f),
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(content = content)
    }
}

@Composable
fun EcoSectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    EcoPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = EcoText
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EcoTextMuted
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun EcoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (enabled) {
        PrimaryActionGradient
    } else {
        Brush.horizontalGradient(listOf(EcoOutline, EcoOutline))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}

@Composable
fun EcoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val contentColor by animateColorAsState(
        targetValue = if (enabled) EcoText else EcoTextMuted.copy(alpha = 0.6f),
        label = "eco_secondary_button_color"
    )
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(EcoSurfaceAlt)
            .border(1.dp, EcoOutline, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

@Composable
fun EcoChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = EcoSurfaceAlt,
    contentColor: Color = EcoText,
    borderColor: Color = Color.Transparent
) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

@Composable
fun EcoStatusDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun EcoConfidenceMeter(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val safeConfidence = confidence.coerceIn(0f, 1f)
    val fillColor = confidenceColor(confidence = safeConfidence)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Confianza",
                style = MaterialTheme.typography.labelLarge,
                color = EcoText
            )
            Text(
                text = "${(safeConfidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = fillColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(EcoSurfaceAlt)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeConfidence)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(fillColor)
            )
        }
    }
}

@Composable
fun EcoLoader(
    text: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eco_loader")
    val scales = listOf(
        transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loader_1"
        ),
        transition.animateFloat(
            initialValue = 0.75f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loader_2"
        ),
        transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loader_3"
        )
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            scales.forEach { scale ->
                val animatedScale by scale
                Box(
                    modifier = Modifier
                        .size((10.dp * animatedScale))
                        .background(EcoGreen.copy(alpha = animatedScale), CircleShape)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = EcoTextMuted
        )
    }
}

@Composable
fun EcoDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = EcoOutline.copy(alpha = 0.8f)
    )
}

@Composable
fun EcoStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = EcoTextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = EcoText
        )
    }
}

@Composable
fun EcoColorSwatch(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(78.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(width = 78.dp, height = 54.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(color)
                .border(1.dp, EcoOutline, MaterialTheme.shapes.medium)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = EcoTextMuted
        )
    }
}

fun confidenceColor(confidence: Float): Color = when {
    confidence >= 0.78f -> SuccessGreen
    confidence >= 0.48f -> WarningAmber
    else -> ErrorRed
}
