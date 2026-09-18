from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def replace_regex(rel, pattern, replacement, count=1):
    path = ROOT / rel
    text = path.read_text()
    new, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f"{rel}: expected {count} replacement(s), got {n} for {pattern[:80]!r}")
    path.write_text(new)


def replace_exact(rel, old, new):
    path = ROOT / rel
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"{rel}: exact block not found: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


# Contextual navigation contract. No capability disappears; each feature family gets a stable
# Spotify-like home instead of a generic floating feature launcher.
(ROOT / "app/src/main/java/com/neoplayer/app/ui/NeoUxActions.kt").write_text('''package com.neoplayer.app.ui

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
''')

# NEO+ is now a contextual destination instead of a multi-tab super-panel.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlusPanel.kt",
    r'@Composable\nfun NeoPlayerEnhancedApp\(.*?\n@Composable\nprivate fun PlaylistHub',
    '''@Composable
fun NeoPlayerEnhancedApp(mainViewModel: MainViewModel, plusViewModel: NeoPlusViewModel) {
    var open by remember { mutableStateOf<NeoPlusSection?>(null) }
    val inheritedActions = LocalNeoUxActions.current
    CompositionLocalProvider(
        LocalNeoUxActions provides inheritedActions.copy(openNeoPlus = { section -> open = section })
    ) {
        Box(Modifier.fillMaxSize()) {
            NeoPlayerApp(mainViewModel)
            open?.let { section -> NeoPlusPanel(plusViewModel, section) { open = null } }
        }
    }
}

@Composable
private fun NeoPlusPanel(vm: NeoPlusViewModel, section: NeoPlusSection, close: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val fa = settings.language == "fa" || (settings.language == "system" && Locale.getDefault().language == "fa")
    val title = when (section) {
        NeoPlusSection.PLAYLISTS -> t(fa, "Playlist management", "مدیریت پلی‌لیست")
        NeoPlusSection.AUDIO -> t(fa, "Playback & audio", "پخش و صدا")
        NeoPlusSection.LIBRARY -> t(fa, "Library settings", "تنظیمات کتابخانه")
        NeoPlusSection.SEARCH -> t(fa, "Search history & suggestions", "تاریخچه و پیشنهادهای جستجو")
        NeoPlusSection.CACHE -> t(fa, "Storage & cache", "حافظه و کش")
    }
    val subtitle = when (section) {
        NeoPlusSection.PLAYLISTS -> t(fa, "Folders, order, visibility and playlist layout", "پوشه‌ها، ترتیب، نمایش و چیدمان پلی‌لیست")
        NeoPlusSection.AUDIO -> t(fa, "Normalization, AutoMix and transition controls", "نرمال‌سازی، اتومیکس و کنترل انتقال")
        NeoPlusSection.LIBRARY -> t(fa, "Source folders, hidden tracks and library layout", "پوشه‌های منبع، آهنگ‌های مخفی و چیدمان کتابخانه")
        NeoPlusSection.SEARCH -> t(fa, "Recent searches and local suggestions", "جستجوهای اخیر و پیشنهادهای محلی")
        NeoPlusSection.CACHE -> t(fa, "Regeneratable local cache only", "فقط کش محلی قابل بازسازی")
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                IconButton(close) { Icon(Icons.Rounded.Close, t(fa, "Close", "بستن")) }
            }
            HorizontalDivider()
            when (section) {
                NeoPlusSection.PLAYLISTS -> PlaylistHub(vm, fa)
                NeoPlusSection.AUDIO -> AudioHub(vm, fa)
                NeoPlusSection.LIBRARY -> LibraryHub(vm, fa)
                NeoPlusSection.SEARCH -> SearchHub(vm, fa)
                NeoPlusSection.CACHE -> CacheHub(vm, fa)
            }
        }
    }
}

@Composable
private fun PlaylistHub'''
)

# Collection organizer is only reachable from Your Library, and opens on the matching collection.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoCompleteApp.kt",
    r'@Composable\nfun NeoCompleteApp\(.*?\nprivate data class AlbumCollection',
    '''@Composable
fun NeoCompleteApp(mainViewModel: MainViewModel, plusViewModel: NeoPlusViewModel) {
    var organizerOpen by remember { mutableStateOf<CollectionSection?>(null) }
    val inheritedActions = LocalNeoUxActions.current
    CompositionLocalProvider(
        LocalNeoUxActions provides inheritedActions.copy(openCollections = { section -> organizerOpen = section })
    ) {
        Box(Modifier.fillMaxSize()) {
            NeoPlayerEnhancedApp(mainViewModel, plusViewModel)
            organizerOpen?.let { section -> CollectionOrganizer(plusViewModel, section) { organizerOpen = null } }
        }
    }
}

@Composable
private fun CollectionOrganizer(vm: NeoPlusViewModel, initial: CollectionSection, close: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val fa = settings.language == "fa" || (settings.language == "system" && Locale.getDefault().language == "fa")
    var tab by rememberSaveable(initial) { mutableIntStateOf(initial.ordinal) }
    val labels = listOf(tx(fa, "Albums", "آلبوم‌ها"), tx(fa, "Artists", "هنرمندان"), tx(fa, "Playlist folders", "پوشه‌های پلی‌لیست"))

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tx(fa, "Organize Your Library", "سازمان‌دهی کتابخانه"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(close) { Icon(Icons.Rounded.Close, tx(fa, "Close", "بستن")) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index, label ->
                    FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) })
                }
            }
            when (tab) {
                0 -> AlbumOrganizer(vm, fa)
                1 -> ArtistOrganizer(vm, fa)
                else -> NestedFolderOrganizer(vm, fa)
            }
        }
    }
}

private data class AlbumCollection'''
)

