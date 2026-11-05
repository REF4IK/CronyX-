package com.winlator.cmod.ui.theme

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.winlator.cmod.core.MmkvPreferences

object ThemePrefs {
    const val THEME_MODE = "theme_mode"
    const val THEME_ID = "theme_id"
    const val DARK_MODE = "dark_mode"
    const val CUSTOM_THEME_COLOR = "custom_theme_color"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val UI_SCALE = "ui_scale"
    const val FONT_SCALE = "font_scale"
    const val CORNER_RADIUS = "corner_radius"
    const val CUSTOM_PRIMARY = "custom_theme_primary"
    const val CUSTOM_SECONDARY = "custom_theme_secondary"
    const val CUSTOM_BACKGROUND = "custom_theme_background"
    const val CUSTOM_SURFACE = "custom_theme_surface"
    const val UI_WALLPAPER = "ui_wallpaper"
    const val UI_WALLPAPER_BLUR = "ui_wallpaper_blur"
    const val UI_WALLPAPER_DARKEN = "ui_wallpaper_darken"

    const val THEME_MODE_SYSTEM = "system"

    /** Дефолты оформления. Читать их нужно отсюда, а не хардкодить в экранах. */
    const val DEFAULT_THEME_ID = "gamehub"
    const val DEFAULT_CORNER_RADIUS = "large"
    const val THEME_MODE_LIGHT = "light"
    const val THEME_MODE_DARK = "dark"

    fun resolveThemeMode(prefs: SharedPreferences): String {
        val stored = prefs.getString(THEME_MODE, null)
        if (stored != null) return stored
        val migrated = if (prefs.getBoolean(DARK_MODE, false)) THEME_MODE_DARK else THEME_MODE_LIGHT
        prefs.edit().putString(THEME_MODE, migrated).apply()
        return migrated
    }

    fun isDarkFromMode(mode: String, systemDark: Boolean): Boolean = when (mode) {
        THEME_MODE_LIGHT -> false
        THEME_MODE_DARK -> true
        else -> systemDark
    }
}

data class CustomThemeColors(
    val primaryArgb: Int,
    val secondaryArgb: Int,
    val backgroundArgb: Int,
    val surfaceArgb: Int,
)

// Material 3 Light theme colors - mapped from existing XML colors
private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
)

// Material 3 Dark theme colors
private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

data class ThemeInfo(val id: String, val name: String, val primaryColor: Color, val accentColor: Color)

val ThemesList = listOf(
    ThemeInfo("default", "Teal Classic", Color(0xFF1A6C59), Color(0xFF8CD5BC)),
    ThemeInfo("gamehub", "GameHub Glass", Color(0xFF2E9BF0), Color(0xFF7FC7FF)),
    ThemeInfo("midnight", "Midnight OLED", Color(0xFF00B2FF), Color(0xFF005180)),
    ThemeInfo("cyberpunk", "Cyberpunk Neon", Color(0xFFFF007F), Color(0xFF9D00FF)),
    ThemeInfo("royal", "Royal Obsidian", Color(0xFFD4AF37), Color(0xFF4C4015)),
    ThemeInfo("dracula", "Dracula Vampire", Color(0xFFFF5555), Color(0xFFBD93F9)),
    ThemeInfo("frost", "Nordic Frost", Color(0xFF88C0D0), Color(0xFF5E81AC)),
    ThemeInfo("forest", "Forest Moss", Color(0xFF2E6F40), Color(0xFF81C784)),
    ThemeInfo("ocean", "Ocean Breeze", Color(0xFF00838F), Color(0xFF4DD0E1)),
    ThemeInfo("sakura", "Sakura Blossom", Color(0xFFC2185B), Color(0xFFF48FB1)),
    ThemeInfo("sunset", "Sunset Glow", Color(0xFFE65100), Color(0xFFFFB74D)),
    ThemeInfo("matrix", "Matrix Code", Color(0xFF00DD00), Color(0xFF00FF00)),
    ThemeInfo("monochrome", "Silver Steel", Color(0xFF455A64), Color(0xFFCFD8DC)),
    ThemeInfo("chocolate", "Sweet Cocoa", Color(0xFF5D4037), Color(0xFFD7CCC8)),
    ThemeInfo("custom", "Custom Palette", Color(0xFF1A6C59), Color(0xFF8CD5BC))
)

