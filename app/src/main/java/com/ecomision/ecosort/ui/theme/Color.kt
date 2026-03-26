package com.ecomision.ecosort.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val EcoGreen = Color(0xFF227A58)
val EcoGreenLight = Color(0xFF61B98E)
val EcoGreenDark = Color(0xFF143B2D)
val EcoTeal = Color(0xFF2F8A82)

val EcoSurface = Color(0xFFF5F8F5)
val EcoSurfaceAlt = Color(0xFFEDF3EE)
val EcoSurfaceRaised = Color(0xFFFFFFFF)
val EcoOutline = Color(0xFFD7E3DA)
val EcoOutlineStrong = Color(0xFFAFBEB5)

val EcoText = Color(0xFF16211B)
val EcoTextMuted = Color(0xFF617067)
val EcoBlackSoft = Color(0xFF101613)

val SuccessGreen = Color(0xFF7CC59B)
val WarningAmber = Color(0xFFE0AE4F)
val ErrorRed = Color(0xFFD46A6A)

val BinWhite = Color(0xFFF5F7F6)
val BinBlack = Color(0xFF1F2522)
val BinGreen = Color(0xFF2D9968)

val SplashGradient = Brush.linearGradient(
    colors = listOf(EcoGreenDark, EcoGreen, EcoTeal)
)

val HeroGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF7FBF8),
        Color(0xFFE9F3ED),
        Color(0xFFE0EEE7)
    )
)

val PrimaryActionGradient = Brush.horizontalGradient(
    colors = listOf(EcoGreen, EcoGreenLight)
)

val StatusGradient = Brush.horizontalGradient(
    colors = listOf(EcoGreen, EcoTeal)
)

val CameraTopScrim = Brush.verticalGradient(
    colors = listOf(
        EcoBlackSoft.copy(alpha = 0.46f),
        EcoBlackSoft.copy(alpha = 0.10f),
        Color.Transparent
    )
)

val CameraBottomScrim = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        EcoBlackSoft.copy(alpha = 0.12f),
        EcoBlackSoft.copy(alpha = 0.42f)
    )
)