# Track+ is split into contextual track tools. The user never lands in an unrelated tab anymore.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/TrackExperienceApp.kt",
    r'@Composable\nfun NeoUltimateApp\(.*?\n@androidx\.annotation\.OptIn',
    '''@Composable
fun NeoUltimateApp(
    mainViewModel: MainViewModel,
    plusViewModel: NeoPlusViewModel,
    experienceViewModel: TrackExperienceViewModel
) {
    val current by experienceViewModel.currentSong.collectAsState()
    val profile by experienceViewModel.currentProfile.collectAsState()
    var open by remember { mutableStateOf<TrackToolsSection?>(null) }
    val themeOverride = profile?.takeIf { it.themeMode == "custom" && it.backgroundArgb != 0 }?.let {
        TrackThemeOverride(it.accentArgb, it.backgroundArgb, it.secondaryArgb)
    }
    val inheritedActions = LocalNeoUxActions.current
    CompositionLocalProvider(
        LocalTrackThemeOverride provides themeOverride,
        LocalNeoUxActions provides inheritedActions.copy(
            openTrackTools = { section -> if (current != null) open = section }
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            NeoCompleteApp(mainViewModel, plusViewModel)
            open?.let { section ->
                if (current != null) TrackExperiencePanel(experienceViewModel, section) { open = null }
            }
        }
    }
}

@Composable
private fun TrackExperiencePanel(vm: TrackExperienceViewModel, section: TrackToolsSection, close: () -> Unit) {
    val song by vm.currentSong.collectAsState()
    val profile by vm.currentProfile.collectAsState()
    val settings by vm.settings.collectAsState()
    val baseScheme = MaterialTheme.colorScheme
    val active = profile ?: song?.let { TrackVisualProfileEntity(it.id) }
    val customTheme = active?.themeMode == "custom"
    val accent = if (customTheme && active?.accentArgb != 0) Color(active!!.accentArgb) else baseScheme.primary
    val background = if (customTheme && active?.backgroundArgb != 0) Color(active!!.backgroundArgb) else baseScheme.background
    val secondary = if (customTheme && active?.secondaryArgb != 0) Color(active!!.secondaryArgb) else baseScheme.secondary
    val scheme = baseScheme.copy(
        primary = accent,
        secondary = secondary,
        background = background,
        surface = background,
        surfaceVariant = blend(background, baseScheme.surfaceVariant, .35f)
    )
    val fa = settings.language == "fa" || (settings.language == "system" && Locale.getDefault().language == "fa")
    val title = when (section) {
        TrackToolsSection.VISUAL -> tx(fa, "Track appearance", "ظاهر آهنگ")
        TrackToolsSection.LYRICS_AI -> tx(fa, "Lyrics AI", "متن هوشمند")
        TrackToolsSection.RECOMMENDATIONS -> tx(fa, "More like this", "پیشنهادهای مشابه")
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(
                            song?.let { "${it.title} • ${it.artist}" } ?: tx(fa, "No track", "بدون آهنگ"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(close) { Icon(Icons.Rounded.Close, tx(fa, "Close", "بستن")) }
                }
                HorizontalDivider()
                when (section) {
                    TrackToolsSection.VISUAL -> VisualTrackTab(vm, fa)
                    TrackToolsSection.LYRICS_AI -> OfflineLyricsTab(vm, fa)
                    TrackToolsSection.RECOMMENDATIONS -> RecommendationTab(vm, fa)
                }
            }
        }
    }
}

@androidx.annotation.OptIn'''
)

