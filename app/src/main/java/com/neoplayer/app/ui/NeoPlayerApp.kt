@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neoplayer.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.neoplayer.app.BuildConfig
import com.neoplayer.app.R
import com.neoplayer.app.data.CategoryEntity
import com.neoplayer.app.data.PlaylistEntity
import com.neoplayer.app.data.SongEntity
import com.neoplayer.app.domain.SmartMixEngine
import com.neoplayer.app.lyrics.LrcParser
import com.neoplayer.app.settings.Accent
import com.neoplayer.app.settings.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private enum class Destination(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Rounded.Home),
    SEARCH(R.string.search, Icons.Rounded.Search),
    LIBRARY(R.string.library, Icons.Rounded.LibraryMusic)
}

@Composable
fun NeoPlayerApp(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    NeoTheme(settings) {
        PermissionGate(vm) { PlayerShell(vm) }
    }
}

@Composable
private fun PermissionGate(vm: MainViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[permission] == true
    }
    if (granted) {
        val songs by vm.songs.collectAsState()
        LaunchedEffect(Unit) { if (songs.isEmpty()) vm.rescan() }
        content()
    } else {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.neo_logo), contentDescription = stringResource(R.string.app_name), modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.permission_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(28.dp))
                Button(onClick = {
                    val permissions = buildList {
                        add(permission)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    launcher.launch(permissions)
                }) { Text(stringResource(R.string.allow_access)) }
            }
        }
    }
}

@Composable
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
private fun ScreenHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel, openSettings: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val histories by vm.histories.collectAsState()
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = stringResource(when (hour) { in 5..11 -> R.string.good_morning; in 12..17 -> R.string.good_afternoon; else -> R.string.good_evening })
    val recentSongs = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(8) }
    val liked = remember(songs, favorites) { songs.filter { favorites.contains(it.id) }.take(8) }
    val playCounts = remember(histories) { histories.associate { it.songId to it.playCount } }
    val mostPlayed = remember(songs, playCounts) { songs.filter { playCounts.getOrDefault(it.id, 0) > 0 }.sortedByDescending { playCounts[it.id] }.take(8) }
    val neverPlayed = remember(songs, playCounts) { songs.filterNot { playCounts.containsKey(it.id) }.take(8) }
    val recentlyPlayed = remember(songs, histories) { SmartMixEngine.recentlyPlayed(songs, histories).take(8) }
    val forgotten = remember(songs, favorites, histories) { SmartMixEngine.forgottenFavorites(songs, favorites.toSet(), histories).take(8) }
    val dayMix = remember(songs, favorites, hour) { SmartMixEngine.timeOfDay(songs, favorites.toSet(), hour).take(8) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader(greeting, stringResource(R.string.your_music_stays_yours), action = {
                IconButton(openSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
            })
        }
        if (songs.isEmpty()) item { EmptyLibrary(vm) }
        else {
            item { SectionTitle(stringResource(R.string.recently_added)) }
            items(recentSongs, key = { "recent-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, recentSongs) }
            item { SectionTitle(stringResource(R.string.liked_songs)) }
            if (liked.isEmpty()) item { HintCard(stringResource(R.string.favorites_hint)) }
            items(liked, key = { "liked-${it.id}" }) { SongRow(it, true, vm, liked) }
            item { SectionTitle(stringResource(R.string.smart_mixes)) }
            if (mostPlayed.isNotEmpty()) {
                item { HintCard(stringResource(R.string.most_played)) }
                items(mostPlayed, key = { "most-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, mostPlayed) }
            }
            if (neverPlayed.isNotEmpty()) {
                item { HintCard(stringResource(R.string.never_played)) }
                items(neverPlayed, key = { "never-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, neverPlayed) }
            }
            if (recentlyPlayed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.recently_played)) }
                items(recentlyPlayed, key = { "recently-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, recentlyPlayed) }
            }
            if (forgotten.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.forgotten_favorites)) }
                items(forgotten, key = { "forgotten-${it.id}" }) { SongRow(it, true, vm, forgotten) }
            }
            if (dayMix.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.time_mix)) }
                items(dayMix, key = { "day-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, dayMix) }
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp))

@Composable
private fun EmptyLibrary(vm: MainViewModel) {
    val scanning by vm.scanning.collectAsState()
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp)); Text(stringResource(R.string.no_music), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.no_music_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Button(onClick = vm::rescan, enabled = !scanning) {
            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rescan))
        }
    }
}

