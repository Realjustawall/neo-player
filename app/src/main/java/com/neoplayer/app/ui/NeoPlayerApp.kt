@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neoplayer.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.neoplayer.app.BuildConfig
import com.neoplayer.app.R
import com.neoplayer.app.data.CategoryEntity
import com.neoplayer.app.data.PlaylistEntity
import com.neoplayer.app.data.SongEntity
import com.neoplayer.app.lyrics.LrcParser
import com.neoplayer.app.settings.Accent
import com.neoplayer.app.settings.ThemeMode
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Destination(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Rounded.Home), SEARCH(R.string.search, Icons.Rounded.Search),
    LIBRARY(R.string.library, Icons.Rounded.LibraryMusic), SETTINGS(R.string.settings, Icons.Rounded.Settings)
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
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimary)
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
    val playback by vm.playback.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(playback.error) { playback.error?.let { snackbar.showSnackbar("Playback error: $it") } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                AnimatedVisibility(playback.current != null) { MiniPlayer(vm) { fullPlayer = true } }
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, stringResource(item.label)) }, label = { Text(stringResource(item.label)) })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                Destination.HOME -> HomeScreen(vm)
                Destination.SEARCH -> SearchScreen(vm)
                Destination.LIBRARY -> LibraryScreen(vm)
                Destination.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
    AnimatedVisibility(fullPlayer, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        NowPlayingScreen(vm) { fullPlayer = false }
    }
    BackHandler(fullPlayer) { fullPlayer = false }
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
private fun HomeScreen(vm: MainViewModel) {
    val songs by vm.songs.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val histories by vm.histories.collectAsState()
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader(greeting, "Your music stays yours") }
        if (songs.isEmpty()) item { EmptyLibrary(vm) }
        else {
            item { SectionTitle(stringResource(R.string.recently_added)) }
            items(songs.sortedByDescending { it.dateAdded }.take(8), key = { "recent-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, songs) }
            item { SectionTitle(stringResource(R.string.liked_songs)) }
            val liked = songs.filter { favorites.contains(it.id) }.take(8)
            if (liked.isEmpty()) item { HintCard("Tap the heart on a song to build your favorites mix.") }
            items(liked, key = { "liked-${it.id}" }) { SongRow(it, true, vm, liked) }
            val playCounts = histories.associate { it.songId to it.playCount }
            val mostPlayed = songs.filter { playCounts.getOrDefault(it.id, 0) > 0 }.sortedByDescending { playCounts[it.id] }.take(8)
            val neverPlayed = songs.filterNot { playCounts.containsKey(it.id) }.take(8)
            item { SectionTitle(stringResource(R.string.smart_mixes)) }
            if (mostPlayed.isNotEmpty()) {
                item { HintCard(stringResource(R.string.most_played)) }
                items(mostPlayed, key = { "most-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, mostPlayed) }
            }
            if (neverPlayed.isNotEmpty()) {
                item { HintCard(stringResource(R.string.never_played)) }
                items(neverPlayed, key = { "never-${it.id}" }) { SongRow(it, favorites.contains(it.id), vm, neverPlayed) }
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
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.search))
        OutlinedTextField(query, { vm.query.value = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(stringResource(R.string.search_hint)) })
        LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp)) {
            if (query.isNotBlank() && results.isEmpty()) item { HintCard("No search results") }
            items(results, key = { it.id }) { SongRow(it, favorites.contains(it.id), vm, results) }
        }
    }
}