# Offline Pro is also contextual: backup/offline are settings, analysis/visual are track context,
# and playlist pro belongs to Your Library.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoOfflineProApp.kt",
    r'@Composable\nfun NeoOfflineProApp\(.*?\n@Composable\nprivate fun BackupTab',
    '''@Composable
fun NeoOfflineProApp(
    mainViewModel: MainViewModel,
    plusViewModel: NeoPlusViewModel,
    experienceViewModel: TrackExperienceViewModel,
    proViewModel: OfflineProViewModel
) {
    val settings by mainViewModel.settings.collectAsState()
    val profile by proViewModel.currentProfile.collectAsState()
    val wallpaper = profile?.backgroundImageUri?.takeIf { it.isNotBlank() }?.let {
        TrackWallpaperOverride(it, profile!!.backgroundOpacity, profile!!.backgroundBlurDp)
    }
    var open by remember { mutableStateOf<OfflineProSection?>(null) }

    NeoTheme(settings) {
        val inheritedActions = LocalNeoUxActions.current
        CompositionLocalProvider(
            LocalTrackWallpaperOverride provides wallpaper,
            LocalNeoUxActions provides inheritedActions.copy(openOfflinePro = { section -> open = section })
        ) {
            Box(Modifier.fillMaxSize()) {
                NeoUltimateApp(mainViewModel, plusViewModel, experienceViewModel)
                open?.let { section -> OfflineProPanel(proViewModel, plusViewModel, section) { open = null } }
            }
        }
    }
}

@Composable
private fun OfflineProPanel(vm: OfflineProViewModel, plusVm: NeoPlusViewModel, section: OfflineProSection, close: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val fa = settings.language == "fa" || (settings.language == "system" && Locale.getDefault().language == "fa")
    val title = when (section) {
        OfflineProSection.BACKUP -> ptx(fa, "Offline Backup", "پشتیبان آفلاین")
        OfflineProSection.ANALYSIS -> ptx(fa, "Audio analysis", "آنالیز صدا")
        OfflineProSection.VISUAL_PRO -> ptx(fa, "Advanced visuals", "تصویر حرفه‌ای")
        OfflineProSection.PLAYLISTS -> ptx(fa, "Playlist tools", "ابزار پلی‌لیست")
        OfflineProSection.OFFLINE_MODE -> ptx(fa, "Offline mode", "حالت آفلاین")
    }
    val subtitle = when (section) {
        OfflineProSection.BACKUP -> ptx(fa, "Keep a smart local listening backup ready", "پشتیبان هوشمند محلی را آماده نگه دار")
        OfflineProSection.ANALYSIS -> ptx(fa, "Deep on-device analysis for the current track or library", "آنالیز عمیق روی دستگاه برای آهنگ یا کتابخانه")
        OfflineProSection.VISUAL_PRO -> ptx(fa, "Advanced visual profile for the current track", "پروفایل تصویری حرفه‌ای برای آهنگ فعلی")
        OfflineProSection.PLAYLISTS -> ptx(fa, "Advanced playlist ordering and local tools", "ترتیب‌دهی و ابزار پیشرفته پلی‌لیست")
        OfflineProSection.OFFLINE_MODE -> ptx(fa, "Network and local-only behavior", "رفتار شبکه و حالت کاملاً محلی")
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                IconButton(close) { Icon(Icons.Rounded.Close, ptx(fa, "Close", "بستن")) }
            }
            HorizontalDivider()
            when (section) {
                OfflineProSection.BACKUP -> BackupTab(vm, fa)
                OfflineProSection.ANALYSIS -> AnalysisTab(vm, fa)
                OfflineProSection.VISUAL_PRO -> VisualProTab(vm, fa)
                OfflineProSection.PLAYLISTS -> PlaylistProTab(vm, fa)
                OfflineProSection.OFFLINE_MODE -> StrictOfflineTab(vm, plusVm, fa)
            }
        }
    }
}

@Composable
private fun BackupTab'''
)

