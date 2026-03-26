package com.ecomision.ecosort.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EcoColorScheme = lightColorScheme(
    primary = EcoGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = EcoMint,
    onSecondary = EcoDark,
    background = EcoSurface,
    onBackground = EcoDark,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = EcoDark,
    outline = EcoOutline
)

@Composable
fun EcoSortTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EcoColorScheme,
        typography = EcoTypography,
        content = content
    )
}
