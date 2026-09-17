package com.neoplayer.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import coil.compose.AsyncImage
import androidx.core.view.WindowCompat
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

    val scheme = if (track != null && backgroundOverride != null) {
        val background = backgroundOverride
        val onBackground = if (dark) Color(0xFFF7F4F0) else Color(0xFF171412)
        val surface = mix(background, if (dark) Color.White else Color.Black, if (dark) .07f else .035f)
        val variant = mix(background, if (dark) Color.White else Color.Black, if (dark) .14f else .08f)
        if (dark) {
            darkColorScheme(
                primary = accent,
                secondary = secondary,
                onPrimary = if (accent.luminance() > .5f) Color(0xFF111111) else Color.White,
                background = background,
                surface = surface,
                surfaceVariant = variant,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = onBackground.copy(alpha = .72f)
            )
        } else {
            lightColorScheme(
                primary = accent,
                secondary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = variant,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = onBackground.copy(alpha = .68f)
            )
        }
    } else if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF16110D),
            background = if (settings.themeMode == ThemeMode.AMOLED) Color.Black else Color(0xFF101010),
            surface = if (settings.themeMode == ThemeMode.AMOLED) Color.Black else Color(0xFF181818),
            surfaceVariant = Color(0xFF252525),
            onBackground = Color(0xFFF6F3EF),
            onSurface = Color(0xFFF6F3EF),
            onSurfaceVariant = Color(0xFFBBB5AE)
        )
    } else {
        lightColorScheme(
            primary = accent,
            background = Color(0xFFFFFBF7),
            surface = Color.White,
            surfaceVariant = Color(0xFFF0EAE4),
            onBackground = Color(0xFF1C1917),
            onSurface = Color(0xFF1C1917)
        )
    }
    val effectiveScheme = if (wallpaper != null) {
        scheme.copy(
            background = scheme.background.copy(alpha = .84f),
            surface = scheme.surface.copy(alpha = .88f),
            surfaceVariant = scheme.surfaceVariant.copy(alpha = .82f)
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