# Core shell: keep Home/Search/Library and add Spotify-style Create action at bottom.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    r'@Composable\nprivate fun PlayerShell\(vm: MainViewModel\) \{.*?\n@Composable\nprivate fun ScreenHeader',
    '''@Composable
private fun PlayerShell(vm: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var createTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val playback by vm.playback.collectAsState()
    val settings by vm.settings.collectAsState()
    val ux = LocalNeoUxActions.current
    val snackbar = remember { SnackbarHostState() }
    val playbackError = playback.error?.let { stringResource(R.string.playback_error, it) }
    LaunchedEffect(playbackError) { playbackError?.let { snackbar.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                AnimatedVisibility(playback.current != null) { MiniPlayer(vm) { fullPlayer = true } }
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, stringResource(item.label)) },
                            label = { Text(stringResource(item.label)) }
                        )
                    }
                    NavigationBarItem(
                        selected = false,
                        onClick = { createOpen = true },
                        icon = { Icon(Icons.Rounded.Add, stringResource(R.string.create)) },
                        label = { Text(stringResource(R.string.create)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                Destination.HOME -> HomeScreen(vm) { settingsOpen = true }
                Destination.SEARCH -> SearchScreen(vm)
                Destination.LIBRARY -> LibraryScreen(vm, ux)
            }
        }
    }
    AnimatedVisibility(
        fullPlayer,
        enter = if (settings.reduceMotion) EnterTransition.None else slideInVertically { it } + fadeIn(),
        exit = if (settings.reduceMotion) ExitTransition.None else slideOutVertically { it } + fadeOut()
    ) { NowPlayingScreen(vm) { fullPlayer = false } }
    AnimatedVisibility(
        settingsOpen,
        enter = if (settings.reduceMotion) EnterTransition.None else slideInVertically { it } + fadeIn(),
        exit = if (settings.reduceMotion) ExitTransition.None else slideOutVertically { it } + fadeOut()
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsScreen(vm) { settingsOpen = false }
        }
    }
    if (createOpen) {
        CreateMenu(
            dismiss = { createOpen = false },
            playlist = { createOpen = false; createTarget = "playlist" },
            category = { createOpen = false; createTarget = "category" },
            folder = { createOpen = false; ux.openCollections(CollectionSection.PLAYLIST_FOLDERS) }
        )
    }
    createTarget?.let { target ->
        NameDialog(
            title = stringResource(if (target == "playlist") R.string.new_playlist else R.string.new_category),
            dismiss = { createTarget = null },
            confirmLabel = stringResource(R.string.create)
        ) { name ->
            if (target == "playlist") vm.createPlaylist(name) else vm.createCategory(name)
            createTarget = null
        }
    }
    BackHandler(fullPlayer || settingsOpen) {
        if (settingsOpen) settingsOpen = false else fullPlayer = false
    }
}

@Composable
private fun CreateMenu(dismiss: () -> Unit, playlist: () -> Unit, category: () -> Unit, folder: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.create)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clickable(onClick = playlist).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.new_playlist), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.playlists), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable(onClick = category).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Category, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.new_category), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.categories), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable(onClick = folder).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.folders), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.playlists), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ScreenHeader'''
)

# Search keeps the user's requested all-songs default, while history/suggestions live in Search itself.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    r'@Composable\nprivate fun SearchScreen\(vm: MainViewModel\) \{.*?\n@Composable\nprivate fun LibraryScreen',
    '''@Composable
private fun SearchScreen(vm: MainViewModel) {
    val results by vm.results.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val query by vm.query.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val categories by vm.categories.collectAsState()
    val folders by vm.folders.collectAsState()
    val ux = LocalNeoUxActions.current
    var filter by rememberSaveable { mutableStateOf("All") }
    var menu by remember { mutableStateOf(false) }
    val filtered = remember(results, filter, query) {
        when {
            query.isBlank() && (filter == "All" || filter == "Songs") -> results
            query.isBlank() -> emptyList()
            filter == "Songs" -> results
            filter == "Artists" -> results.filter { it.artist.contains(query, ignoreCase = true) }
            filter == "Albums" -> results.filter { it.album.contains(query, ignoreCase = true) }
            filter == "Genres" -> results.filter { it.genre.contains(query, ignoreCase = true) }
            else -> results
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.search), action = {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Search history & suggestions") },
                        onClick = { menu = false; ux.openNeoPlus(NeoPlusSection.SEARCH) }
                    )
                }
            }
        })
        OutlinedTextField(
            query,
            { vm.query.value = it },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton({ vm.query.value = "" }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
        LazyRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf(
                "All" to R.string.library,
                "Songs" to R.string.songs,
                "Artists" to R.string.artists,
                "Albums" to R.string.albums,
                "Genres" to R.string.genres,
                "Playlists" to R.string.playlists,
                "Categories" to R.string.categories,
                "Folders" to R.string.folders
            ).forEach { option ->
                item {
                    FilterChip(
                        selected = filter == option.first,
                        onClick = { filter = option.first },
                        label = { Text(stringResource(option.second)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
            if (query.isBlank() && (filter == "All" || filter == "Songs")) {
                item { SectionTitle(stringResource(R.string.songs)) }
            }
            val playlistResults = if (query.isNotBlank() && (filter == "All" || filter == "Playlists")) playlists.filter { it.title.contains(query, true) || it.description.contains(query, true) } else emptyList()
            val categoryResults = if (query.isNotBlank() && (filter == "All" || filter == "Categories")) categories.filter { it.title.contains(query, true) || it.description.contains(query, true) } else emptyList()
            val folderResults = if (query.isNotBlank() && (filter == "All" || filter == "Folders")) folders.filter { it.relativePath.contains(query, true) } else emptyList()
            if (query.isNotBlank() && filtered.isEmpty() && playlistResults.isEmpty() && categoryResults.isEmpty() && folderResults.isEmpty()) {
                item { HintCard(stringResource(R.string.no_search_results)) }
            }
            items(playlistResults, key = { "playlist-${it.id}" }) { value ->
                LibraryFacetRow(Icons.Rounded.QueueMusic, value.title, stringResource(R.string.playlists)) { vm.playPlaylist(value.id) }
            }
            items(categoryResults, key = { "category-${it.id}" }) { value ->
                LibraryFacetRow(Icons.Rounded.Category, value.title, stringResource(R.string.categories)) { vm.playCategory(value.id) }
            }
            items(folderResults, key = { "folder-${it.relativePath}" }) { value ->
                LibraryFacetRow(Icons.Rounded.Folder, value.relativePath, stringResource(R.string.folders)) {
                    val tracks = vm.songs.value.filter { song -> song.relativePath == value.relativePath }
                    tracks.firstOrNull()?.let { vm.play(it, tracks) }
                }
            }
            if (filter != "Playlists" && filter != "Categories" && filter != "Folders") {
                items(filtered, key = { it.id }) { SongRow(it, favorites.contains(it.id), vm, filtered) }
            }
        }
    }
}

@Composable
private fun LibraryScreen'''
)