@Composable private fun HintCard(text: String) = Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text(text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
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

@Composable private fun SongList(vm: MainViewModel) {
    val songs by vm.songs.collectAsState(); val favorites by vm.favoriteIds.collectAsState(); val history by vm.histories.collectAsState()
    var sort by rememberSaveable { mutableStateOf("Title") }; var ascending by rememberSaveable { mutableStateOf(true) }; var menu by remember { mutableStateOf(false) }
    val sortOptions = listOf("Title" to R.string.title, "Artist" to R.string.artist, "Album" to R.string.album, "Date added" to R.string.date_added, "Duration" to R.string.duration, "Year" to R.string.year, "Most played" to R.string.most_played, "Recently played" to R.string.recently_played)
    val stats = history.associateBy { it.songId }
    val comparator = when (sort) {
        "Artist" -> compareBy<SongEntity> { it.artist.lowercase() }
        "Album" -> compareBy { it.album.lowercase() }
        "Date added" -> compareBy { it.dateAdded }
        "Duration" -> compareBy { it.durationMs }
        "Year" -> compareBy { it.year }
        "Most played" -> compareBy { stats[it.id]?.playCount ?: 0 }
        "Recently played" -> compareBy { stats[it.id]?.lastPlayedAt ?: 0 }
        else -> compareBy { it.title.lowercase() }
    }
    val sorted = remember(songs, history, sort, ascending) { songs.sortedWith(if (ascending) comparator else comparator.reversed()) }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box { TextButton({ menu = true }) { Icon(Icons.Rounded.Sort, null); Text(stringResource(sortOptions.first { it.first == sort }.second)) }; DropdownMenu(menu, { menu = false }) { sortOptions.forEach { option -> DropdownMenuItem({ Text(stringResource(option.second)) }, { sort = option.first; menu = false }) } } }
            Spacer(Modifier.weight(1f)); IconButton({ ascending = !ascending }) { Icon(if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, stringResource(R.string.sort_direction)) }
        }
        if (sort == "Title" && ascending) {
            val paged = vm.songsPaged.collectAsLazyPagingItems()
            LazyColumn { items(paged.itemCount, key = { index -> paged[index]?.id ?: "loading-$index" }) { index -> paged[index]?.let { SongRow(it, favorites.contains(it.id), vm, songs) } } }
        } else {
            LazyColumn { items(sorted, key = { it.id }) { SongRow(it, favorites.contains(it.id), vm, sorted) } }
        }
    }
}

@Composable private fun AlbumList(vm: MainViewModel) {
    val albums by vm.albums.collectAsState(); val songs by vm.songs.collectAsState()
    var selected by remember { mutableStateOf<Long?>(null) }
    selected?.let { id -> val tracks = songs.filter { it.albumId == id }; FacetPage(tracks.firstOrNull()?.album.orEmpty(), tracks.firstOrNull()?.artist.orEmpty(), tracks, vm, "album" to id.toString()) { selected = null }; return }
    LazyColumn { items(albums, key = { it.albumId }) { album ->
        LibraryFacetRow(Icons.Rounded.Album, album.album, "${album.artist} • ${stringResource(R.string.song_count, album.songCount)}") {
            selected = album.albumId
        }
    } }
}

@Composable private fun ArtistList(vm: MainViewModel) {
    val artists by vm.artists.collectAsState(); val songs by vm.songs.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    selected?.let { artist -> FacetPage(artist, stringResource(R.string.song_count, songs.count { it.artist == artist }), songs.filter { it.artist == artist }, vm, "artist" to artist) { selected = null }; return }
    LazyColumn { items(artists, key = { it.artist }) { artist -> LibraryFacetRow(Icons.Rounded.Person, artist.artist, stringResource(R.string.album_song_count, artist.albumCount, artist.songCount)) { selected = artist.artist } } }
}

@Composable private fun GenreList(vm: MainViewModel) {
    val genres by vm.genres.collectAsState(); val songs by vm.songs.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    selected?.let { genre -> FacetPage(genre, stringResource(R.string.song_count, songs.count { it.genre == genre }), songs.filter { it.genre == genre }, vm, "genre" to genre) { selected = null }; return }
    LazyColumn { items(genres, key = { it.genre }) { genre -> LibraryFacetRow(Icons.Rounded.MusicNote, genre.genre, stringResource(R.string.song_count, genre.songCount)) { selected = genre.genre } } }
}

