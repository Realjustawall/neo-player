package com.neoplayer.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class NeoPlusSection { PLAYLISTS, AUDIO, LIBRARY, SEARCH, CACHE }
enum class CollectionSection { ALBUMS, ARTISTS, PLAYLIST_FOLDERS }
enum class TrackToolsSection { VISUAL, LYRICS_AI, RECOMMENDATIONS }
enum class OfflineProSection { BACKUP, ANALYSIS, VISUAL_PRO, PLAYLISTS, OFFLINE_MODE }

/**
 * Contextual navigation hooks used by the core player UI.
 *
 * Advanced features remain implemented by their original ViewModels/panels, but callers must choose
 * the exact section they need. This prevents the old "feature dump" UX while preserving every
 * capability and keeps each tool in the place a listener naturally expects to find it.
 */
data class NeoUxActions(
    val openNeoPlus: (NeoPlusSection) -> Unit = {},
    val openCollections: (CollectionSection) -> Unit = {},
    val openTrackTools: (TrackToolsSection) -> Unit = {},
    val openOfflinePro: (OfflineProSection) -> Unit = {}
)

val LocalNeoUxActions = staticCompositionLocalOf { NeoUxActions() }