# Your Library owns all collection, folder and playlist management. Technical library controls are
# in the same overflow rather than on arbitrary screen corners.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    r'@Composable\nprivate fun LibraryScreen\(.*?\n@Composable private fun SongList',
    '''@Composable
private fun LibraryScreen(vm: MainViewModel, ux: NeoUxActions) {
    val labels = listOf(R.string.songs, R.string.albums, R.string.artists, R.string.genres, R.string.folders, R.string.playlists, R.string.categories)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.library), action = {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, { menu = false }) {
                    when (tab) {
                        1 -> DropdownMenuItem(
                            text = { Text("Organize albums") },
                            onClick = { menu = false; ux.openCollections(CollectionSection.ALBUMS) }
                        )
                        2 -> DropdownMenuItem(
                            text = { Text("Organize artists") },
                            onClick = { menu = false; ux.openCollections(CollectionSection.ARTISTS) }
                        )
                        5 -> {
                            DropdownMenuItem(
                                text = { Text("Playlist folders") },
                                onClick = { menu = false; ux.openCollections(CollectionSection.PLAYLIST_FOLDERS) }
                            )
                            DropdownMenuItem(
                                text = { Text("Playlist management") },
                                onClick = { menu = false; ux.openNeoPlus(NeoPlusSection.PLAYLISTS) }
                            )
                            DropdownMenuItem(
                                text = { Text("Advanced playlist tools") },
                                onClick = { menu = false; ux.openOfflinePro(OfflineProSection.PLAYLISTS) }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("Library settings") },
                        onClick = { menu = false; ux.openNeoPlus(NeoPlusSection.LIBRARY) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rescan)) },
                        onClick = { menu = false; vm.rescan() }
                    )
                }
            }
        })
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            items(labels.size) { index ->
                val selected = tab == index
                Surface(onClick = { tab = index }, modifier = Modifier.padding(4.dp), shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Text(stringResource(labels[index]), Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                }
            }
        }
        when (tab) {
            0 -> SongList(vm)
            1 -> AlbumList(vm)
            2 -> ArtistList(vm)
            3 -> GenreList(vm)
            4 -> FolderList(vm)
            5 -> CollectionList(vm, true)
            else -> CollectionList(vm, false)
        }
    }
}

@Composable private fun SongList'''
)