@Composable private fun FacetPage(title: String, subtitle: String, tracks: List<SongEntity>, vm: MainViewModel, favoriteKey: Pair<String, String>, close: () -> Unit) {
    val favorites by vm.favoriteIds.collectAsState()
    val favoriteCollections by vm.favoriteCollections.collectAsState()
    val playlists by vm.playlists.collectAsState(); var playlistMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }; Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton({ vm.toggleFavoriteCollection(favoriteKey.first, favoriteKey.second) }) { val liked = "${favoriteKey.first}:${favoriteKey.second}" in favoriteCollections; Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.favorite), tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ tracks.firstOrNull()?.let { vm.play(it, tracks) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text(stringResource(R.string.play)) }
            OutlinedButton({ tracks.shuffled().firstOrNull()?.let { vm.play(it, tracks.shuffled()) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Text(stringResource(R.string.shuffle)) }
            if (playlists.isNotEmpty()) Box { OutlinedButton({ playlistMenu = true }) { Text(stringResource(R.string.add_to_playlist)) }; DropdownMenu(playlistMenu, { playlistMenu = false }) { playlists.forEach { value -> DropdownMenuItem({ Text(value.title) }, { vm.addSongsToPlaylist(value.id, tracks.map { it.id }); playlistMenu = false }) } } }
        }
        LazyColumn { items(tracks, key = { it.id }) { SongRow(it, favorites.contains(it.id), vm, tracks) } }
    }
}

@Composable private fun FolderList(vm: MainViewModel) {
    val folders by vm.folders.collectAsState(); val songs by vm.songs.collectAsState()
    LazyColumn { items(folders, key = { it.relativePath }) { folder ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LibraryFacetRow(Icons.Rounded.Folder, folder.relativePath.ifBlank { stringResource(R.string.storage_root) }, stringResource(R.string.song_count, folder.songCount)) { songs.firstOrNull { it.relativePath == folder.relativePath }?.let { vm.play(it, songs.filter { s -> s.relativePath == folder.relativePath }) } } }
            IconButton({ vm.excludeFolder(folder.relativePath) }) { Icon(Icons.Rounded.Close, stringResource(R.string.exclude_folder)) }
        }
    } }
}

