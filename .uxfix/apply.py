from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    return text.replace(old, new, 1)


def replace_function(text, start_marker, end_marker, replacement, label):
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"missing function start: {label}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"missing function end: {label}")
    return text[:start] + replacement.rstrip() + "\n\n" + text[end:]


# 1) Remove the four random floating launchers and expose them through shared navigation actions.
rel = "app/src/main/java/com/neoplayer/app/ui/NeoPlusPanel.kt"
text = read(rel)
text = replace_once(
    text,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.CompositionLocalProvider\n",
    "NeoPlus CompositionLocalProvider import",
)
neo_plus = '''@Composable
fun NeoPlayerEnhancedApp(mainViewModel: MainViewModel, plusViewModel: NeoPlusViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    val inheritedActions = LocalNeoUxActions.current
    CompositionLocalProvider(
        LocalNeoUxActions provides inheritedActions.copy(openNeoPlus = { open = true })
    ) {
        Box(Modifier.fillMaxSize()) {
            NeoPlayerApp(mainViewModel)
            if (open) NeoPlusPanel(plusViewModel) { open = false }
        }
    }
}'''
text = replace_function(text, "@Composable\nfun NeoPlayerEnhancedApp", "@Composable\nprivate fun NeoPlusPanel", neo_plus, "NeoPlayerEnhancedApp")
write(rel, text)

rel = "app/src/main/java/com/neoplayer/app/ui/NeoCompleteApp.kt"
text = read(rel)
text = replace_once(
    text,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.CompositionLocalProvider\n",
    "NeoComplete CompositionLocalProvider import",
)
complete = '''@Composable
fun NeoCompleteApp(mainViewModel: MainViewModel, plusViewModel: NeoPlusViewModel) {
    var organizerOpen by rememberSaveable { mutableStateOf(false) }
    val inheritedActions = LocalNeoUxActions.current
    CompositionLocalProvider(
        LocalNeoUxActions provides inheritedActions.copy(openCollections = { organizerOpen = true })
    ) {
        Box(Modifier.fillMaxSize()) {
            NeoPlayerEnhancedApp(mainViewModel, plusViewModel)
            if (organizerOpen) CollectionOrganizer(plusViewModel) { organizerOpen = false }
        }
    }
}'''
text = replace_function(text, "@Composable\nfun NeoCompleteApp", "@Composable\nprivate fun CollectionOrganizer", complete, "NeoCompleteApp")
write(rel, text)

rel = "app/src/main/java/com/neoplayer/app/ui/TrackExperienceApp.kt"
text = read(rel)
ultimate = '''@Composable
fun NeoUltimateApp(
    mainViewModel: MainViewModel,
    plusViewModel: NeoPlusViewModel,
    experienceViewModel: TrackExperienceViewModel
) {
    val current by experienceViewModel.currentSong.collectAsState()
    val profile by experienceViewModel.currentProfile.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    val themeOverride = profile?.takeIf { it.themeMode == "custom" && it.backgroundArgb != 0 }?.let {
        TrackThemeOverride(it.accentArgb, it.backgroundArgb, it.secondaryArgb)
    }
    val inheritedActions = LocalNeoUxActions.current
    CompositionLocalProvider(
        LocalTrackThemeOverride provides themeOverride,
        LocalNeoUxActions provides inheritedActions.copy(openTrackTools = { if (current != null) open = true })
    ) {
        Box(Modifier.fillMaxSize()) {
            NeoCompleteApp(mainViewModel, plusViewModel)
            if (open && current != null) {
                TrackExperiencePanel(experienceViewModel) { open = false }
            }
        }
    }
}'''
text = replace_function(text, "@Composable\nfun NeoUltimateApp", "@Composable\nprivate fun TrackExperiencePanel", ultimate, "NeoUltimateApp")
write(rel, text)