# Now Playing is the home for all current-track actions, not generic NEO+/Pro launchers.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    r'@Composable\nprivate fun NowPlayingScreen\(vm: MainViewModel, close: \(\) -> Unit\) \{.*?\n@Composable private fun PlayerPanel',
    '''@Composable
private fun NowPlayingScreen(vm: MainViewModel, close: () -> Unit) {
    val state by vm.playback.collectAsState(); val item = state.current ?: return
    val settings by vm.settings.collectAsState()
    val library by vm.songs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val currentSong = remember(library, item.mediaId) { library.firstOrNull { it.id == item.mediaId.toLongOrNull() } }
    val ux = LocalNeoUxActions.current
    val context = LocalContext.current
    var panel by remember { mutableStateOf("player") }
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var moreMenu by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    LaunchedEffect(state.playing) { while (state.playing) { delay(500); vm.refreshPosition() } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (settings.dynamicArtwork) AsyncImage(item.mediaMetadata.artworkUri, null, Modifier.fillMaxSize().blur(72.dp).alpha(.18f), contentScale = ContentScale.Crop)
            Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues())) {
                Row(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, delta -> verticalDrag += delta },
                            onDragEnd = { if (verticalDrag > 100) close(); verticalDrag = 0f }
                        )
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.minimize)) }
                    Text(stringResource(R.string.now_playing), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Box {
                        IconButton({ moreMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(moreMenu, { moreMenu = false }) {
                            currentSong?.let { song ->
                                if (playlists.isNotEmpty()) DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_to_playlist)) },
                                    onClick = { moreMenu = false; addToPlaylist = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.start_radio)) },
                                    onClick = { moreMenu = false; vm.startRadio(song) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_to_queue)) },
                                    onClick = { moreMenu = false; vm.addQueue(song) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Track appearance") },
                                onClick = { moreMenu = false; ux.openTrackTools(TrackToolsSection.VISUAL) }
                            )
                            DropdownMenuItem(
                                text = { Text("Lyrics AI") },
                                onClick = { moreMenu = false; ux.openTrackTools(TrackToolsSection.LYRICS_AI) }
                            )
                            DropdownMenuItem(
                                text = { Text("More like this") },
                                onClick = { moreMenu = false; ux.openTrackTools(TrackToolsSection.RECOMMENDATIONS) }
                            )
                            DropdownMenuItem(
                                text = { Text("Audio analysis") },
                                onClick = { moreMenu = false; ux.openOfflinePro(OfflineProSection.ANALYSIS) }
                            )
                            DropdownMenuItem(
                                text = { Text("Advanced visuals") },
                                onClick = { moreMenu = false; ux.openOfflinePro(OfflineProSection.VISUAL_PRO) }
                            )
                            currentSong?.let { song ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.song_information)) },
                                    onClick = { moreMenu = false; info = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_file)) },
                                    onClick = {
                                        val uri = Uri.parse(song.uri)
                                        val share = Intent(Intent.ACTION_SEND).setType(song.mimeType.ifBlank { "audio/*" }).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        context.startActivity(Intent.createChooser(share, song.title))
                                        moreMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                when (panel) {
                    "queue" -> QueuePanel(vm)
                    "lyrics" -> LyricsPanel(vm, item.mediaId.toLongOrNull() ?: -1, state.positionMs)
                    else -> PlayerPanel(vm, openQueue = { panel = "queue" }, openLyrics = { panel = "lyrics" })
                }
            }
        }
    }
    if (addToPlaylist && currentSong != null) {
        AlertDialog(
            onDismissRequest = { addToPlaylist = false },
            title = { Text(stringResource(R.string.add_to_playlist)) },
            text = {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        Text(
                            playlist.title,
                            Modifier.fillMaxWidth().clickable {
                                vm.addToPlaylist(playlist.id, currentSong.id)
                                addToPlaylist = false
                            }.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ addToPlaylist = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (info && currentSong != null) SongInfo(currentSong) { info = false }
}

@Composable private fun PlayerPanel'''
)

# Queue exposes the playback-order controls Spotify users expect in the queue context.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    r'@Composable private fun QueuePanel\(vm: MainViewModel\) \{.*?\n@Composable private fun LyricsPanel',
    '''@Composable private fun QueuePanel(vm: MainViewModel) {
    val state by vm.playback.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.queue), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            TextButton(vm::clearQueue) { Text(stringResource(R.string.clear)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.shuffle,
                onClick = vm::toggleShuffle,
                leadingIcon = { Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.shuffle)) }
            )
            FilterChip(
                selected = state.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = vm::cycleRepeat,
                leadingIcon = { Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.repeat)) }
            )
        }
        LazyColumn { itemsIndexed(state.queue, key = { _, item -> item.mediaId }) { index, item ->
            var currentIndex by remember(item.mediaId) { mutableIntStateOf(index) }
            var dragDistance by remember(item.mediaId) { mutableFloatStateOf(0f) }
            Row(Modifier.fillMaxWidth().padding(12.dp).pointerInput(item.mediaId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentIndex = index; dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = { dragDistance = 0f }
                ) { change, amount ->
                    dragDistance += amount.y
                    if (dragDistance > 56f && currentIndex < state.queue.lastIndex) {
                        vm.moveQueueItem(currentIndex, currentIndex + 1); currentIndex += 1; dragDistance = 0f
                    } else if (dragDistance < -56f && currentIndex > 0) {
                        vm.moveQueueItem(currentIndex, currentIndex - 1); currentIndex -= 1; dragDistance = 0f
                    }
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.size(48.dp), sourceUri = item.localConfiguration?.uri?.toString(), sizePx = 128)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1)
                    Text(item.mediaMetadata.artist?.toString().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.DragHandle, stringResource(R.string.reorder), Modifier.padding(horizontal = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton({ vm.moveQueueItem(index, index - 1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up)) }
                IconButton({ vm.moveQueueItem(index, index + 1) }, enabled = index < state.queue.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down)) }
                IconButton({ vm.removeQueueItem(index) }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove)) }
            }
        } }
    }
}

@Composable private fun LyricsPanel'''
)

# Lyrics AI is reachable directly from the Lyrics view as well as the Now Playing overflow.
replace_exact(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    '    val settings by vm.settings.collectAsState()\n    var editing by remember { mutableStateOf(false) }; var draft by remember(lyrics?.original) { mutableStateOf(lyrics?.original.orEmpty()) }',
    '    val settings by vm.settings.collectAsState()\n    val ux = LocalNeoUxActions.current\n    var editing by remember { mutableStateOf(false) }; var draft by remember(lyrics?.original) { mutableStateOf(lyrics?.original.orEmpty()) }'
)
replace_exact(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    '        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.lyrics), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium); TextButton({ importLrc.launch("*/*") }) { Text(stringResource(R.string.import_lrc)) }; TextButton({ editing = !editing }) { Text(stringResource(if (editing) R.string.preview else R.string.edit)) } }',
    '        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.lyrics), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium); TextButton({ ux.openTrackTools(TrackToolsSection.LYRICS_AI) }) { Text("AI") }; TextButton({ importLrc.launch("*/*") }) { Text(stringResource(R.string.import_lrc)) }; TextButton({ editing = !editing }) { Text(stringResource(if (editing) R.string.preview else R.string.edit)) } }'
)