@Composable
private fun LibraryFacetRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        Icon(Icons.Rounded.NavigateNext, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CollectionList(vm: MainViewModel, playlistMode: Boolean) {
    val playlists by vm.playlists.collectAsState(); val categories by vm.categories.collectAsState()
    val favoriteCollections by vm.favoriteCollections.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pair<Long, String>?>(null) }
    selected?.let { value -> CollectionEditor(vm, value.first, value.second, playlistMode) { selected = null }; return }
    val values: List<Any> = if (playlistMode) playlists else categories
    Box(Modifier.fillMaxSize()) {
        LazyColumn { if (values.isEmpty()) item { HintCard(stringResource(if (playlistMode) R.string.no_playlists else R.string.no_categories)) }
            items(values, key = { if (it is PlaylistEntity) "p${it.id}" else "c${(it as CategoryEntity).id}" }) { value ->
                val title = if (value is PlaylistEntity) value.title else (value as CategoryEntity).title
                val id = if (value is PlaylistEntity) value.id else (value as CategoryEntity).id
                val artwork = if (value is PlaylistEntity) value.artworkUri else (value as CategoryEntity).artworkUri
                Row(Modifier.fillMaxWidth().clickable { selected = id to title }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (artwork != null) Artwork(artwork, Modifier.size(52.dp)) else Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(if (playlistMode) Icons.Rounded.QueueMusic else Icons.Rounded.Category, null, tint = MaterialTheme.colorScheme.primary) }
                    Text(title, Modifier.weight(1f).padding(14.dp), fontWeight = FontWeight.SemiBold)
                    IconButton({ vm.toggleFavoriteCollection(if (playlistMode) "playlist" else "category", id.toString()) }) { val liked = "${if (playlistMode) "playlist" else "category"}:$id" in favoriteCollections; Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.favorite), tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton({ if (playlistMode) vm.deletePlaylist(id) else vm.deleteCategory(id) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                }
            }
        }
        FloatingActionButton({ dialog = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Rounded.Add, stringResource(R.string.create)) }
    }
    if (dialog) NameDialog(stringResource(if (playlistMode) R.string.new_playlist else R.string.new_category), { dialog = false }, stringResource(R.string.create)) { if (playlistMode) vm.createPlaylist(it) else vm.createCategory(it); dialog = false }
}

@Composable
private fun CollectionEditor(vm: MainViewModel, id: Long, initialTitle: String, playlist: Boolean, close: () -> Unit) {
    val flow = remember(id, playlist) { if (playlist) vm.playlistSongs(id) else vm.categorySongs(id) }
    val tracks by flow.collectAsState(initial = emptyList())
    var rename by remember { mutableStateOf(false) }
    val playlists by vm.playlists.collectAsState(); var playlistMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; vm.setCollectionArtwork(id, it.toString(), playlist) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(initialTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton({ rename = true }) { Text(stringResource(R.string.rename)) }
            IconButton({ artworkPicker.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, stringResource(R.string.change_artwork)) }
            if (!playlist && playlists.isNotEmpty()) Box { TextButton({ playlistMenu = true }) { Text(stringResource(R.string.to_playlist)) }; DropdownMenu(playlistMenu, { playlistMenu = false }) { playlists.forEach { value -> DropdownMenuItem({ Text(value.title) }, { vm.addSongsToPlaylist(value.id, tracks.map { it.id }); playlistMenu = false }) } } }
        }
        if (tracks.isEmpty()) HintCard(stringResource(R.string.empty_collection))
        LazyColumn { itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
            Row(Modifier.fillMaxWidth().clickable { vm.play(song, tracks) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.artworkUri, Modifier.size(48.dp), sourceUri = song.uri, sizePx = 128)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(song.title, maxLines = 1); Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                IconButton({ if (index > 0) vm.reorderCollection(id, tracks.map { it.id }.toMutableList().apply { add(index - 1, removeAt(index)) }, playlist) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up)) }
                IconButton({ if (index < tracks.lastIndex) vm.reorderCollection(id, tracks.map { it.id }.toMutableList().apply { add(index + 1, removeAt(index)) }, playlist) }, enabled = index < tracks.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down)) }
                IconButton({ if (playlist) vm.removeFromPlaylist(id, song.id) else vm.removeFromCategory(id, song.id) }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove)) }
            }
        } }
    }
    if (rename) NameDialog(stringResource(R.string.rename), { rename = false }, stringResource(R.string.save), initialTitle) {
        if (playlist) vm.renamePlaylist(id, it) else vm.updateCategory(id, it, "")
        rename = false
    }
}