rel = "app/src/main/java/com/neoplayer/app/ui/NeoOfflineProApp.kt"
text = read(rel)
offline = '''@Composable
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
    var open by rememberSaveable { mutableStateOf(false) }

    NeoTheme(settings) {
        val inheritedActions = LocalNeoUxActions.current
        CompositionLocalProvider(
            LocalTrackWallpaperOverride provides wallpaper,
            LocalNeoUxActions provides inheritedActions.copy(openOfflinePro = { open = true })
        ) {
            Box(Modifier.fillMaxSize()) {
                NeoUltimateApp(mainViewModel, plusViewModel, experienceViewModel)
                if (open) OfflineProPanel(proViewModel, plusViewModel) { open = false }
            }
        }
    }
}'''
text = replace_function(text, "@Composable\nfun NeoOfflineProApp", "@Composable\nprivate fun OfflineProPanel", offline, "NeoOfflineProApp")
write(rel, text)

# 2) Core Spotify-style placement: three primary destinations, settings from Home, collection tools
# in Library, and track-specific/advanced tools inside Now Playing's overflow.
rel = "app/src/main/java/com/neoplayer/app/ui/NeoPlayerApp.kt"
text = read(rel)
text = replace_once(
    text,
    '''private enum class Destination(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Rounded.Home), SEARCH(R.string.search, Icons.Rounded.Search),
    LIBRARY(R.string.library, Icons.Rounded.LibraryMusic), SETTINGS(R.string.settings, Icons.Rounded.Settings)
}''',
    '''private enum class Destination(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Rounded.Home),
    SEARCH(R.string.search, Icons.Rounded.Search),
    LIBRARY(R.string.library, Icons.Rounded.LibraryMusic)
}''',
    "primary destinations",
)
player_shell = '''@Composable
private fun PlayerShell(vm: MainViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
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
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                Destination.HOME -> HomeScreen(vm) { settingsOpen = true }
                Destination.SEARCH -> SearchScreen(vm)
                Destination.LIBRARY -> LibraryScreen(vm, ux.openCollections)
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
    BackHandler(fullPlayer || settingsOpen) {
        if (settingsOpen) settingsOpen = false else fullPlayer = false
    }
}'''
text = replace_function(text, "@Composable\nprivate fun PlayerShell", "@Composable\nprivate fun ScreenHeader", player_shell, "PlayerShell")
text = replace_once(text, "private fun HomeScreen(vm: MainViewModel) {", "private fun HomeScreen(vm: MainViewModel, openSettings: () -> Unit) {", "HomeScreen signature")
text = replace_once(
    text,
    "item { ScreenHeader(greeting, stringResource(R.string.your_music_stays_yours)) }",
    '''item {
            ScreenHeader(greeting, stringResource(R.string.your_music_stays_yours), action = {
                IconButton(openSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
            })
        }''',
    "Home settings placement",
)
search_screen = '''@Composable
private fun SearchScreen(vm: MainViewModel) {
    val results by vm.results.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val query by vm.query.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val categories by vm.categories.collectAsState()
    val folders by vm.folders.collectAsState()
    var filter by rememberSaveable { mutableStateOf("All") }
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
        ScreenHeader(stringResource(R.string.search))
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
}'''
text = replace_function(text, "@Composable\nprivate fun SearchScreen", "@Composable\nprivate fun LibraryScreen", search_screen, "SearchScreen")
text = replace_once(text, "private fun LibraryScreen(vm: MainViewModel) {", "private fun LibraryScreen(vm: MainViewModel, openCollections: () -> Unit) {", "LibraryScreen signature")
text = replace_once(
    text,
    'ScreenHeader(stringResource(R.string.library), action = { IconButton(vm::rescan) { Icon(Icons.Rounded.Refresh, stringResource(R.string.rescan)) } })',
    '''ScreenHeader(stringResource(R.string.library), action = {
            Row {
                IconButton(openCollections) { Icon(Icons.Rounded.LibraryMusic, "Collections") }
                IconButton(vm::rescan) { Icon(Icons.Rounded.Refresh, stringResource(R.string.rescan)) }
            }
        })''',
    "Library actions placement",
)
text = replace_once(
    text,
    "LaunchedEffect(sourceUri) { embedded = embeddedArtwork(context, sourceUri) }",
    "LaunchedEffect(sourceUri, uri) { embedded = if (uri.isNullOrBlank()) embeddedArtwork(context, sourceUri) else null }",
    "artwork lazy extraction",
)
now_playing = '''@Composable
private fun NowPlayingScreen(vm: MainViewModel, close: () -> Unit) {
    val state by vm.playback.collectAsState(); val item = state.current ?: return
    val settings by vm.settings.collectAsState()
    val ux = LocalNeoUxActions.current
    var panel by remember { mutableStateOf("player") }
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var moreMenu by remember { mutableStateOf(false) }
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
                            DropdownMenuItem(
                                text = { Text("Track tools") },
                                onClick = { moreMenu = false; ux.openTrackTools() }
                            )
                            DropdownMenuItem(
                                text = { Text("NEO+") },
                                onClick = { moreMenu = false; ux.openNeoPlus() }
                            )
                            DropdownMenuItem(
                                text = { Text("Offline Pro") },
                                onClick = { moreMenu = false; ux.openOfflinePro() }
                            )
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
}'''
text = replace_function(text, "@Composable\nprivate fun NowPlayingScreen", "@Composable private fun PlayerPanel", now_playing, "NowPlayingScreen")
text = replace_once(
    text,
    "@Composable private fun PlayerPanel(vm: MainViewModel) {",
    "@Composable private fun PlayerPanel(vm: MainViewModel, openQueue: () -> Unit, openLyrics: () -> Unit) {",
    "PlayerPanel signature",
)
controls_anchor = '''        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
            IconButton(vm::previous, Modifier.size(58.dp)) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(38.dp)) }
            FilledIconButton(vm::togglePlayback, Modifier.size(72.dp)) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.play_pause), Modifier.size(42.dp)) }
            IconButton(vm::next, Modifier.size(58.dp)) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(38.dp)) }
            IconButton(vm::cycleRepeat) { Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, stringResource(R.string.repeat), tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        }'''