@Composable
private fun LibraryScreen(vm: MainViewModel) {
    val labels = listOf(R.string.songs, R.string.albums, R.string.artists, R.string.genres, R.string.folders, R.string.playlists, R.string.categories)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.library), action = { IconButton(vm::rescan) { Icon(Icons.Rounded.Refresh, stringResource(R.string.rescan)) } })
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
    val sorted = songs.sortedWith(if (ascending) comparator else comparator.reversed())
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box { TextButton({ menu = true }) { Icon(Icons.Rounded.Sort, null); Text(sort) }; DropdownMenu(menu, { menu = false }) { listOf("Title", "Artist", "Album", "Date added", "Duration", "Year", "Most played", "Recently played").forEach { option -> DropdownMenuItem({ Text(option) }, { sort = option; menu = false }) } } }
            Spacer(Modifier.weight(1f)); IconButton({ ascending = !ascending }) { Icon(if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, "Sort direction") }
        }
        LazyColumn { items(sorted, key = { it.id }) { SongRow(it, favorites.contains(it.id), vm, sorted) } }
    }
}

@Composable private fun AlbumList(vm: MainViewModel) {
    val albums by vm.albums.collectAsState(); val songs by vm.songs.collectAsState()
    var selected by remember { mutableStateOf<Long?>(null) }
    selected?.let { id -> val tracks = songs.filter { it.albumId == id }; FacetPage(tracks.firstOrNull()?.album.orEmpty(), tracks.firstOrNull()?.artist.orEmpty(), tracks, vm, "album" to id.toString()) { selected = null }; return }
    LazyColumn { items(albums, key = { it.albumId }) { album ->
        LibraryFacetRow(Icons.Rounded.Album, album.album, "${album.artist} • ${album.songCount} songs") {
            selected = album.albumId
        }
    } }
}

@Composable private fun ArtistList(vm: MainViewModel) {
    val artists by vm.artists.collectAsState(); val songs by vm.songs.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    selected?.let { artist -> FacetPage(artist, "${songs.count { it.artist == artist }} songs", songs.filter { it.artist == artist }, vm, "artist" to artist) { selected = null }; return }
    LazyColumn { items(artists, key = { it.artist }) { artist -> LibraryFacetRow(Icons.Rounded.Person, artist.artist, "${artist.albumCount} albums • ${artist.songCount} songs") { selected = artist.artist } } }
}

@Composable private fun GenreList(vm: MainViewModel) {
    val genres by vm.genres.collectAsState(); val songs by vm.songs.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    selected?.let { genre -> FacetPage(genre, "${songs.count { it.genre == genre }} songs", songs.filter { it.genre == genre }, vm, "genre" to genre) { selected = null }; return }
    LazyColumn { items(genres, key = { it.genre }) { genre -> LibraryFacetRow(Icons.Rounded.MusicNote, genre.genre, "${genre.songCount} songs") { selected = genre.genre } } }
}

