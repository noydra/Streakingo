package com.noydra.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.noydra.resources.Res
import com.noydra.resources.google_sans_bold
import com.noydra.resources.google_sans_regular
import org.jetbrains.compose.resources.Font

@Immutable
data class Palette(
    val bg: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val primaryHover: Color,
    val subtle: Color,
    val subtleHover: Color,
    val switchOff: Color,
    val switchKnob: Color,
    val flame: Color,
    val hero: Color,
    val ok: Color,
    val okBg: Color,
    val err: Color,
    val warn: Color,
    val warnBg: Color,
    val link: Color,
    val segmentSelected: Color,
)

val LightPalette = Palette(
    bg = Color(0xFFF4F5F9), card = Color(0xFFFFFFFF), border = Color(0xFFE6E8EF),
    text = Color(0xFF14161C), muted = Color(0xFF6B7080),
    primary = Color(0xFF16A34A), primaryHover = Color(0xFF12893E),
    subtle = Color(0xFFEEF0F5), subtleHover = Color(0xFFE2E5EC),
    switchOff = Color(0xFFD5D9E2), switchKnob = Color(0xFF7A8090),
    flame = Color(0xFFF97316), hero = Color(0xFFFFF4EA),
    ok = Color(0xFF16A34A), okBg = Color(0xFFDCFCE7), err = Color(0xFFDC2626),
    warn = Color(0xFFB45309), warnBg = Color(0xFFFEF3C7),
    link = Color(0xFF2563EB), segmentSelected = Color(0xFFFFFFFF),
)

val DarkPalette = Palette(
    bg = Color(0xFF0F1115), card = Color(0xFF1A1D24), border = Color(0xFF262A33),
    text = Color(0xFFF2F3F7), muted = Color(0xFF8D93A3),
    primary = Color(0xFF22C55E), primaryHover = Color(0xFF1DB053),
    subtle = Color(0xFF242833), subtleHover = Color(0xFF2E3340),
    switchOff = Color(0xFF3A3F4B), switchKnob = Color(0xFFFFFFFF),
    flame = Color(0xFFFB923C), hero = Color(0xFF2A1E14),
    ok = Color(0xFF4ADE80), okBg = Color(0xFF163322), err = Color(0xFFF87171),
    warn = Color(0xFFFBBF24), warnBg = Color(0xFF3A2E12),
    link = Color(0xFF60A5FA), segmentSelected = Color(0xFF3A3F4B),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val Colors: Palette
    @Composable get() = LocalPalette.current

@Composable
fun isDark(theme: String): Boolean = when (theme) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun appFontFamily(language: String): FontFamily =
    if (language == "zh") FontFamily.Default
    else FontFamily(
        Font(Res.font.google_sans_regular, FontWeight.Normal),
        Font(Res.font.google_sans_bold, FontWeight.Bold),
    )

@Composable
fun AppTheme(dark: Boolean, language: String, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = palette.primary,
        background = palette.bg,
        surface = palette.card,
        surfaceContainer = palette.card,
        onSurface = palette.text,
        onBackground = palette.text,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalTextStyle provides TextStyle(fontFamily = appFontFamily(language), color = palette.text),
            content = content,
        )
    }
}