controls_new = controls_anchor + '''
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(openLyrics) { Icon(Icons.Rounded.Lyrics, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.lyrics)) }
            TextButton(openQueue) { Icon(Icons.Rounded.QueueMusic, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.queue)) }
        }'''
text = replace_once(text, controls_anchor, controls_new, "Now Playing queue/lyrics placement")
text = replace_once(
    text,
    "@Composable private fun SettingsScreen(vm: MainViewModel) {",
    "@Composable private fun SettingsScreen(vm: MainViewModel, close: (() -> Unit)? = null) {",
    "SettingsScreen signature",
)
text = replace_once(
    text,
    "    val equalizerIntent = remember { Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply { putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }\n",
    "    val equalizerIntent = remember { Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply { putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }\n    val ux = LocalNeoUxActions.current\n",
    "Settings UX actions",
)
text = replace_once(
    text,
    'item { ScreenHeader(stringResource(R.string.settings), stringResource(R.string.offline_by_design)) }',
    '''item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (close != null) IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.offline_by_design), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }''',
    "Settings header",
)
text = replace_once(
    text,
    '        item { SettingsTitle(stringResource(R.string.library)) }',
    '''        item { SettingsTitle("NEO tools") }
        item { SettingsAction(Icons.Rounded.Settings, "NEO+", "Advanced playlists, audio, library, search and cache", ux.openNeoPlus) }
        item { SettingsAction(Icons.Rounded.MusicNote, "Offline Pro", "Offline backup, analysis, visual and strict-offline controls", ux.openOfflinePro) }
        item { SettingsTitle(stringResource(R.string.library)) }''',
    "Advanced tools in Settings",
)
write(rel, text)

print("UX patch applied")