@Composable private fun FacetPage(title: String, subtitle: String, tracks: List<SongEntity>, vm: MainViewModel, favoriteKey: Pair<String, String>, close: () -> Unit) {
    val favorites by vm.favoriteIds.collectAsState()
    val favoriteCollections by vm.favoriteCollections.collectAsState()
    val playlists by vm.playlists.collectAsState(); var playlistMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }; Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton({ vm.toggleFavoriteCollection(favoriteKey.first, favoriteKey.second) }) { val liked = "${favoriteKey.first}:${favoriteKey.second}" in favoriteCollections; Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ tracks.firstOrNull()?.let { vm.play(it, tracks) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
            OutlinedButton({ tracks.shuffled().firstOrNull()?.let { vm.play(it, tracks.shuffled()) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Text("Shuffle") }
            if (playlists.isNotEmpty()) Box { OutlinedButton({ playlistMenu = true }) { Text("Add to playlist") }; DropdownMenu(playlistMenu, { playlistMenu = false }) { playlists.forEach { value -> DropdownMenuItem({ Text(value.title) }, { vm.addSongsToPlaylist(value.id, tracks.map { it.id }); playlistMenu = false }) } } }
        }
        LazyColumn { items(tracks, key = { it.id }) { SongRow(it, favorites.contains(it.id), vm, tracks) } }
    }
}

@Composable private fun FolderList(vm: MainViewModel) {
    val folders by vm.folders.collectAsState(); val songs by vm.songs.collectAsState()
    LazyColumn { items(folders, key = { it.relativePath }) { folder ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LibraryFacetRow(Icons.Rounded.Folder, folder.relativePath.ifBlank { "Storage root" }, "${folder.songCount} songs") { songs.firstOrNull { it.relativePath == folder.relativePath }?.let { vm.play(it, songs.filter { s -> s.relativePath == folder.relativePath }) } } }
            IconButton({ vm.excludeFolder(folder.relativePath) }) { Icon(Icons.Rounded.Close, "Exclude folder") }
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
        LazyColumn { if (values.isEmpty()) item { HintCard(if (playlistMode) "No playlists yet" else "No categories yet") }
            items(values, key = { if (it is PlaylistEntity) "p${it.id}" else "c${(it as CategoryEntity).id}" }) { value ->
                val title = if (value is PlaylistEntity) value.title else (value as CategoryEntity).title
                val id = if (value is PlaylistEntity) value.id else (value as CategoryEntity).id
                val artwork = if (value is PlaylistEntity) value.artworkUri else (value as CategoryEntity).artworkUri
                Row(Modifier.fillMaxWidth().clickable { selected = id to title }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (artwork != null) Artwork(artwork, Modifier.size(52.dp)) else Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(if (playlistMode) Icons.Rounded.QueueMusic else Icons.Rounded.Category, null, tint = MaterialTheme.colorScheme.primary) }
                    Text(title, Modifier.weight(1f).padding(14.dp), fontWeight = FontWeight.SemiBold)
                    IconButton({ vm.toggleFavoriteCollection(if (playlistMode) "playlist" else "category", id.toString()) }) { val liked = "${if (playlistMode) "playlist" else "category"}:$id" in favoriteCollections; Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton({ if (playlistMode) vm.deletePlaylist(id) else vm.deleteCategory(id) }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        }
        FloatingActionButton({ dialog = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Rounded.Add, "Create") }
    }
    if (dialog) NameDialog(if (playlistMode) "New playlist" else "New category", { dialog = false }) { if (playlistMode) vm.createPlaylist(it) else vm.createCategory(it); dialog = false }
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
            IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(initialTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton({ rename = true }) { Text("Rename") }
            IconButton({ artworkPicker.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, "Change artwork") }
            if (!playlist && playlists.isNotEmpty()) Box { TextButton({ playlistMenu = true }) { Text("To playlist") }; DropdownMenu(playlistMenu, { playlistMenu = false }) { playlists.forEach { value -> DropdownMenuItem({ Text(value.title) }, { vm.addSongsToPlaylist(value.id, tracks.map { it.id }); playlistMenu = false }) } } }
        }
        if (tracks.isEmpty()) HintCard("Use a song menu to add music to this collection.")
        LazyColumn { itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
            Row(Modifier.fillMaxWidth().clickable { vm.play(song, tracks) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.artworkUri, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(song.title, maxLines = 1); Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                IconButton({ if (index > 0) vm.reorderCollection(id, tracks.map { it.id }.toMutableList().apply { add(index - 1, removeAt(index)) }, playlist) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                IconButton({ if (index < tracks.lastIndex) vm.reorderCollection(id, tracks.map { it.id }.toMutableList().apply { add(index + 1, removeAt(index)) }, playlist) }, enabled = index < tracks.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                IconButton({ if (playlist) vm.removeFromPlaylist(id, song.id) else vm.removeFromCategory(id, song.id) }) { Icon(Icons.Rounded.Close, "Remove") }
            }
        } }
    }
    if (rename) NameDialog("Rename", { rename = false }, "Save", initialTitle) {
        if (playlist) vm.renamePlaylist(id, it) else vm.updateCategory(id, it, "")
        rename = false
    }
}

@Composable private fun NameDialog(title: String, dismiss: () -> Unit, confirmLabel: String = "Create", initialValue: String = "", save: (String) -> Unit) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton({ save(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@Composable
private fun SongRow(song: SongEntity, favorite: Boolean, vm: MainViewModel, list: List<SongEntity>) {
    var menu by remember { mutableStateOf(false) }; var info by remember { mutableStateOf(false) }; var editMetadata by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; var addTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) vm.rescan() }
    val playlists by vm.playlists.collectAsState(); val categories by vm.categories.collectAsState()
    Row(Modifier.fillMaxWidth().clickable { vm.play(song, list) }.padding(start = 16.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(song.artworkUri, Modifier.size(54.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${song.artist} • ${formatDuration(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        IconButton({ vm.toggleFavorite(song.id) }) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        Box { IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Play next") }, { vm.addNext(song); menu = false })
                DropdownMenuItem({ Text("Add to queue") }, { vm.addQueue(song); menu = false })
                if (playlists.isNotEmpty()) DropdownMenuItem({ Text("Add to playlist") }, { addTarget = "playlist"; menu = false })
                if (categories.isNotEmpty()) DropdownMenuItem({ Text("Add to category") }, { addTarget = "category"; menu = false })
                DropdownMenuItem({ Text("Song information") }, { info = true; menu = false })
                DropdownMenuItem({ Text("Edit library metadata") }, { editMetadata = true; menu = false })
                DropdownMenuItem({ Text("Share file") }, {
                    val uri = Uri.parse(song.uri)
                    val share = Intent(Intent.ACTION_SEND).setType(song.mimeType.ifBlank { "audio/*" }).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(share, song.title)); menu = false
                })
                if (Build.VERSION.SDK_INT >= 30) DropdownMenuItem({ Text("Delete from device") }, { confirmDelete = true; menu = false })
            }
        }
    }
    if (info) SongInfo(song) { info = false }
    if (editMetadata) MetadataEditor(song, { editMetadata = false }) { title, artist, album, genre, year -> vm.saveMetadata(song, title, artist, album, genre, year); editMetadata = false }
    addTarget?.let { target ->
        AlertDialog(onDismissRequest = { addTarget = null }, title = { Text(if (target == "playlist") "Add to playlist" else "Add to category") }, text = { LazyColumn {
            if (target == "playlist") items(playlists, key = { it.id }) { value -> Text(value.title, Modifier.fillMaxWidth().clickable { vm.addToPlaylist(value.id, song.id); addTarget = null }.padding(14.dp)) }
            else items(categories, key = { it.id }) { value -> Text(value.title, Modifier.fillMaxWidth().clickable { vm.addToCategory(value.id, song.id); addTarget = null }.padding(14.dp)) }
        } }, confirmButton = {}, dismissButton = { TextButton({ addTarget = null }) { Text("Cancel") } })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${song.title}?") }, text = { Text("This permanently removes the audio file from the device. This cannot be undone.") }, confirmButton = { TextButton({
        confirmDelete = false
        if (Build.VERSION.SDK_INT >= 30) runCatching { MediaStore.createDeleteRequest(context.contentResolver, listOf(Uri.parse(song.uri))).intentSender }.getOrNull()?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@Composable private fun MetadataEditor(song: SongEntity, dismiss: () -> Unit, save: (String, String, String, String, Int) -> Unit) {
    var title by remember { mutableStateOf(song.title) }; var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }; var genre by remember { mutableStateOf(song.genre) }; var year by remember { mutableStateOf(song.year.takeIf { it > 0 }?.toString().orEmpty()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Edit metadata") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("These safe library overrides do not rewrite or risk corrupting the audio file.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        item { OutlinedTextField(title, { title = it }, label = { Text("Title") }) }
        item { OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }) }
        item { OutlinedTextField(album, { album = it }, label = { Text("Album") }) }
        item { OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }) }
        item { OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }) }
    } }, confirmButton = { TextButton({ save(title, artist, album, genre, year.toIntOrNull() ?: 0) }, enabled = title.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@Composable private fun SongInfo(song: SongEntity, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(song.title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("Artist" to song.artist, "Album" to song.album, "Genre" to song.genre.ifBlank { "Unknown" }, "Year" to song.year.takeIf { it > 0 }?.toString().orEmpty(), "Duration" to formatDuration(song.durationMs), "Bitrate" to song.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }.orEmpty(), "Type" to song.mimeType, "Size" to "${song.sizeBytes / 1_048_576} MB", "Folder" to song.relativePath).forEach { (key, value) -> if (value.isNotBlank()) Text("$key: $value") }
    } }, confirmButton = { TextButton(dismiss) { Text("Done") } })
}

@Composable
private fun Artwork(uri: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .65f), MaterialTheme.colorScheme.surfaceVariant))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
        AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun MiniPlayer(vm: MainViewModel, open: () -> Unit) {
    val state by vm.playback.collectAsState()
    val item = state.current ?: return
    Surface(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = open), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column { Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.size(64.dp).padding(6.dp))
            Column(Modifier.weight(1f)) { Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(item.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            IconButton(vm::togglePlayback) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause") }
            IconButton(vm::next) { Icon(Icons.Rounded.SkipNext, "Next") }
        }
            val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun NowPlayingScreen(vm: MainViewModel, close: () -> Unit) {
    val state by vm.playback.collectAsState(); val item = state.current ?: return
    var panel by remember { mutableStateOf("player") }; var verticalDrag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.playing) { while (state.playing) { delay(500); vm.refreshPosition() } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues())) {
            Row(Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures(onVerticalDrag = { _, delta -> verticalDrag += delta }, onDragEnd = { if (verticalDrag > 100) close(); verticalDrag = 0f }) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Minimize") }
                Text(stringResource(R.string.now_playing), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton({ panel = if (panel == "queue") "player" else "queue" }) { Icon(Icons.Rounded.QueueMusic, stringResource(R.string.queue)) }
                IconButton({ panel = if (panel == "lyrics") "player" else "lyrics" }) { Icon(Icons.Rounded.Lyrics, stringResource(R.string.lyrics)) }
            }
            when (panel) {
                "queue" -> QueuePanel(vm)
                "lyrics" -> LyricsPanel(vm, item.mediaId.toLongOrNull() ?: -1, state.positionMs)
                else -> PlayerPanel(vm)
            }
        }
    }
}

@Composable private fun PlayerPanel(vm: MainViewModel) {
    val state by vm.playback.collectAsState(); val item = state.current ?: return
    val favorites by vm.favoriteIds.collectAsState(); val id = item.mediaId.toLongOrNull()
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(.15f))
        Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
            detectHorizontalDragGestures(onHorizontalDrag = { _, delta -> horizontalDrag += delta }, onDragEnd = { if (horizontalDrag < -80) vm.next() else if (horizontalDrag > 80) vm.previous(); horizontalDrag = 0f })
        })
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.mediaMetadata.title?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(item.mediaMetadata.artist?.toString().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) }
            id?.let { IconButton({ vm.toggleFavorite(it) }) { Icon(if (favorites.contains(it)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (favorites.contains(it)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }
        }
        Spacer(Modifier.height(16.dp))
        Slider(state.positionMs.toFloat().coerceAtMost(state.durationMs.toFloat().coerceAtLeast(1f)), { vm.seek(it.toLong()) }, valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f))
        Row(Modifier.fillMaxWidth()) { Text(formatDuration(state.positionMs), fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))}", fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
            IconButton(vm::previous, Modifier.size(58.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(38.dp)) }
            FilledIconButton(vm::togglePlayback, Modifier.size(72.dp)) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", Modifier.size(42.dp)) }
            IconButton(vm::next, Modifier.size(58.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(38.dp)) }
            IconButton(vm::cycleRepeat) { Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        }
        Box {
            TextButton({ speedMenu = true }) { Icon(Icons.Rounded.Speed, null); Spacer(Modifier.width(6.dp)); Text("Playback speed") }
            DropdownMenu(speedMenu, { speedMenu = false }) { listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed -> DropdownMenuItem({ Text("${speed}×") }, { vm.setSpeed(speed); speedMenu = false }) } }
        }
        Box {
            TextButton({ sleepMenu = true }) { Text(stringResource(R.string.sleep_timer)) }
            DropdownMenu(sleepMenu, { sleepMenu = false }) {
                listOf(5, 10, 15, 30, 45, 60).forEach { minutes -> DropdownMenuItem({ Text("$minutes min") }, { vm.setSleepTimer(minutes); sleepMenu = false }) }
                DropdownMenuItem({ Text("End of song") }, { vm.sleepAtEndOfSong(); sleepMenu = false })
                DropdownMenuItem({ Text("End of queue") }, { vm.sleepAtEndOfQueue(); sleepMenu = false })
                DropdownMenuItem({ Text("Cancel timer") }, { vm.cancelSleepTimer(); sleepMenu = false })
            }
        }
        Spacer(Modifier.weight(.15f))
    }
}