fun getColorSchemeFor(
    themeId: String,
    isDark: Boolean,
    customColorArgb: Int,
    customColors: CustomThemeColors? = null,
): ColorScheme {
    val customColor = Color(customColorArgb)
    return when (themeId) {
        // GameHub Glass: почти чёрный фон, синий акцент, полупрозрачные
        // поверхности. surfaceVariant несёт альфу, поэтому самописные Card
        // в экранах автоматически становятся стеклянными.
        "gamehub" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF2E9BF0),
                onPrimary = Color(0xFF001A2E),
                primaryContainer = Color(0xFF123A57),
                onPrimaryContainer = Color(0xFFCFE7FF),
                secondary = Color(0xFF7FC7FF),
                onSecondary = Color(0xFF002033),
                secondaryContainer = Color(0xFF1E4460),
                onSecondaryContainer = Color(0xFFD1ECFF),
                tertiary = Color(0xFFB9C6D4),
                onTertiary = Color(0xFF24303B),
                background = Color(0xFF0B0B0D),
                onBackground = Color(0xFFF2F2F4),
                surface = Color(0xFF121215),
                onSurface = Color(0xFFF2F2F4),
                surfaceVariant = Color(0xFF1B1B21).copy(alpha = 0.92f),
                onSurfaceVariant = Color(0xFF9A9AA0),
                surfaceContainer = Color(0xFF15151A),
                surfaceContainerLow = Color(0xFF101013),
                surfaceContainerHigh = Color(0xFF1C1C22),
                surfaceContainerHighest = Color(0xFF232329),
                outline = Color(0xFF6E6E74),
                outlineVariant = Color(0xFF3A3A40),
                error = Color(0xFFFF6B6B),
                onError = Color(0xFF3B0000),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF1877D1),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFD3E9FF),
                onPrimaryContainer = Color(0xFF00203A),
                secondary = Color(0xFF00639B),
                onSecondary = Color(0xFFFFFFFF),
                background = Color(0xFFF6F7F9),
                onBackground = Color(0xFF15161A),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF15161A),
                surfaceVariant = Color(0xFF1B1B21).copy(alpha = 0.06f),
                onSurfaceVariant = Color(0xFF5B6169),
                outline = Color(0xFF757B85),
                outlineVariant = Color(0xFFC4C8D0),
            )
        }
        "midnight" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF00B2FF),
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF003366),
                onPrimaryContainer = Color(0xFFB3E0FF),
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                onBackground = Color(0xFFE5F5FF),
                onSurface = Color(0xFFE5F5FF),
                surfaceVariant = Color(0xFF121212),
                onSurfaceVariant = Color(0xFF888888),
                outline = Color(0xFF444444)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF0088CC),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFCCE6FF),
                onPrimaryContainer = Color(0xFF003366),
                background = Color(0xFFF9FBFF),
                surface = Color(0xFFF9FBFF),
                onBackground = Color(0xFF001122),
                onSurface = Color(0xFF001122),
                surfaceVariant = Color(0xFFE1E5EC),
                onSurfaceVariant = Color(0xFF40474F)
            )
        }
        "cyberpunk" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFFF007F),
                onPrimary = Color.White,
                secondary = Color(0xFF9D00FF),
                background = Color(0xFF120024),
                surface = Color(0xFF1A0033),
                onBackground = Color(0xFFFFE5F0),
                onSurface = Color(0xFFFFE5F0),
                primaryContainer = Color(0xFF4A0033),
                onPrimaryContainer = Color(0xFFFFB3D9),
                surfaceVariant = Color(0xFF2E004B),
                onSurfaceVariant = Color(0xFFD3A2FF)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFE60073),
                onPrimary = Color.White,
                secondary = Color(0xFF7A00CC),
                background = Color(0xFFFFF0F5),
                surface = Color(0xFFFFF0F5),
                onBackground = Color(0xFF290014),
                onSurface = Color(0xFF290014),
                surfaceVariant = Color(0xFFFFD1E6),
                onSurfaceVariant = Color(0xFF5C3D4D)
            )
        }
        "royal" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFD4AF37),
                onPrimary = Color.Black,
                background = Color(0xFF121212),
                surface = Color(0xFF1A1A1A),
                onBackground = Color(0xFFF9F6F0),
                onSurface = Color(0xFFF9F6F0),
                primaryContainer = Color(0xFF3F350F),
                onPrimaryContainer = Color(0xFFFFF1C2),
                surfaceVariant = Color(0xFF282828),
                onSurfaceVariant = Color(0xFFC5C5C5)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF967205),
                onPrimary = Color.White,
                background = Color(0xFFFFFDF9),
                surface = Color(0xFFFFFDF9),
                onBackground = Color(0xFF211D03),
                onSurface = Color(0xFF211D03),
                surfaceVariant = Color(0xFFEDE6D3),
                onSurfaceVariant = Color(0xFF4F4A3C)
            )
        }
        "dracula" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFFF5555),
                onPrimary = Color.Black,
                secondary = Color(0xFFBD93F9),
                background = Color(0xFF282A36),
                surface = Color(0xFF282A36),
                onBackground = Color(0xFFF8F8F2),
                onSurface = Color(0xFFF8F8F2),
                primaryContainer = Color(0xFF44475A),
                onPrimaryContainer = Color(0xFFF8F8F2),
                surfaceVariant = Color(0xFF3A3D52),
                onSurfaceVariant = Color(0xFFF8F8F2)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFC53939),
                onPrimary = Color.White,
                secondary = Color(0xFF7A4FBA),
                background = Color(0xFFF9F8F9),
                surface = Color(0xFFF9F8F9),
                onBackground = Color(0xFF2A1C2A),
                onSurface = Color(0xFF2A1C2A),
                surfaceVariant = Color(0xFFEDE9EE),
                onSurfaceVariant = Color(0xFF4B424B)
            )
        }
        "frost" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF88C0D0),
                onPrimary = Color(0xFF2E3440),
                secondary = Color(0xFF81A1C1),
                background = Color(0xFF2E3440),
                surface = Color(0xFF3B4252),
                onBackground = Color(0xFFECEFF4),
                onSurface = Color(0xFFECEFF4),
                primaryContainer = Color(0xFF434C5E),
                onPrimaryContainer = Color(0xFF8FBCBB),
                surfaceVariant = Color(0xFF4C566A),
                onSurfaceVariant = Color(0xFFD8DEE9)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF4C566A),
                onPrimary = Color.White,
                secondary = Color(0xFF5E81AC),
                background = Color(0xFFECEFF4),
                surface = Color(0xFFD8DEE9),
                onBackground = Color(0xFF2E3440),
                onSurface = Color(0xFF2E3440),
                surfaceVariant = Color(0xFFE5E9F0),
                onSurfaceVariant = Color(0xFF4C566A)
            )
        }
        "forest" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF81C784),
                onPrimary = Color(0xFF003300),
                background = Color(0xFF132515),
                surface = Color(0xFF1B2F1D),
                onBackground = Color(0xFFE8F5E9),
                onSurface = Color(0xFFE8F5E9),
                primaryContainer = Color(0xFF2E6F40),
                onPrimaryContainer = Color(0xFFC8E6C9),
                surfaceVariant = Color(0xFF2B3A2E),
                onSurfaceVariant = Color(0xFFB9CBBF)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF2E6F40),
                onPrimary = Color.White,
                background = Color(0xFFF1F8F3),
                surface = Color(0xFFE8F5E9),
                onBackground = Color(0xFF002000),
                onSurface = Color(0xFF002000),
                surfaceVariant = Color(0xFFC8E6C9),
                onSurfaceVariant = Color(0xFF334A38)
            )
        }
        "ocean" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF4DD0E1),
                onPrimary = Color(0xFF00363A),
                background = Color(0xFF00161A),
                surface = Color(0xFF002428),
                onBackground = Color(0xFFE0F7FA),
                onSurface = Color(0xFFE0F7FA),
                primaryContainer = Color(0xFF006064),
                onPrimaryContainer = Color(0xFFB2EBF2),
                surfaceVariant = Color(0xFF20353A),
                onSurfaceVariant = Color(0xFFAEC4C9)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF00838F),
                onPrimary = Color.White,
                background = Color(0xFFE0F7FA),
                surface = Color(0xFFB2EBF2),
                onBackground = Color(0xFF002D30),
                onSurface = Color(0xFF002D30),
                surfaceVariant = Color(0xFF80DEEA),
                onSurfaceVariant = Color(0xFF004D40)
            )
        }
        "sakura" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFF48FB1),
                onPrimary = Color(0xFF4A0020),
                background = Color(0xFF201317),
                surface = Color(0xFF2A1C20),
                onBackground = Color(0xFFFCE4EC),
                onSurface = Color(0xFFFCE4EC),
                primaryContainer = Color(0xFF880E4F),
                onPrimaryContainer = Color(0xFFF8BBD0),
                surfaceVariant = Color(0xFF3A2B30),
                onSurfaceVariant = Color(0xFFECA3BD)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFC2185B),
                onPrimary = Color.White,
                background = Color(0xFFFFF0F5),
                surface = Color(0xFFFCE4EC),
                onBackground = Color(0xFF3F001D),
                onSurface = Color(0xFF3F001D),
                surfaceVariant = Color(0xFFF8BBD0),
                onSurfaceVariant = Color(0xFF880E4F)
            )
        }
        "sunset" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFFFB74D),
                onPrimary = Color(0xFF4E2500),
                background = Color(0xFF1F1510),
                surface = Color(0xFF2D1F18),
                onBackground = Color(0xFFFFF3E0),
                onSurface = Color(0xFFFFF3E0),
                primaryContainer = Color(0xFFE65100),
                onPrimaryContainer = Color(0xFFFFCC80),
                surfaceVariant = Color(0xFF3E2F27),
                onSurfaceVariant = Color(0xFFF5C099)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFE65100),
                onPrimary = Color.White,
                background = Color(0xFFFFF8E1),
                surface = Color(0xFFFFE0B2),
                onBackground = Color(0xFF3E1B00),
                onSurface = Color(0xFF3E1B00),
                surfaceVariant = Color(0xFFFFCC80),
                onSurfaceVariant = Color(0xFFE65100)
            )
        }
        "matrix" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF00FF00),
                onPrimary = Color.Black,
                background = Color(0xFF000800),
                surface = Color(0xFF001200),
                onBackground = Color(0xFFD0FFD0),
                onSurface = Color(0xFFD0FFD0),
                primaryContainer = Color(0xFF004400),
                onPrimaryContainer = Color(0xFF80FF80),
                surfaceVariant = Color(0xFF002200),
                onSurfaceVariant = Color(0xFF00FF00)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF008800),
                onPrimary = Color.White,
                background = Color(0xFFF0FFF0),
                surface = Color(0xFFD0FFD0),
                onBackground = Color(0xFF003300),
                onSurface = Color(0xFF003300),
                surfaceVariant = Color(0xFF80FF80),
                onSurfaceVariant = Color(0xFF004400)
            )
        }
        "monochrome" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFCFD8DC),
                onPrimary = Color.Black,
                background = Color(0xFF1A2124),
                surface = Color(0xFF263238),
                onBackground = Color(0xFFECEFF1),
                onSurface = Color(0xFFECEFF1),
                primaryContainer = Color(0xFF37474F),
                onPrimaryContainer = Color(0xFFB0BEC5),
                surfaceVariant = Color(0xFF3C4950),
                onSurfaceVariant = Color(0xFFCFD8DC)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF455A64),
                onPrimary = Color.White,
                background = Color(0xFFECEFF1),
                surface = Color(0xFFCFD8DC),
                onBackground = Color(0xFF1E272C),
                onSurface = Color(0xFF1E272C),
                surfaceVariant = Color(0xFFB0BEC5),
                onSurfaceVariant = Color(0xFF37474F)
            )
        }
        "chocolate" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFD7CCC8),
                onPrimary = Color(0xFF2E1912),
                background = Color(0xFF231A17),
                surface = Color(0xFF3E2723),
                onBackground = Color(0xFFEFEBE9),
                onSurface = Color(0xFFEFEBE9),
                primaryContainer = Color(0xFF5D4037),
                onPrimaryContainer = Color(0xFFBCAAA4),
                surfaceVariant = Color(0xFF4E342E),
                onSurfaceVariant = Color(0xFFD7CCC8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF5D4037),
                onPrimary = Color.White,
                background = Color(0xFFEFEBE9),
                surface = Color(0xFFD7CCC8),
                onBackground = Color(0xFF2E1912),
                onSurface = Color(0xFF2E1912),
                surfaceVariant = Color(0xFFBCAAA4),
                onSurfaceVariant = Color(0xFF4E342E)
            )
        }
        "custom" -> {
            val primary = Color(customColors?.primaryArgb ?: customColorArgb)
            val secondary = Color(customColors?.secondaryArgb ?: 0xFF8CD5BC.toInt())
            val bg = Color(customColors?.backgroundArgb ?: (if (isDark) 0xFF121212 else 0xFFFBFDF9).toInt())
            val surface = Color(customColors?.surfaceArgb ?: (if (isDark) 0xFF161616 else 0xFFF5F7F5).toInt())
            val surfaceVariant = if (isDark) {
                androidx.compose.ui.graphics.lerp(surface, Color(0xFF9E9E9E), 0.18f)
            } else {
                androidx.compose.ui.graphics.lerp(surface, Color.Black, 0.07f)
            }
            val onBg = if (bg.luminance() > 0.5f) Color(0xFF191C1A) else Color(0xFFE1E3DF)
            val onSurfaceC = if (surface.luminance() > 0.5f) Color(0xFF191C1A) else Color(0xFFE1E3DF)
            if (isDark) {
                darkColorScheme(
                    primary = primary,
                    onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = primary.copy(alpha = 0.2f),
                    onPrimaryContainer = primary,
                    secondary = secondary,
                    onSecondary = if (secondary.luminance() > 0.5f) Color.Black else Color.White,
                    background = bg,
                    surface = surface,
                    onBackground = onBg,
                    onSurface = onSurfaceC,
                    surfaceVariant = surfaceVariant,
                    onSurfaceVariant = Color(0xFFC0C9C2),
                    outline = Color(0xFF8A938C),
                    outlineVariant = Color(0xFF404943)
                )
            } else {
                lightColorScheme(
                    primary = primary,
                    onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = primary.copy(alpha = 0.2f),
                    onPrimaryContainer = primary,
                    secondary = secondary,
                    onSecondary = if (secondary.luminance() > 0.5f) Color.Black else Color.White,
                    background = bg,
                    surface = surface,
                    onBackground = onBg,
                    onSurface = onSurfaceC,
                    surfaceVariant = surfaceVariant,
                    onSurfaceVariant = Color(0xFF404943),
                    outline = Color(0xFF707973),
                    outlineVariant = Color(0xFFC0C9C2)
                )
            }
        }
        else -> if (isDark) DarkColorScheme else LightColorScheme
    }
}

