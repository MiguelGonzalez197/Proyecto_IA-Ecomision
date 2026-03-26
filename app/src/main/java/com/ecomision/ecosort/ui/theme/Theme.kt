package com.ecomision.ecosort.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val EcoColorScheme = lightColorScheme(
    primary = EcoGreen,
    onPrimary = Color.White,
    primaryContainer = EcoSurfaceAlt,
    onPrimaryContainer = EcoText,
    secondary = EcoSurfaceAlt,
    onSecondary = EcoText,
    tertiary = EcoTeal,
    onTertiary = Color.White,
    background = EcoSurface,
    onBackground = EcoText,
    surface = EcoSurfaceRaised,
    onSurface = EcoText,
    surfaceVariant = EcoSurfaceAlt,
    onSurfaceVariant = EcoTextMuted,
    outline = EcoOutline,
    outlineVariant = EcoOutlineStrong,
    error = ErrorRed,
    onError = Color.White
)

private val EcoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun EcoSortTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EcoColorScheme,
        typography = EcoTypography,
        shapes = EcoShapes,
        content = content
    )
}