@Composable private fun QueuePanel(vm: MainViewModel) {
    val state by vm.playback.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.queue), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium); TextButton(vm::clearQueue) { Text("Clear") } }
        LazyColumn { itemsIndexed(state.queue, key = { index, item -> "$index-${item.mediaId}" }) { index, item ->
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(item.mediaMetadata.artworkUri?.toString(), Modifier.size(48.dp)); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1); Text(item.mediaMetadata.artist?.toString().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                IconButton({ vm.moveQueueItem(index, index - 1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                IconButton({ vm.moveQueueItem(index, index + 1) }, enabled = index < state.queue.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                IconButton({ vm.removeQueueItem(index) }) { Icon(Icons.Rounded.Close, "Remove") }
            }
        } }
    }
}

@Composable private fun LyricsPanel(vm: MainViewModel, songId: Long, position: Long) {
    val lyrics by vm.lyrics(songId).collectAsState(initial = null)
    val settings by vm.settings.collectAsState()
    var editing by remember { mutableStateOf(false) }; var draft by remember(lyrics?.original) { mutableStateOf(lyrics?.original.orEmpty()) }
    var translation by remember(lyrics?.translation) { mutableStateOf(lyrics?.translation.orEmpty()) }
    var romanization by remember(lyrics?.romanization) { mutableStateOf(lyrics?.romanization.orEmpty()) }
    var syncLine by remember { mutableIntStateOf(0) }
    val lines = remember(lyrics?.original) { LrcParser.parse(lyrics?.original.orEmpty()) }
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
        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.lyrics), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium); TextButton({ importLrc.launch("*/*") }) { Text(stringResource(R.string.import_lrc)) }; TextButton({ editing = !editing }) { Text(if (editing) "Preview" else "Edit") } }
        if (editing) {
            OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Original lyrics or LRC") })
            if (settings.romanizationEnabled) OutlinedTextField(romanization, { romanization = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Pronunciation / romanization") }, maxLines = 3)
            if (settings.translationEnabled) OutlinedTextField(translation, { translation = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Translation") }, maxLines = 3)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ draft = LrcParser.stampLine(draft, syncLine, position); syncLine++ }, Modifier.weight(1f), enabled = syncLine < draft.lines().size) { Text("Stamp line ${syncLine + 1}") }
                Button({ vm.saveLyricsLayers(songId, draft, translation, romanization); editing = false }, Modifier.weight(1f)) { Text("Save lyrics") }
            }
        } else if (lyrics == null || lyrics?.original.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Lyrics, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Text("No lyrics found"); providerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }; if (vm.onlineLyricsAvailable && settings.lyricsMode != "offline") Button({ vm.fetchLyrics(songId) }, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Fetch lyrics") }; TextButton({ editing = true }) { Text("Add lyrics") } } }
        } else if (lines.isNotEmpty()) {
            val active = LrcParser.activeIndex(lines, position)
            LazyColumn(Modifier.fillMaxSize()) { itemsIndexed(lines) { index, line -> Column(Modifier.padding(vertical = 10.dp)) { Text(line.text.ifBlank { "♪" }, fontSize = if (index == active) (settings.lyricsFontSize + 4).sp else settings.lyricsFontSize.sp, fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal, color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); translation.lines().getOrNull(index)?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (settings.lyricsFontSize - 3).coerceAtLeast(12).sp) }; romanization.lines().getOrNull(index)?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f), fontSize = (settings.lyricsFontSize - 4).coerceAtLeast(11).sp) } } } }
        } else {
            LazyColumn { item { Text(lyrics?.original.orEmpty(), fontSize = 20.sp, lineHeight = 32.sp, modifier = Modifier.padding(vertical = 18.dp)) } }
        }
    }
}