@Composable private fun NameDialog(title: String, dismiss: () -> Unit, confirmLabel: String, initialValue: String = "", save: (String) -> Unit) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true) },
        confirmButton = { TextButton({ save(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SongRow(song: SongEntity, favorite: Boolean, vm: MainViewModel, list: List<SongEntity>) {
    var menu by remember { mutableStateOf(false) }; var info by remember { mutableStateOf(false) }; var editMetadata by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; var addTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) vm.rescan() }
    val playlists by vm.playlists.collectAsState(); val categories by vm.categories.collectAsState()
    Row(Modifier.fillMaxWidth().clickable { vm.play(song, list) }.padding(start = 16.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(song.artworkUri, Modifier.size(54.dp), sourceUri = song.uri, sizePx = 160)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${song.artist} • ${formatDuration(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        IconButton({ vm.toggleFavorite(song.id) }) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.favorite), tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        Box { IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.play_next)) }, { vm.addNext(song); menu = false })
                DropdownMenuItem({ Text(stringResource(R.string.start_radio)) }, { vm.startRadio(song); menu = false })
                DropdownMenuItem({ Text(stringResource(R.string.add_to_queue)) }, { vm.addQueue(song); menu = false })
                if (playlists.isNotEmpty()) DropdownMenuItem({ Text(stringResource(R.string.add_to_playlist)) }, { addTarget = "playlist"; menu = false })
                if (categories.isNotEmpty()) DropdownMenuItem({ Text(stringResource(R.string.add_to_category)) }, { addTarget = "category"; menu = false })
                DropdownMenuItem({ Text(stringResource(R.string.song_information)) }, { info = true; menu = false })
                DropdownMenuItem({ Text(stringResource(R.string.edit_library_metadata)) }, { editMetadata = true; menu = false })
                DropdownMenuItem({ Text(stringResource(R.string.share_file)) }, {
                    val uri = Uri.parse(song.uri)
                    val share = Intent(Intent.ACTION_SEND).setType(song.mimeType.ifBlank { "audio/*" }).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(share, song.title)); menu = false
                })
                if (Build.VERSION.SDK_INT >= 30) DropdownMenuItem({ Text(stringResource(R.string.delete_from_device)) }, { confirmDelete = true; menu = false })
            }
        }
    }
    if (info) SongInfo(song) { info = false }
    if (editMetadata) MetadataEditor(song, { editMetadata = false }) { title, artist, album, genre, year -> vm.saveMetadata(song, title, artist, album, genre, year); editMetadata = false }
    addTarget?.let { target ->
        AlertDialog(onDismissRequest = { addTarget = null }, title = { Text(stringResource(if (target == "playlist") R.string.add_to_playlist else R.string.add_to_category)) }, text = { LazyColumn {
            if (target == "playlist") items(playlists, key = { it.id }) { value -> Text(value.title, Modifier.fillMaxWidth().clickable { vm.addToPlaylist(value.id, song.id); addTarget = null }.padding(14.dp)) }
            else items(categories, key = { it.id }) { value -> Text(value.title, Modifier.fillMaxWidth().clickable { vm.addToCategory(value.id, song.id); addTarget = null }.padding(14.dp)) }
        } }, confirmButton = {}, dismissButton = { TextButton({ addTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_song, song.title)) }, text = { Text(stringResource(R.string.delete_warning)) }, confirmButton = { TextButton({
        confirmDelete = false
        if (Build.VERSION.SDK_INT >= 30) runCatching { MediaStore.createDeleteRequest(context.contentResolver, listOf(Uri.parse(song.uri))).intentSender }.getOrNull()?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun MetadataEditor(song: SongEntity, dismiss: () -> Unit, save: (String, String, String, String, Int) -> Unit) {
    var title by remember { mutableStateOf(song.title) }; var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }; var genre by remember { mutableStateOf(song.genre) }; var year by remember { mutableStateOf(song.year.takeIf { it > 0 }?.toString().orEmpty()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.edit_metadata)) }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(stringResource(R.string.metadata_override_note), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        item { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.title)) }) }
        item { OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.artist)) }) }
        item { OutlinedTextField(album, { album = it }, label = { Text(stringResource(R.string.album)) }) }
        item { OutlinedTextField(genre, { genre = it }, label = { Text(stringResource(R.string.genre)) }) }
        item { OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.year)) }) }
    } }, confirmButton = { TextButton({ save(title, artist, album, genre, year.toIntOrNull() ?: 0) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SongInfo(song: SongEntity, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(song.title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(stringResource(R.string.artist) to song.artist, stringResource(R.string.album) to song.album, stringResource(R.string.genre) to song.genre.ifBlank { stringResource(R.string.unknown) }, stringResource(R.string.year) to song.year.takeIf { it > 0 }?.toString().orEmpty(), stringResource(R.string.duration) to formatDuration(song.durationMs), stringResource(R.string.bitrate) to song.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }.orEmpty(), stringResource(R.string.file_type) to song.mimeType, stringResource(R.string.file_size) to "${song.sizeBytes / 1_048_576} MB", stringResource(R.string.folder) to song.relativePath).forEach { (key, value) -> if (value.isNotBlank()) Text("$key: $value") }
    } }, confirmButton = { TextButton(dismiss) { Text(stringResource(R.string.done)) } })
}

private suspend fun embeddedArtwork(context: android.content.Context, sourceUri: String?): String? = withContext(Dispatchers.IO) {
    if (sourceUri.isNullOrBlank()) return@withContext null
    val file = File(context.cacheDir, "art_" + sourceUri.hashCode() + ".jpg")
    if (file.exists() && file.length() > 0L) return@withContext file.absolutePath
    val retriever = MediaMetadataRetriever()
    runCatching {
        retriever.setDataSource(context, Uri.parse(sourceUri))
        retriever.embeddedPicture?.let { bytes -> file.outputStream().use { it.write(bytes) }; file.absolutePath }
    }.getOrNull().also { retriever.release() }
}

