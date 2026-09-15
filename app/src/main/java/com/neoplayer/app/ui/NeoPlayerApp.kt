@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neoplayer.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
    Scaffold(
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
    val songs by vm.songs.collectAsState(); val favorites by vm.favoriteIds.collectAsState()
    LazyColumn { items(songs, key = { it.id }) { SongRow(it, favorites.contains(it.id), vm, songs) } }
}

@Composable private fun AlbumList(vm: MainViewModel) {
    val albums by vm.albums.collectAsState(); val songs by vm.songs.collectAsState()
    LazyColumn { items(albums, key = { it.albumId }) { album ->
        LibraryFacetRow(Icons.Rounded.Album, album.album, "${album.artist} • ${album.songCount} songs") {
            songs.firstOrNull { it.albumId == album.albumId }?.let { vm.play(it, songs.filter { song -> song.albumId == album.albumId }) }
        }
    } }
}

@Composable private fun ArtistList(vm: MainViewModel) {
    val artists by vm.artists.collectAsState(); val songs by vm.songs.collectAsState()
    LazyColumn { items(artists, key = { it.artist }) { artist -> LibraryFacetRow(Icons.Rounded.Person, artist.artist, "${artist.albumCount} albums • ${artist.songCount} songs") { songs.firstOrNull { it.artist == artist.artist }?.let { vm.play(it, songs.filter { s -> s.artist == artist.artist }) } } } }
}

@Composable private fun GenreList(vm: MainViewModel) {
    val genres by vm.genres.collectAsState(); val songs by vm.songs.collectAsState()
    LazyColumn { items(genres, key = { it.genre }) { genre -> LibraryFacetRow(Icons.Rounded.MusicNote, genre.genre, "${genre.songCount} songs") { songs.firstOrNull { it.genre == genre.genre }?.let { vm.play(it, songs.filter { s -> s.genre == genre.genre }) } } } }
}

@Composable private fun FolderList(vm: MainViewModel) {
    val folders by vm.folders.collectAsState(); val songs by vm.songs.collectAsState()
    LazyColumn { items(folders, key = { it.relativePath }) { folder -> LibraryFacetRow(Icons.Rounded.Folder, folder.relativePath.ifBlank { "Storage root" }, "${folder.songCount} songs") { songs.firstOrNull { it.relativePath == folder.relativePath }?.let { vm.play(it, songs.filter { s -> s.relativePath == folder.relativePath }) } } } }
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
    var dialog by remember { mutableStateOf(false) }
    val values: List<Any> = if (playlistMode) playlists else categories
    Box(Modifier.fillMaxSize()) {
        LazyColumn { if (values.isEmpty()) item { HintCard(if (playlistMode) "No playlists yet" else "No categories yet") }
            items(values, key = { if (it is PlaylistEntity) "p${it.id}" else "c${(it as CategoryEntity).id}" }) { value ->
                val title = if (value is PlaylistEntity) value.title else (value as CategoryEntity).title
                val id = if (value is PlaylistEntity) value.id else (value as CategoryEntity).id
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(if (playlistMode) Icons.Rounded.QueueMusic else Icons.Rounded.Category, null, tint = MaterialTheme.colorScheme.primary) }
                    Text(title, Modifier.weight(1f).padding(14.dp), fontWeight = FontWeight.SemiBold)
                    IconButton({ if (playlistMode) vm.deletePlaylist(id) else vm.deleteCategory(id) }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        }
        FloatingActionButton({ dialog = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Rounded.Add, "Create") }
    }
    if (dialog) NameDialog(if (playlistMode) "New playlist" else "New category", { dialog = false }) { if (playlistMode) vm.createPlaylist(it) else vm.createCategory(it); dialog = false }
}

@Composable private fun NameDialog(title: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton({ save(value) }, enabled = value.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@Composable
private fun SongRow(song: SongEntity, favorite: Boolean, vm: MainViewModel, list: List<SongEntity>) {
    var menu by remember { mutableStateOf(false) }; var info by remember { mutableStateOf(false) }
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
                if (playlists.isNotEmpty()) DropdownMenuItem({ Text("Add to ${playlists.first().title}") }, { vm.addToPlaylist(playlists.first().id, song.id); menu = false })
                if (categories.isNotEmpty()) DropdownMenuItem({ Text("Add to ${categories.first().title}") }, { vm.addToCategory(categories.first().id, song.id); menu = false })
                DropdownMenuItem({ Text("Song information") }, { info = true; menu = false })
            }
        }
    }
    if (info) SongInfo(song) { info = false }
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
                IconButton({ vm.removeQueueItem(index) }) { Icon(Icons.Rounded.Close, "Remove") }
            }
        } }
    }
}

@Composable private fun LyricsPanel(vm: MainViewModel, songId: Long, position: Long) {
    val lyrics by vm.lyrics(songId).collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }; var draft by remember(lyrics?.original) { mutableStateOf(lyrics?.original.orEmpty()) }
    val lines = remember(lyrics?.original) { LrcParser.parse(lyrics?.original.orEmpty()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.lyrics), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium); TextButton({ editing = !editing }) { Text(if (editing) "Preview" else "Edit") } }
        if (editing) {
            OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Paste text or LRC") })
            Button({ vm.saveLyrics(songId, draft); editing = false }, Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Save lyrics") }
        } else if (lyrics == null || lyrics?.original.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Lyrics, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Text("No lyrics found"); TextButton({ editing = true }) { Text("Add lyrics") } } }
        } else if (lines.isNotEmpty()) {
            val active = LrcParser.activeIndex(lines, position)
            LazyColumn(Modifier.fillMaxSize()) { itemsIndexed(lines) { index, line -> Text(line.text.ifBlank { "♪" }, Modifier.padding(vertical = 12.dp), fontSize = if (index == active) 24.sp else 19.sp, fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal, color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
            LazyColumn { item { Text(lyrics?.original.orEmpty(), fontSize = 20.sp, lineHeight = 32.sp, modifier = Modifier.padding(vertical = 18.dp)) } }
        }
    }
}

@Composable private fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState(); var customDialog by remember { mutableStateOf(false) }
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
@Composable private fun ChoiceRow(title: String, choices: List<String>, selected: Int, choose: (Int) -> Unit) { Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); LazyRow { items(choices.size) { index -> OutlinedButton({ choose(index) }, Modifier.padding(end = 8.dp)) { Text((if (index == selected) "✓ " else "") + choices[index]) } } } } }

@Composable private fun CustomColorDialog(dismiss: () -> Unit, save: (Int) -> Unit) {
    var hex by remember { mutableStateOf("FF7A1A") }; val valid = Regex("[0-9a-fA-F]{6}").matches(hex)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Custom accent") }, text = { OutlinedTextField(hex, { hex = it.take(6) }, label = { Text("Hex color") }, prefix = { Text("#") }, singleLine = true) },
        confirmButton = { TextButton({ save((0xFF000000 or hex.toLong(16)).toInt()) }, enabled = valid) { Text("Apply") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

private fun accentPreview(accent: Accent, custom: Int) = when (accent) { Accent.ORANGE -> Color(0xFFFF7A1A); Accent.GREEN -> Color(0xFF45D483); Accent.RED -> Color(0xFFFF5364); Accent.BLUE -> Color(0xFF5B8CFF); Accent.CUSTARD -> Color(0xFFE8C978); Accent.CUSTOM -> Color(custom) }
private fun formatDuration(ms: Long): String { val total = ms.coerceAtLeast(0) / 1000; return String.format(Locale.US, "%d:%02d", total / 60, total % 60) }
