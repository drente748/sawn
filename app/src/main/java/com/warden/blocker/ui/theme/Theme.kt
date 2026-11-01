package com.warden.blocker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warden "Sunrise" palette — the bright redesign.
 *
 * Brand intent: a guard you *want* to open. Vivid azure is the anchor (calm, trustworthy),
 * fresh green confirms safety, warm orange adds energy, and the feature tiles each carry
 * their own bright hue so every capability is recognisable at a glance.
 *
 * The app is deliberately single-scheme: always light, never grey. Airy blue-tinted
 * neutrals keep surfaces clean while saturated accents carry the personality. Dynamic
 * color is disabled so the brand reads identically on every device.
 */
object WardenPalette {
    // Anchors
    val Blue = Color(0xFF2563EB)
    val BlueDeep = Color(0xFF1D4FD7)
    val BlueSoft = Color(0xFFE3ECFF)
    val Green = Color(0xFF059669)
    val GreenSoft = Color(0xFFD9F7E8)
    val Orange = Color(0xFFF97316)
    val OrangeSoft = Color(0xFFFFE8D6)

    // Feature-tile hues
    val Pink = Color(0xFFEC4899)
    val PinkSoft = Color(0xFFFCE7F3)
    val Violet = Color(0xFF8B5CF6)
    val VioletSoft = Color(0xFFF1EAFE)
    val Teal = Color(0xFF0D9488)
    val TealSoft = Color(0xFFD7F5F1)
    val Indigo = Color(0xFF4F46E5)
    val IndigoSoft = Color(0xFFE7E9FE)
    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0xFFFFF3DB)

    // Neutrals — airy, blue-tinted, never plain grey
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF51607A)
    val Canvas = Color(0xFFF7F9FF)
    val Card = Color(0xFFFFFFFF)
    val Line = Color(0xFFA9BAE0)
    val LineSoft = Color(0xFFDDE5F5)

    // Hero gradient — azure into indigo-violet; both stops keep white text legible.
    val HeroStart = Color(0xFF2563EB)
    val HeroMid = Color(0xFF4F46E5)
    val HeroEnd = Color(0xFF7C3AED)
}

private val SunriseColors = lightColorScheme(
    primary = WardenPalette.Blue,
    onPrimary = Color.White,
    primaryContainer = WardenPalette.BlueSoft,
    onPrimaryContainer = Color(0xFF0A2B7A),
    secondary = WardenPalette.Green,
    onSecondary = Color.White,
    secondaryContainer = WardenPalette.GreenSoft,
    onSecondaryContainer = Color(0xFF024E36),
    tertiary = WardenPalette.Orange,
    onTertiary = Color.White,
    tertiaryContainer = WardenPalette.OrangeSoft,
    onTertiaryContainer = Color(0xFF6A2E00),
    background = WardenPalette.Canvas,
    onBackground = WardenPalette.Ink,
    surface = WardenPalette.Card,
    onSurface = WardenPalette.Ink,
    surfaceVariant = Color(0xFFEDF2FE),
    onSurfaceVariant = WardenPalette.Muted,
    surfaceTint = WardenPalette.Blue,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBFF),
    surfaceContainer = Color(0xFFF2F6FF),
    surfaceContainerHigh = Color(0xFFEDF2FE),
    surfaceContainerHighest = Color(0xFFE6EDFC),
    outline = WardenPalette.Line,
    outlineVariant = WardenPalette.LineSoft,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E4),
    onErrorContainer = Color(0xFF5C0F0F),
)

private val SunriseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val WardenTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = Typography().titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * Warden ships in one bright scheme on purpose: there is no dark variant, so the
 * guard never turns into a dull grey app even on phones set to dark mode.
 */
@Composable
fun WardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SunriseColors,
        shapes = SunriseShapes,
        typography = WardenTypography,
        content = content,
    )
}