@Composable
private fun Artwork(uri: String?, modifier: Modifier = Modifier, sourceUri: String? = null, sizePx: Int = 256) {
    val context = LocalContext.current
    var embedded by remember(sourceUri) { mutableStateOf<String?>(null) }
    LaunchedEffect(sourceUri, uri) { embedded = if (uri.isNullOrBlank()) embeddedArtwork(context, sourceUri) else null }
    val imageData = embedded ?: uri
    val model = remember(imageData, sizePx) {
        ImageRequest.Builder(context).data(imageData).size(sizePx).crossfade(false)
            .memoryCacheKey(imageData.orEmpty()).diskCacheKey(imageData.orEmpty())
            .diskCachePolicy(CachePolicy.ENABLED).build()
    }
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .65f), MaterialTheme.colorScheme.surfaceVariant))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun MiniPlayer(vm: MainViewModel, open: () -> Unit) {
    val state by vm.playback.collectAsState()
    val item = state.current ?: return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = open), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.fillMaxWidth().height(68.dp)) {
            Row(Modifier.fillMaxSize().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.padding(6.dp).size(56.dp), sourceUri = item.localConfiguration?.uri?.toString(), sizePx = 160)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(item.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                IconButton(vm::togglePlayback) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.play_pause)) }
                IconButton(vm::next) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next)) }
            }
            val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
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

