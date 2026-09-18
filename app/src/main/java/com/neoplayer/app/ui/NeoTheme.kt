package com.neoplayer.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.neoplayer.app.settings.Accent
import com.neoplayer.app.settings.AppSettings
import com.neoplayer.app.settings.ThemeMode

/**
 * Optional additive per-track theme. Existing screens still call NeoTheme(settings) exactly as
 * before; Track+ only supplies this CompositionLocal while a song with a custom theme is active.
 */
data class TrackThemeOverride(
    val accentArgb: Int,
    val backgroundArgb: Int,
    val secondaryArgb: Int
)

val LocalTrackThemeOverride = staticCompositionLocalOf<TrackThemeOverride?> { null }

data class TrackWallpaperOverride(
    val uri: String,
    val opacity: Float = .28f,
    val blurDp: Int = 18
)

val LocalTrackWallpaperOverride = staticCompositionLocalOf<TrackWallpaperOverride?> { null }

private fun accentColor(settings: AppSettings) = when (settings.accent) {
    Accent.ORANGE -> Color(0xFFFF7A1A)
    Accent.GREEN -> Color(0xFF45D483)
    Accent.RED -> Color(0xFFFF5364)
    Accent.BLUE -> Color(0xFF5B8CFF)
    Accent.CUSTARD -> Color(0xFFE8C978)
    Accent.PURPLE -> Color(0xFFB586FF)
    Accent.CYAN -> Color(0xFF42D9E8)
    Accent.PINK -> Color(0xFFFF6FAE)
    Accent.INDIGO -> Color(0xFF7C83FF)
    Accent.TEAL -> Color(0xFF35C6A5)
    Accent.GOLD -> Color(0xFFFFB84D)
    Accent.CUSTOM -> Color(settings.customColor)
}

private fun mix(a: Color, b: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

/**
 * Material 3 components use the newer surface-container roles heavily. Previously those roles kept
 * the library defaults, so drawers, menus, sheets and side panels could look purple/gray even while
 * the main screen was light, AMOLED, or using a custom accent. Fill every surface role from the
 * same palette so the selected theme is genuinely global.
 */
private fun completeScheme(
    base: ColorScheme,
    dark: Boolean,
    background: Color,
    surface: Color,
    accent: Color,
    secondary: Color,
    onSurface: Color
): ColorScheme {
    val toward = if (dark) Color.White else Color.Black
    val inverse = if (dark) Color(0xFFF4F1ED) else Color(0xFF202020)
    val onAccent = if (accent.luminance() > .52f) Color(0xFF111111) else Color.White
    val onSecondary = if (secondary.luminance() > .52f) Color(0xFF111111) else Color.White
    val primaryContainer = mix(background, accent, if (dark) .24f else .16f)
    val secondaryContainer = mix(background, secondary, if (dark) .20f else .13f)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onSurface,
        inversePrimary = mix(accent, inverse, .28f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSurface,
        tertiary = secondary,
        onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer,
        onTertiaryContainer = onSurface,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = mix(background, toward, if (dark) .12f else .07f),
        onSurfaceVariant = onSurface.copy(alpha = if (dark) .74f else .68f),
        surfaceTint = accent,
        inverseSurface = inverse,
        inverseOnSurface = if (dark) Color(0xFF292724) else Color(0xFFF5F2EE),
        outline = onSurface.copy(alpha = .38f),
        outlineVariant = onSurface.copy(alpha = .18f),
        scrim = Color.Black,
        surfaceDim = mix(background, toward, if (dark) .035f else .02f),
        surfaceBright = mix(background, toward, if (dark) .16f else .045f),
        surfaceContainerLowest = mix(background, toward, if (dark) .015f else .01f),
        surfaceContainerLow = mix(background, toward, if (dark) .05f else .025f),
        surfaceContainer = mix(background, toward, if (dark) .075f else .04f),
        surfaceContainerHigh = mix(background, toward, if (dark) .105f else .055f),
        surfaceContainerHighest = mix(background, toward, if (dark) .14f else .075f)
    )
}

@Composable
fun NeoTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val track = LocalTrackThemeOverride.current
    val wallpaper = LocalTrackWallpaperOverride.current
    val inheritedDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
    val backgroundOverride = track?.backgroundArgb?.takeIf { it != 0 }?.let(::Color)
    val dark = backgroundOverride?.let { it.luminance() < .48f } ?: inheritedDark
    val accent = track?.accentArgb?.takeIf { it != 0 }?.let(::Color) ?: accentColor(settings)
    val secondary = track?.secondaryArgb?.takeIf { it != 0 }?.let(::Color) ?: accent

    val background = backgroundOverride ?: when {
        dark && settings.themeMode == ThemeMode.AMOLED -> Color.Black
        dark -> Color(0xFF101010)
        else -> Color(0xFFFFFBF7)
    }
    val onSurface = if (dark) Color(0xFFF6F3EF) else Color(0xFF1C1917)
    val surface = when {
        backgroundOverride != null -> mix(background, if (dark) Color.White else Color.Black, if (dark) .07f else .035f)
        dark && settings.themeMode == ThemeMode.AMOLED -> Color.Black
        dark -> Color(0xFF181818)
        else -> Color.White
    }

    val rawScheme = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = completeScheme(rawScheme, dark, background, surface, accent, secondary, onSurface)

    val effectiveScheme = if (wallpaper != null) {
        scheme.copy(
            background = scheme.background.copy(alpha = .84f),
            surface = scheme.surface.copy(alpha = .88f),
            surfaceVariant = scheme.surfaceVariant.copy(alpha = .82f),
            surfaceDim = scheme.surfaceDim.copy(alpha = .84f),
            surfaceBright = scheme.surfaceBright.copy(alpha = .88f),
            surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = .78f),
            surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = .82f),
            surfaceContainer = scheme.surfaceContainer.copy(alpha = .84f),
            surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = .87f),
            surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = .90f)
        )
    } else scheme

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = effectiveScheme.background.copy(alpha = 1f).toArgb()
        window.navigationBarColor = effectiveScheme.surface.copy(alpha = 1f).toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = effectiveScheme, typography = neoTypography()) {
        Box(Modifier.fillMaxSize()) {
            wallpaper?.takeIf { it.uri.isNotBlank() }?.let { value ->
                AsyncImage(
                    model = value.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(value.blurDp.coerceIn(0, 48).dp)
                )
                Box(
                    Modifier.fillMaxSize().background(
                        effectiveScheme.background.copy(alpha = (1f - value.opacity.coerceIn(0f, .85f)))
                    )
                )
            }
            content()
        }
    }
}

@Composable
private fun neoTypography() = MaterialTheme.typography.copy(
    displaySmall = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
)
