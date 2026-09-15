package com.neoplayer.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.neoplayer.app.settings.Accent
import com.neoplayer.app.settings.AppSettings
import com.neoplayer.app.settings.ThemeMode

private fun accentColor(settings: AppSettings) = when (settings.accent) {
    Accent.ORANGE -> Color(0xFFFF7A1A)
    Accent.GREEN -> Color(0xFF45D483)
    Accent.RED -> Color(0xFFFF5364)
    Accent.BLUE -> Color(0xFF5B8CFF)
    Accent.CUSTARD -> Color(0xFFE8C978)
    Accent.CUSTOM -> Color(settings.customColor)
}

@Composable
fun NeoTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
    val accent = accentColor(settings)
    val scheme = if (dark) darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF16110D),
        background = if (settings.themeMode == ThemeMode.AMOLED) Color.Black else Color(0xFF101010),
        surface = if (settings.themeMode == ThemeMode.AMOLED) Color.Black else Color(0xFF181818),
        surfaceVariant = Color(0xFF252525),
        onBackground = Color(0xFFF6F3EF),
        onSurface = Color(0xFFF6F3EF),
        onSurfaceVariant = Color(0xFFBBB5AE)
    ) else lightColorScheme(
        primary = accent,
        background = Color(0xFFFFFBF7),
        surface = Color.White,
        surfaceVariant = Color(0xFFF0EAE4),
        onBackground = Color(0xFF1C1917),
        onSurface = Color(0xFF1C1917)
    )
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = scheme.background.toArgb()
        window.navigationBarColor = scheme.surface.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = neoTypography(), content = content)
}

@Composable
private fun neoTypography() = MaterialTheme.typography.copy(
    displaySmall = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
)