@Composable private fun PlayerPanel(vm: MainViewModel, openQueue: () -> Unit, openLyrics: () -> Unit) {
    val state by vm.playback.collectAsState(); val item = state.current ?: return
    val favorites by vm.favoriteIds.collectAsState(); val id = item.mediaId.toLongOrNull()
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var customSleep by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(.15f))
        Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(onHorizontalDrag = { _, delta -> horizontalDrag += delta }, onDragEnd = { if (horizontalDrag < -80) vm.next() else if (horizontalDrag > 80) vm.previous(); horizontalDrag = 0f })
        }, sourceUri = item.localConfiguration?.uri?.toString(), sizePx = 1024)
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.mediaMetadata.title?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(item.mediaMetadata.artist?.toString().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) }
            id?.let { IconButton({ vm.toggleFavorite(it) }) { Icon(if (favorites.contains(it)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.favorite), tint = if (favorites.contains(it)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }
        }
        Spacer(Modifier.height(16.dp))
        Slider(state.positionMs.toFloat().coerceAtMost(state.durationMs.toFloat().coerceAtLeast(1f)), { vm.seek(it.toLong()) }, valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f))
        Row(Modifier.fillMaxWidth()) { Text(formatDuration(state.positionMs), fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))}", fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
            IconButton(vm::previous, Modifier.size(58.dp)) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(38.dp)) }
            FilledIconButton(vm::togglePlayback, Modifier.size(72.dp)) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.play_pause), Modifier.size(42.dp)) }
            IconButton(vm::next, Modifier.size(58.dp)) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(38.dp)) }
            IconButton(vm::cycleRepeat) { Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, stringResource(R.string.repeat), tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(openLyrics) { Icon(Icons.Rounded.Lyrics, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.lyrics)) }
            TextButton(openQueue) { Icon(Icons.Rounded.QueueMusic, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.queue)) }
        }
        Box {
            TextButton({ speedMenu = true }) { Icon(Icons.Rounded.Speed, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.playback_speed)) }
            DropdownMenu(speedMenu, { speedMenu = false }) { listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed -> DropdownMenuItem({ Text("${speed}×") }, { vm.setSpeed(speed); speedMenu = false }) } }
        }
        Box {
            TextButton({ sleepMenu = true }) { Text(stringResource(R.string.sleep_timer)) }
            DropdownMenu(sleepMenu, { sleepMenu = false }) {
                listOf(5, 10, 15, 30, 45, 60).forEach { minutes -> DropdownMenuItem({ Text(stringResource(R.string.minute_format, minutes)) }, { vm.setSleepTimer(minutes); sleepMenu = false }) }
                DropdownMenuItem({ Text(stringResource(R.string.custom)) }, { customSleep = true; sleepMenu = false })
                DropdownMenuItem({ Text(stringResource(R.string.end_of_song)) }, { vm.sleepAtEndOfSong(); sleepMenu = false })
                DropdownMenuItem({ Text(stringResource(R.string.end_of_queue)) }, { vm.sleepAtEndOfQueue(); sleepMenu = false })
                DropdownMenuItem({ Text(stringResource(R.string.cancel_timer)) }, { vm.cancelSleepTimer(); sleepMenu = false })
            }
        }
        Spacer(Modifier.weight(.15f))
    }
    if (customSleep) {
        var minutes by remember { mutableStateOf("90") }
        AlertDialog(onDismissRequest = { customSleep = false }, title = { Text(stringResource(R.string.sleep_timer)) }, text = { OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.minutes)) }) }, confirmButton = { TextButton({ minutes.toIntOrNull()?.takeIf { it > 0 }?.let(vm::setSleepTimer); customSleep = false }) { Text(stringResource(R.string.start)) } }, dismissButton = { TextButton({ customSleep = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable private fun QueuePanel(vm: MainViewModel) {
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

@Composable private fun LyricsPanel(vm: MainViewModel, songId: Long, position: Long) {
    val lyrics by vm.lyrics(songId).collectAsState(initial = null)
    LaunchedEffect(songId) { vm.loadSidecarLyrics(songId) }
    val settings by vm.settings.collectAsState()
    val ux = LocalNeoUxActions.current
    var editing by remember { mutableStateOf(false) }; var draft by remember(lyrics?.original) { mutableStateOf(lyrics?.original.orEmpty()) }
    var translation by remember(lyrics?.translation) { mutableStateOf(lyrics?.translation.orEmpty()) }
    var romanization by remember(lyrics?.romanization) { mutableStateOf(lyrics?.romanization.orEmpty()) }
    var syncLine by remember { mutableIntStateOf(0) }
    val lines = remember(lyrics?.original) { LrcParser.parse(lyrics?.original.orEmpty()) }
    val lyricsListState = rememberLazyListState()
    val lyricsScope = rememberCoroutineScope()
    val context = LocalContext.current
    val loading by vm.lyricsLoading.collectAsState()
    val providerError by vm.lyricsError.collectAsState()
    val importLrc = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selected ->
            runCatching { context.contentResolver.openInputStream(selected)?.bufferedReader()?.use { it.readText() } }
                .getOrNull()?.let { imported -> draft = imported; editing = true }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.lyrics), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium); TextButton({ ux.openTrackTools(TrackToolsSection.LYRICS_AI) }) { Text("AI") }; TextButton({ importLrc.launch("*/*") }) { Text(stringResource(R.string.import_lrc)) }; TextButton({ editing = !editing }) { Text(stringResource(if (editing) R.string.preview else R.string.edit)) } }
        if (editing) {
            OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().weight(1f), label = { Text(stringResource(R.string.original_lyrics_lrc)) })
            if (settings.romanizationEnabled) OutlinedTextField(romanization, { romanization = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text(stringResource(R.string.pronunciation_romanization)) }, maxLines = 3)
            if (settings.translationEnabled) OutlinedTextField(translation, { translation = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text(stringResource(R.string.translation)) }, maxLines = 3)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ draft = LrcParser.stampLine(draft, syncLine, position); syncLine++ }, Modifier.weight(1f), enabled = syncLine < draft.lines().size) { Text(stringResource(R.string.stamp_line, syncLine + 1)) }
                Button({ vm.saveLyricsLayers(songId, draft, translation, romanization); editing = false }, Modifier.weight(1f)) { Text(stringResource(R.string.save_lyrics)) }
            }
        } else if (lyrics == null || lyrics?.original.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Lyrics, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Text(stringResource(R.string.no_lyrics)); providerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }; if (vm.onlineLyricsAvailable && settings.lyricsMode != "offline") Button({ vm.fetchLyrics(songId) }, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.fetch_lyrics)) }; TextButton({ editing = true }) { Text(stringResource(R.string.add_lyrics)) } } }
        } else if (lines.isNotEmpty()) {
            val active = LrcParser.activeIndex(lines, position)
            LaunchedEffect(active, settings.lyricsAutoScroll) { if (settings.lyricsAutoScroll && active >= 0) lyricsListState.animateScrollToItem(active.coerceIn(0, lines.lastIndex)) }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = lyricsListState, modifier = Modifier.fillMaxSize().padding(bottom = 54.dp)) { itemsIndexed(lines) { index, line -> Column(Modifier.padding(vertical = 10.dp)) { Text(line.text.ifBlank { "♪" }, fontSize = if (index == active) (settings.lyricsFontSize + 4).sp else settings.lyricsFontSize.sp, fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal, color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); translation.lines().getOrNull(index)?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (settings.lyricsFontSize - 3).coerceAtLeast(12).sp) }; romanization.lines().getOrNull(index)?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f), fontSize = (settings.lyricsFontSize - 4).coerceAtLeast(11).sp) } } } }
                if (active >= 0) OutlinedButton({ lyricsScope.launch { lyricsListState.animateScrollToItem(active.coerceIn(0, lines.lastIndex)) } }, Modifier.align(Alignment.BottomCenter)) { Text(stringResource(R.string.current_line)) }
            }
        } else {
            LazyColumn { item { Text(lyrics?.original.orEmpty(), fontSize = 20.sp, lineHeight = 32.sp, modifier = Modifier.padding(vertical = 18.dp)) } }
        }
    }
}