@Composable
fun SharedPreferences.observeString(key: String, defaultValue: String): State<String> {
    val state = remember { mutableStateOf(getString(key, defaultValue) ?: defaultValue) }
    DisposableEffect(this, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = getString(key, defaultValue) ?: defaultValue
            }
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
fun SharedPreferences.observeBoolean(key: String, defaultValue: Boolean): State<Boolean> {
    val state = remember { mutableStateOf(getBoolean(key, defaultValue)) }
    DisposableEffect(this, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = getBoolean(key, defaultValue)
            }
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
fun SharedPreferences.observeInt(key: String, defaultValue: Int): State<Int> {
    val state = remember { mutableStateOf(getInt(key, defaultValue)) }
    DisposableEffect(this, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = getInt(key, defaultValue)
            }
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
fun SharedPreferences.observeFloat(key: String, defaultValue: Float): State<Float> {
    val state = remember { mutableStateOf(getFloat(key, defaultValue)) }
    DisposableEffect(this, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                state.value = getFloat(key, defaultValue)
            }
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinlatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Переопределяет настройку режима темы. Без него `darkTheme = true` ничего
    // не делает, если в приложении выбран режим «светлая»: isDarkFromMode
    // возвращает false независимо от переданного флага.
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { MmkvPreferences() }

    val themeMode by prefs.observeString(ThemePrefs.THEME_MODE, ThemePrefs.resolveThemeMode(prefs))
    val themeId by prefs.observeString(ThemePrefs.THEME_ID, ThemePrefs.DEFAULT_THEME_ID)
    val customColor by prefs.observeInt(ThemePrefs.CUSTOM_THEME_COLOR, 0xFF1A6C59.toInt())
    val dynamicEnabled by prefs.observeBoolean(ThemePrefs.DYNAMIC_COLOR, false)
    val uiScale by prefs.observeFloat(ThemePrefs.UI_SCALE, 0.85f)
    val fontScale by prefs.observeFloat(ThemePrefs.FONT_SCALE, 0.85f)
    val cornerRadius by prefs.observeString(ThemePrefs.CORNER_RADIUS, ThemePrefs.DEFAULT_CORNER_RADIUS)
    val customPrimary by prefs.observeInt(
        ThemePrefs.CUSTOM_PRIMARY,
        if (prefs.contains(ThemePrefs.CUSTOM_THEME_COLOR)) customColor else 0xFF1A6C59.toInt(),
    )
    val customSecondary by prefs.observeInt(ThemePrefs.CUSTOM_SECONDARY, 0xFF8CD5BC.toInt())
    val customBackground by prefs.observeInt(ThemePrefs.CUSTOM_BACKGROUND, 0xFF121212.toInt())
    val customSurface by prefs.observeInt(ThemePrefs.CUSTOM_SURFACE, 0xFF161616.toInt())
    val uiWallpaper by prefs.observeString(ThemePrefs.UI_WALLPAPER, "")
    val surfaceAlpha by prefs.observeInt("ui_wallpaper_surface_alpha", 60)

    val effectiveDark = forceDark || ThemePrefs.isDarkFromMode(themeMode, darkTheme)

    val colorScheme = if (dynamicEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(themeId, effectiveDark, customPrimary, customSecondary, customBackground, customSurface) {
            getColorSchemeFor(
                themeId,
                effectiveDark,
                customPrimary,
                CustomThemeColors(
                    primaryArgb = customPrimary,
                    secondaryArgb = customSecondary,
                    backgroundArgb = customBackground,
                    surfaceArgb = customSurface,
                ),
            )
        }
    }

    // Обои установлены (изображение/GIF/видео): поверхности становятся
    // полупрозрачными, чтобы обои просвечивали сквозь карточки/списки.
    // В горизонтальном режиме обои не показываются — поверхности обычные
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val translucentSurfaces = remember(uiWallpaper) { uiWallpaper.isNotEmpty() } && !isLandscape
    val effectiveColorScheme = if (translucentSurfaces) {
        val alpha = (surfaceAlpha.coerceIn(0, 100) / 100f)
        colorScheme.copy(
            background = colorScheme.background.copy(alpha = alpha),
            surface = colorScheme.surface.copy(alpha = alpha),
            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = alpha),
            surfaceContainer = colorScheme.surfaceContainer.copy(alpha = alpha),
            surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = alpha),
            surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = alpha),
            surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = alpha),
            surfaceContainerLowest = colorScheme.surfaceContainerLowest.copy(alpha = alpha),
        )
    } else {
        colorScheme
    }

    // extraSmall/extraLarge задаются явно: AlertDialog берёт радиус из extraLarge,
    // а Surface без явного shape — из small. Без этого стеклянные диалоги получали
    // углы от дефолтного ключа темы, а не 26dp, как карточки.
    val shapes = when (cornerRadius) {
        "small" -> Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(12.dp),
            extraLarge = RoundedCornerShape(12.dp),
        )
        "large" -> Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(26.dp),
        )
        else -> Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(24.dp),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Цвет статус-бара задан прозрачным в MainActivity (edge-to-edge),
            // здесь только светлая/тёмная иконография
            WindowCompat.getInsetsController((view.context as Activity).window, view).isAppearanceLightStatusBars = !effectiveDark
        }
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, uiScale, fontScale) {
        Density(
            density = baseDensity.density * uiScale.coerceIn(0.85f, 1.3f),
            fontScale = baseDensity.fontScale * fontScale.coerceIn(0.85f, 1.3f),
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = effectiveColorScheme,
            shapes = shapes,
            content = content,
        )
    }
}
