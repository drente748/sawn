package com.sawn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sawn.app.R

object SawnPalette {
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFFBF7EF)
    val Background = Color(0xFFF6F2E9)
    val Ink = Color(0xFF143028)
    val Muted = Color(0xFF5D6A64)
    val Line = Color(0xFFDED6C8)

    val Primary = Color(0xFF166B5A)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFDBEFE7)
    val OnPrimaryContainer = Color(0xFF09342B)

    val Success = Color(0xFF1A805F)
    val SuccessContainer = Color(0xFFE4F3EC)
    val Error = Color(0xFFB3261E)
    val ErrorContainer = Color(0xFFFDECE9)
    val Info = Color(0xFF2F5F85)
    val InfoContainer = Color(0xFFE3EFF7)
}

/** نسخة الليل من الهوية نفسها: ورقٌ أخضر داكن بدل الورق الفاتح، ونفَس زيتوني أفتح. */
private object SawnNight {
    val Background = Color(0xFF0B1512)
    val Surface = Color(0xFF12201B)
    val SurfaceSoft = Color(0xFF1B2C26)
    val SurfaceHighest = Color(0xFF23352E)
    val Ink = Color(0xFFE4EDE8)
    val Muted = Color(0xFFA2B3AB)
    val Line = Color(0xFF37463F)
    val LineSoft = Color(0xFF28352F)

    val Primary = Color(0xFF7FD8B6)
    val OnPrimary = Color(0xFF003829)
    val PrimaryContainer = Color(0xFF135041)
    val OnPrimaryContainer = Color(0xFFCFEFDF)

    val SuccessContainer = Color(0xFF17453A)
    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF7E170F)
    val OnErrorContainer = Color(0xFFFFDAD6)
    val Info = Color(0xFF9FCCED)
    val InfoContainer = Color(0xFF1B4560)
}

private val SawnArabicFont = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private fun arabicStyle(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    fontFamily = SawnArabicFont,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private val SawnTypography = Typography(
    displaySmall = arabicStyle(28.sp, 1.45.em, FontWeight.Bold),
    headlineMedium = arabicStyle(24.sp, 1.45.em, FontWeight.Bold),
    headlineSmall = arabicStyle(22.sp, 1.4.em, FontWeight.SemiBold),
    titleLarge = arabicStyle(19.sp, 1.45.em, FontWeight.SemiBold),
    titleMedium = arabicStyle(16.sp, 1.5.em, FontWeight.Medium),
    titleSmall = arabicStyle(14.sp, 1.5.em, FontWeight.SemiBold),
    bodyLarge = arabicStyle(16.sp, 1.65.em),
    bodyMedium = arabicStyle(14.sp, 1.65.em),
    bodySmall = arabicStyle(12.sp, 1.6.em),
    labelLarge = arabicStyle(14.sp, 1.5.em, FontWeight.SemiBold),
    labelMedium = arabicStyle(12.sp, 1.5.em, FontWeight.Medium),
    labelSmall = arabicStyle(11.sp, 1.4.em, FontWeight.Medium),
)

private val SawnLightColors = lightColorScheme(
    primary = SawnPalette.Primary,
    onPrimary = SawnPalette.OnPrimary,
    primaryContainer = SawnPalette.PrimaryContainer,
    onPrimaryContainer = SawnPalette.OnPrimaryContainer,
    secondary = SawnPalette.Success,
    onSecondary = SawnPalette.OnPrimary,
    secondaryContainer = SawnPalette.SuccessContainer,
    onSecondaryContainer = SawnPalette.OnPrimaryContainer,
    tertiary = SawnPalette.Info,
    onTertiary = SawnPalette.OnPrimary,
    tertiaryContainer = SawnPalette.InfoContainer,
    onTertiaryContainer = SawnPalette.OnPrimaryContainer,
    background = SawnPalette.Background,
    onBackground = SawnPalette.Ink,
    surface = SawnPalette.Surface,
    onSurface = SawnPalette.Ink,
    surfaceVariant = SawnPalette.SurfaceSoft,
    onSurfaceVariant = SawnPalette.Muted,
    surfaceTint = SawnPalette.Primary,
    surfaceContainerLowest = SawnPalette.Surface,
    surfaceContainerLow = SawnPalette.SurfaceSoft,
    surfaceContainer = SawnPalette.SurfaceSoft,
    surfaceContainerHigh = SawnPalette.Background,
    surfaceContainerHighest = Color(0xFFEEE8DD),
    outline = SawnPalette.Line,
    outlineVariant = Color(0xFFE8E0D3),
    error = SawnPalette.Error,
    onError = SawnPalette.OnPrimary,
    errorContainer = SawnPalette.ErrorContainer,
    onErrorContainer = Color(0xFF5F1410),
)

private val SawnDarkColors = darkColorScheme(
    primary = SawnNight.Primary,
    onPrimary = SawnNight.OnPrimary,
    primaryContainer = SawnNight.PrimaryContainer,
    onPrimaryContainer = SawnNight.OnPrimaryContainer,
    secondary = SawnPalette.Success,
    onSecondary = SawnNight.OnPrimary,
    secondaryContainer = SawnNight.SuccessContainer,
    onSecondaryContainer = SawnNight.OnPrimaryContainer,
    tertiary = SawnNight.Info,
    onTertiary = SawnNight.OnPrimary,
    tertiaryContainer = SawnNight.InfoContainer,
    onTertiaryContainer = SawnNight.OnPrimaryContainer,
    background = SawnNight.Background,
    onBackground = SawnNight.Ink,
    surface = SawnNight.Surface,
    onSurface = SawnNight.Ink,
    surfaceVariant = SawnNight.SurfaceSoft,
    onSurfaceVariant = SawnNight.Muted,
    surfaceTint = SawnNight.Primary,
    surfaceContainerLowest = SawnNight.Background,
    surfaceContainerLow = SawnNight.Surface,
    surfaceContainer = SawnNight.SurfaceSoft,
    surfaceContainerHigh = SawnNight.SurfaceHighest,
    surfaceContainerHighest = SawnNight.SurfaceHighest,
    outline = SawnNight.Line,
    outlineVariant = SawnNight.LineSoft,
    error = SawnNight.Error,
    onError = SawnNight.Surface,
    errorContainer = SawnNight.ErrorContainer,
    onErrorContainer = SawnNight.OnErrorContainer,
)

private val SawnShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SawnTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) SawnDarkColors else SawnLightColors,
            shapes = SawnShapes,
            typography = SawnTypography,
            content = content,
        )
    }
}