@Composable private fun SettingsScreen(vm: MainViewModel, close: (() -> Unit)? = null) {
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

@Composable private fun SettingsTitle(text: String) = Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
@Composable private fun SettingsAction(icon: ImageVector, title: String, subtitle: String, action: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Column(Modifier.padding(start = 16.dp)) { Text(title); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ToggleRow(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().clickable { change(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, change) }
@Composable private fun EffectSlider(title: String, value: Int, max: Int, change: (Int) -> Unit) = Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) { Row { Text(title); Spacer(Modifier.weight(1f)); Text("${value * 100 / max}%") }; Slider(value.toFloat(), { change(it.toInt()) }, valueRange = 0f..max.toFloat()) }
@Composable private fun ChoiceRow(title: String, choices: List<String>, selected: Int, choose: (Int) -> Unit) { Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); LazyRow { items(choices.size) { index -> OutlinedButton({ choose(index) }, Modifier.padding(end = 8.dp)) { Text((if (index == selected) "✓ " else "") + choices[index]) } } } } }

@Composable private fun CustomColorDialog(dismiss: () -> Unit, save: (Int) -> Unit) {
    var hex by remember { mutableStateOf("FF7A1A") }; val valid = Regex("[0-9a-fA-F]{6}").matches(hex)
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.custom_accent)) }, text = { OutlinedTextField(hex, { hex = it.take(6) }, label = { Text(stringResource(R.string.hex_color)) }, prefix = { Text("#") }, singleLine = true) },
        confirmButton = { TextButton({ save((0xFF000000 or hex.toLong(16)).toInt()) }, enabled = valid) { Text(stringResource(R.string.apply)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun accentPreview(accent: Accent, custom: Int) = when (accent) { Accent.ORANGE -> Color(0xFFFF7A1A); Accent.GREEN -> Color(0xFF45D483); Accent.RED -> Color(0xFFFF5364); Accent.BLUE -> Color(0xFF5B8CFF); Accent.CUSTARD -> Color(0xFFE8C978); Accent.PURPLE -> Color(0xFFB586FF); Accent.CYAN -> Color(0xFF42D9E8); Accent.PINK -> Color(0xFFFF6FAE); Accent.INDIGO -> Color(0xFF7C83FF); Accent.TEAL -> Color(0xFF35C6A5); Accent.GOLD -> Color(0xFFFFB84D); Accent.CUSTOM -> Color(custom) }
private fun accentLabel(accent: Accent) = when (accent) { Accent.ORANGE -> R.string.orange; Accent.GREEN -> R.string.green; Accent.RED -> R.string.red; Accent.BLUE -> R.string.blue; Accent.CUSTARD -> R.string.custard; Accent.PURPLE -> R.string.purple; Accent.CYAN -> R.string.cyan; Accent.PINK -> R.string.pink; Accent.INDIGO -> R.string.indigo; Accent.TEAL -> R.string.teal; Accent.GOLD -> R.string.gold; Accent.CUSTOM -> R.string.custom }
private fun formatDuration(ms: Long): String { val total = ms.coerceAtLeast(0) / 1000; return String.format(Locale.US, "%d:%02d", total / 60, total % 60) }