# Settings contains only settings-shaped capabilities; generic NEO+/Pro launchers are gone.
replace_regex(
    "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt",
    r'@Composable private fun SettingsScreen\(vm: MainViewModel, close: \(\(\) -> Unit\)\? = null\) \{.*?\n@Composable private fun SettingsTitle',
    '''@Composable private fun SettingsScreen(vm: MainViewModel, close: (() -> Unit)? = null) {
    val settings by vm.settings.collectAsState(); var customDialog by remember { mutableStateOf(false) }; var clearHistory by remember { mutableStateOf(false) }
    val playback by vm.playback.collectAsState()
    val excluded by vm.excludedFolders.collectAsState()
    val audioEffects by vm.audioEffects.collectAsState()
    val context = LocalContext.current
    val equalizerIntent = remember { Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply { putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
    val ux = LocalNeoUxActions.current
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (close != null) IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.offline_by_design), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SettingsTitle(stringResource(R.string.appearance)) }
        item { ChoiceRow(stringResource(R.string.theme_mode), listOf(stringResource(R.string.system), stringResource(R.string.theme_dark), stringResource(R.string.theme_light), stringResource(R.string.theme_amoled)), settings.themeMode.ordinal) { vm.setTheme(ThemeMode.entries[it]) } }
        item { Text(stringResource(R.string.accent_color), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold) }
        item { LazyRow(Modifier.padding(horizontal = 14.dp)) { items(Accent.entries) { accent ->
            val color = accentPreview(accent, settings.customColor)
            Column(Modifier.clickable { if (accent == Accent.CUSTOM) customDialog = true else vm.setAccent(accent) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { if (settings.accent == accent) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold) }
                Text(stringResource(accentLabel(accent)), fontSize = 11.sp)
            }
        } } }
        item { ToggleRow(stringResource(R.string.dynamic_artwork), stringResource(R.string.dynamic_artwork_summary), settings.dynamicArtwork, vm::setDynamicArtwork) }
        item { ToggleRow(stringResource(R.string.reduce_animations), stringResource(R.string.reduce_animations_summary), settings.reduceMotion, vm::setReduceMotion) }
        item { SettingsTitle(stringResource(R.string.language)) }
        item { ChoiceRow(stringResource(R.string.app_language), listOf(stringResource(R.string.system), stringResource(R.string.english), stringResource(R.string.persian)), when (settings.language) { "en" -> 1; "fa" -> 2; else -> 0 }) { index ->
            val tag = listOf("system", "en", "fa")[index]; vm.setLanguage(tag)
            AppCompatDelegate.setApplicationLocales(if (tag == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
        } }
        item { SettingsTitle(stringResource(R.string.library)) }
        item { SettingsAction(Icons.Rounded.LibraryMusic, "Library settings", "Source folders, hidden songs and library layout", { ux.openNeoPlus(NeoPlusSection.LIBRARY) }) }
        item { SettingsAction(Icons.Rounded.Refresh, stringResource(R.string.rescan), stringResource(R.string.rescan_summary), vm::rescan) }
        item { ChoiceRow(stringResource(R.string.minimum_audio_duration), listOf("0s", "10s", "30s", "60s"), listOf(0L, 10_000L, 30_000L, 60_000L).indexOf(settings.minDurationMs).coerceAtLeast(0)) { vm.setMinDuration(listOf(0L, 10_000L, 30_000L, 60_000L)[it]) } }
        if (excluded.isNotEmpty()) item { Column { Text(stringResource(R.string.excluded_folders), Modifier.padding(horizontal = 20.dp)); excluded.forEach { path -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(path, Modifier.weight(1f)); TextButton({ vm.includeFolder(path) }) { Text(stringResource(R.string.include)) } } } } }
        item { SettingsTitle(stringResource(R.string.playback)) }
        item { ChoiceRow(stringResource(R.string.crossfade), listOf(stringResource(R.string.off), "3s", "5s", "8s", "12s"), listOf(0L, 3_000L, 5_000L, 8_000L, 12_000L).indexOf(settings.crossfadeMs).coerceAtLeast(0)) { vm.setCrossfadeMs(listOf(0L, 3_000L, 5_000L, 8_000L, 12_000L)[it]) } }
        item { HintCard(stringResource(R.string.gapless_automatic)) }
        item { SettingsAction(Icons.Rounded.MusicNote, "Advanced playback", "Loudness normalization, AutoMix and transition controls", { ux.openNeoPlus(NeoPlusSection.AUDIO) }) }
        item { SettingsTitle(stringResource(R.string.audio)) }
        item { HintCard(stringResource(R.string.track_effect_profile) + (playback.current?.mediaMetadata?.title?.toString()?.let { " • $it" } ?: "") + "\n" + stringResource(R.string.track_effect_profile_summary)) }
        if (audioEffects.available) {
            item { ChoiceRow(stringResource(R.string.equalizer), listOf(stringResource(R.string.normal), stringResource(R.string.bass_boost), stringResource(R.string.rock), stringResource(R.string.pop), stringResource(R.string.classical), stringResource(R.string.jazz), stringResource(R.string.electronic), stringResource(R.string.vocal), stringResource(R.string.custom)), vm.audioPresets.indexOf(audioEffects.preset).coerceAtLeast(0)) { vm.setAudioPreset(vm.audioPresets[it]) } }
            item { EffectSlider(stringResource(R.string.bass_boost), audioEffects.bass, 1000, vm::setBass) }
            item { EffectSlider(stringResource(R.string.virtualizer), audioEffects.virtualizer, 1000, vm::setVirtualizer) }
            item { EffectSlider(stringResource(R.string.loudness), audioEffects.loudnessMb, 1200, vm::setLoudness) }
            if (audioEffects.preset == "Custom") itemsIndexed(audioEffects.bandLevels) { index, level -> EffectSlider(stringResource(R.string.band_number, index + 1), level.toInt() + 1500, 3000) { vm.setEqualizerBand(index, (it - 1500).toShort()) } }
        } else if (equalizerIntent.resolveActivity(context.packageManager) != null) item { SettingsAction(Icons.Rounded.MusicNote, stringResource(R.string.equalizer), stringResource(R.string.equalizer_unavailable_summary), { context.startActivity(equalizerIntent) }) }
        item { SettingsAction(Icons.Rounded.MusicNote, "Audio analysis", "Deep on-device analysis and ReplayGain/R128 tools", { ux.openOfflinePro(OfflineProSection.ANALYSIS) }) }
        item { SettingsTitle(stringResource(R.string.lyrics)) }
        item { val modes = if (vm.onlineLyricsAvailable) listOf(stringResource(R.string.auto), stringResource(R.string.offline_only), stringResource(R.string.online_only)) else listOf(stringResource(R.string.auto), stringResource(R.string.offline_only)); ChoiceRow(stringResource(R.string.lyrics_source), modes, if (settings.lyricsMode == "offline") 1 else if (settings.lyricsMode == "online" && vm.onlineLyricsAvailable) 2 else 0) { vm.setLyricsMode(if (it == 1) "offline" else if (it == 2) "online" else "auto") } }
        item { ChoiceRow(stringResource(R.string.lyrics_size), listOf("16", "20", "24", "28"), listOf(16, 20, 24, 28).indexOf(settings.lyricsFontSize).coerceAtLeast(1)) { vm.setLyricsFontSize(listOf(16, 20, 24, 28)[it]) } }
        item { SettingsTitle("Storage & offline") }
        item { SettingsAction(Icons.Rounded.Folder, "Storage & cache", "View and clear regeneratable local cache", { ux.openNeoPlus(NeoPlusSection.CACHE) }) }
        item { SettingsAction(Icons.Rounded.QueueMusic, "Offline Backup", "Smart local backup generated from your listening", { ux.openOfflinePro(OfflineProSection.BACKUP) }) }
        item { SettingsAction(Icons.Rounded.Info, "Offline mode", "Strict local-only and network behavior", { ux.openOfflinePro(OfflineProSection.OFFLINE_MODE) }) }
        item { SettingsTitle(stringResource(R.string.privacy)) }
        item { HintCard(stringResource(R.string.privacy_summary)) }
        item { SettingsAction(Icons.Rounded.History, stringResource(R.string.clear_history), stringResource(R.string.clear_history_summary), { clearHistory = true }) }
        item { SettingsTitle(stringResource(R.string.about)) }
        item { Column(Modifier.padding(20.dp)) { Text("NEO PLAYER", style = MaterialTheme.typography.titleLarge); Text(BuildConfig.DISPLAY_VERSION + " • " + BuildConfig.VERSION_NAME); Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.app_description)); Text(stringResource(R.string.made_by), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text(stringResource(R.string.build_number, BuildConfig.VERSION_CODE) + "\nLicense: Apache-2.0") } }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (customDialog) CustomColorDialog({ customDialog = false }) { vm.setCustomColor(it); customDialog = false }
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text(stringResource(R.string.clear_history_question)) }, text = { Text(stringResource(R.string.clear_history_warning)) }, confirmButton = { TextButton({ vm.clearHistory(); clearHistory = false }) { Text(stringResource(R.string.clear)) } }, dismissButton = { TextButton({ clearHistory = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SettingsTitle'''
)

print("UX2 contextual rewrite applied")
