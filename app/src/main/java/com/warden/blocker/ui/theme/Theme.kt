package com.warden.blocker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warden palette 1.0.0 — قاتم/pressing light design.
 *
 * Brand intent (المواصفة 2026-11-01): هادئ وملهم — أبيض نقي + أخضر أمان، طابع عافية وسلام
 * داخلي. لا ثيم داكن إطلاقاً؛ أحادي السكيمة على كل الأجهزة.
 */
object WardenPalette {
    // الأساسيات من المواصفة (tokens فقط، الاستهلاك في الشاشات)
    val Bg = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFF6F8F7)
    val Green = Color(0xFF3DDC97)
    val GreenDark = Color(0xFF0B3B2A)
    val TextPrimary = Color(0xFF1A1F1C)
    val TextMuted = Color(0xFF5C6B63)
    val Amber = Color(0xFFF2A93B)
    val Card = Color(0xFFFFFFFF)
    val Line = Color(0xFFE6EDE8)

    // Secondary containers derived from brand tones for M3 (light scheme calls for containers)
    val GreenSoft = Color(0xFFE2F7ED)   // light green container, text on it GreenDark
    val AmberSoft = Color(0xFFFFF1DC)   // light amber container, warning surfaces only
    val LineSoft = Color(0xFFEFF3F0)

    // Legacy aliases (0.x era) — الشاشات القديمة لا تزال تشير إليها؛ تُلجم في إعادة
    // كتابة الشاشات (مهام 2–3) ثم تُحذف في تنظيف لاحق.
    val Ink = TextPrimary
    val Muted = TextMuted
    val Canvas = Bg
    val Blue = Green
    val BlueDeep = GreenDark
    val BlueSoft = GreenSoft
    val GreenDarkSoft = GreenSoft
    val Orange = Amber
    val OrangeSoft = AmberSoft
    val Pink = Green
    val PinkSoft = GreenSoft
    val Violet = Green
    val VioletSoft = GreenSoft
    val Teal = Green
    val TealSoft = GreenSoft
    val Indigo = Green
    val IndigoSoft = GreenSoft
    val HeroStart = Green
    val HeroMid = Green
    val HeroEnd = GreenDark
}

private val SunriseColors = lightColorScheme(
    primary = WardenPalette.Green,
    onPrimary = WardenPalette.GreenDark,
    primaryContainer = WardenPalette.GreenSoft,
    onPrimaryContainer = WardenPalette.GreenDark,
    secondary = WardenPalette.GreenDark,
    onSecondary = WardenPalette.Bg,
    secondaryContainer = WardenPalette.GreenSoft,
    onSecondaryContainer = WardenPalette.GreenDark,
    tertiary = WardenPalette.Amber,
    onTertiary = Color(0xFF3D2A05),
    tertiaryContainer = WardenPalette.AmberSoft,
    onTertiaryContainer = Color(0xFF3D2A05),
    background = WardenPalette.Bg,
    onBackground = WardenPalette.TextPrimary,
    surface = WardenPalette.Card,
    onSurface = WardenPalette.TextPrimary,
    surfaceVariant = WardenPalette.SurfaceSoft,
    onSurfaceVariant = WardenPalette.TextMuted,
    surfaceTint = WardenPalette.Green,
    surfaceContainerLowest = WardenPalette.Bg,
    surfaceContainerLow = WardenPalette.SurfaceSoft,
    surfaceContainer = WardenPalette.SurfaceSoft,
    surfaceContainerHigh = WardenPalette.SurfaceSoft,
    surfaceContainerHighest = WardenPalette.LineSoft,
    outline = WardenPalette.Line,
    outlineVariant = WardenPalette.LineSoft,
    error = Color(0xFFDC2626),
    onError = WardenPalette.Bg,
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
    // Warden عربي بالكامل (كل النصوص فارسيّة-عربية inline) — فرض اتجاه RTL
    // مستقل عن لغة النظام كي لا تختلط الفقرات داخل حاوية LTR عند من يملك
    // لغة إنجليزية على الهاتف. القياس يظل يعمل في كلا الاتجاهين.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = SunriseColors,
            shapes = SunriseShapes,
            typography = WardenTypography,
            content = content,
        )
    }
}
