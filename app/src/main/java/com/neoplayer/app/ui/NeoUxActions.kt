package com.neoplayer.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Central navigation hooks for additive feature shells.
 *
 * Advanced features stay implemented in their existing panels, but the entry points are exposed to
 * the core UI so they can live in predictable Spotify-style locations instead of floating around
 * screen corners.
 */
data class NeoUxActions(
    val openNeoPlus: () -> Unit = {},
    val openCollections: () -> Unit = {},
    val openTrackTools: () -> Unit = {},
    val openOfflinePro: () -> Unit = {}
)

val LocalNeoUxActions = staticCompositionLocalOf { NeoUxActions() }