@Composable private fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState(); var customDialog by remember { mutableStateOf(false) }
    val excluded by vm.excludedFolders.collectAsState()
    val audioEffects by vm.audioEffects.collectAsState()
    val context = LocalContext.current
    val equalizerIntent = remember { Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply { putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader(stringResource(R.string.settings), "Offline by design") }
        item { SettingsTitle("Appearance") }
        item { ChoiceRow("Theme mode", ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }, settings.themeMode.ordinal) { vm.setTheme(ThemeMode.entries[it]) } }
        item { Text("Accent color", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold) }
        item { LazyRow(Modifier.padding(horizontal = 14.dp)) { items(Accent.entries) { accent ->
            val color = accentPreview(accent, settings.customColor)
            Column(Modifier.clickable { if (accent == Accent.CUSTOM) customDialog = true else vm.setAccent(accent) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { if (settings.accent == accent) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold) }
                Text(accent.name.lowercase().replaceFirstChar(Char::uppercase), fontSize = 11.sp)
            }
        } } }
        item { SettingsTitle("Language") }
        item { ChoiceRow("App language", listOf("System", "English", "فارسی"), when (settings.language) { "en" -> 1; "fa" -> 2; else -> 0 }) { index ->
            val tag = listOf("system", "en", "fa")[index]; vm.setLanguage(tag)
            AppCompatDelegate.setApplicationLocales(if (tag == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
        } }
        item { SettingsTitle("Library") }
        item { SettingsAction(Icons.Rounded.Refresh, stringResource(R.string.rescan), "Refresh changed, added, and removed files", vm::rescan) }
        item { ChoiceRow("Minimum audio duration", listOf("0s", "10s", "30s", "60s"), listOf(0L, 10_000L, 30_000L, 60_000L).indexOf(settings.minDurationMs).coerceAtLeast(0)) { vm.setMinDuration(listOf(0L, 10_000L, 30_000L, 60_000L)[it]) } }
        if (excluded.isNotEmpty()) item { Column { Text("Excluded folders", Modifier.padding(horizontal = 20.dp)); excluded.forEach { path -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(path, Modifier.weight(1f)); TextButton({ vm.includeFolder(path) }) { Text("Include") } } } } }
        item { SettingsTitle("Playback") }
        item { ToggleRow("Gapless playback", "Media3 gapless transitions for compatible files", settings.gapless, vm::setGapless) }
        item { SettingsTitle("Audio") }
        if (audioEffects.available) {
            item { ChoiceRow(stringResource(R.string.equalizer), vm.audioPresets, vm.audioPresets.indexOf(audioEffects.preset).coerceAtLeast(0), vm::setAudioPreset) }
            item { EffectSlider("Bass boost", audioEffects.bass, 1000, vm::setBass) }
            item { EffectSlider("Virtualizer", audioEffects.virtualizer, 1000, vm::setVirtualizer) }
            item { EffectSlider("Loudness", audioEffects.loudnessMb, 1200, vm::setLoudness) }
            if (audioEffects.preset == "Custom") itemsIndexed(audioEffects.bandLevels) { index, level -> EffectSlider("Band ${index + 1}", level.toInt() + 1500, 3000) { vm.setEqualizerBand(index, (it - 1500).toShort()) } }
        } else if (equalizerIntent.resolveActivity(context.packageManager) != null) item { SettingsAction(Icons.Rounded.MusicNote, stringResource(R.string.equalizer), "Play a song to attach NEO effects, or open the device panel", { context.startActivity(equalizerIntent) }) }
        item { SettingsTitle("Lyrics") }
        item { val modes = if (vm.onlineLyricsAvailable) listOf("Auto", "Offline only", "Online only") else listOf("Auto", "Offline only"); ChoiceRow("Lyrics source", modes, if (settings.lyricsMode == "offline") 1 else if (settings.lyricsMode == "online" && vm.onlineLyricsAvailable) 2 else 0) { vm.setLyricsMode(if (it == 1) "offline" else if (it == 2) "online" else "auto") } }
        item { ChoiceRow("Lyrics size", listOf("16", "20", "24", "28"), listOf(16, 20, 24, 28).indexOf(settings.lyricsFontSize).coerceAtLeast(1)) { vm.setLyricsFontSize(listOf(16, 20, 24, 28)[it]) } }
        item { SettingsTitle("Privacy") }
        item { HintCard("No account, tracking, analytics, or network catalog. Listening data stays on this device.") }
        item { SettingsTitle(stringResource(R.string.about)) }
        item { Column(Modifier.padding(20.dp)) { Text("NEO PLAYER", style = MaterialTheme.typography.titleLarge); Text(BuildConfig.DISPLAY_VERSION + " • " + BuildConfig.VERSION_NAME); Spacer(Modifier.height(8.dp)); Text("A modern local-first music player."); Text(stringResource(R.string.made_by), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text("Build ${BuildConfig.VERSION_CODE}\nLicense: Apache-2.0\nLibraries: Jetpack Compose, Media3, Room, DataStore, Coil") } }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (customDialog) CustomColorDialog({ customDialog = false }) { vm.setCustomColor(it); customDialog = false }
}

@Composable private fun SettingsTitle(text: String) = Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
@Composable private fun SettingsAction(icon: ImageVector, title: String, subtitle: String, action: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Column(Modifier.padding(start = 16.dp)) { Text(title); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ToggleRow(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().clickable { change(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, change) }
@Composable private fun EffectSlider(title: String, value: Int, max: Int, change: (Int) -> Unit) = Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) { Row { Text(title); Spacer(Modifier.weight(1f)); Text("${value * 100 / max}%") }; Slider(value.toFloat(), { change(it.toInt()) }, valueRange = 0f..max.toFloat()) }
@Composable private fun ChoiceRow(title: String, choices: List<String>, selected: Int, choose: (Int) -> Unit) { Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); LazyRow { items(choices.size) { index -> OutlinedButton({ choose(index) }, Modifier.padding(end = 8.dp)) { Text((if (index == selected) "✓ " else "") + choices[index]) } } } } }

@Composable private fun CustomColorDialog(dismiss: () -> Unit, save: (Int) -> Unit) {
    var hex by remember { mutableStateOf("FF7A1A") }; val valid = Regex("[0-9a-fA-F]{6}").matches(hex)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Custom accent") }, text = { OutlinedTextField(hex, { hex = it.take(6) }, label = { Text("Hex color") }, prefix = { Text("#") }, singleLine = true) },
        confirmButton = { TextButton({ save((0xFF000000 or hex.toLong(16)).toInt()) }, enabled = valid) { Text("Apply") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

private fun accentPreview(accent: Accent, custom: Int) = when (accent) { Accent.ORANGE -> Color(0xFFFF7A1A); Accent.GREEN -> Color(0xFF45D483); Accent.RED -> Color(0xFFFF5364); Accent.BLUE -> Color(0xFF5B8CFF); Accent.CUSTARD -> Color(0xFFE8C978); Accent.CUSTOM -> Color(custom) }
private fun formatDuration(ms: Long): String { val total = ms.coerceAtLeast(0) / 1000; return String.format(Locale.US, "%d:%02d", total / 60, total % 60) }
