package com.neoplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoplayer.app.data.PlaylistFolderEntity
import com.neoplayer.app.data.SongEntity
import java.util.Locale

/**
 * Final additive shell around both the untouched classic UI and Neo+.
 * Collection organization lives in its own overlay to avoid replacing any original navigation.
 */
@Composable
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

private data class AlbumCollection(val albumId: Long, val title: String, val artist: String, val tracks: List<SongEntity>)

@Composable
private fun AlbumOrganizer(vm: NeoPlusViewModel, fa: Boolean) {
    val rawSongs by vm.songs.collectAsState()
    val hidden by vm.hiddenGlobalIds.collectAsState()
    val pins by vm.pinnedCollections.collectAsState()
    val settings by vm.settings.collectAsState()
    val albums = remember(rawSongs, hidden) {
        rawSongs.asSequence()
            .filterNot { it.id in hidden }
            .groupBy { it.albumId }
            .map { (id, tracks) -> AlbumCollection(id, tracks.firstOrNull()?.album.orEmpty(), tracks.firstOrNull()?.artist.orEmpty(), tracks) }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }
    val grid = settings.libraryViewMode == "grid"

    Column(Modifier.fillMaxSize()) {
        ViewModeHeader(vm, fa, albums.size)
        if (grid) {
            LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize()) {
                gridItems(albums, key = { it.albumId }) { album ->
                    val pinned = pins.any { it.type == "album" && it.key == album.albumId.toString() }
                    CollectionCard(
                        icon = { Icon(Icons.Rounded.Album, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = album.title,
                        subtitle = "${album.artist} • ${album.tracks.size}",
                        pinned = pinned,
                        pin = { vm.togglePin("album", album.albumId.toString()) },
                        play = { album.tracks.firstOrNull()?.let { vm.play(it, album.tracks) } },
                        fa = fa
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(albums, key = { it.albumId }) { album ->
                    val pinned = pins.any { it.type == "album" && it.key == album.albumId.toString() }
                    CollectionRow(
                        icon = { Icon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.primary) },
                        title = album.title,
                        subtitle = "${album.artist} • ${album.tracks.size}",
                        pinned = pinned,
                        pin = { vm.togglePin("album", album.albumId.toString()) },
                        play = { album.tracks.firstOrNull()?.let { vm.play(it, album.tracks) } },
                        fa = fa
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistOrganizer(vm: NeoPlusViewModel, fa: Boolean) {
    val rawSongs by vm.songs.collectAsState()
    val hidden by vm.hiddenGlobalIds.collectAsState()
    val pins by vm.pinnedCollections.collectAsState()
    val settings by vm.settings.collectAsState()
    val artists = remember(rawSongs, hidden) {
        rawSongs.asSequence()
            .filterNot { it.id in hidden }
            .groupBy { it.artist }
            .entries
            .sortedBy { it.key.lowercase(Locale.ROOT) }
    }
    val grid = settings.libraryViewMode == "grid"

    Column(Modifier.fillMaxSize()) {
        ViewModeHeader(vm, fa, artists.size)
        if (grid) {
            LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize()) {
                gridItems(artists, key = { it.key }) { artist ->
                    val pinned = pins.any { it.type == "artist" && it.key == artist.key }
                    CollectionCard(
                        icon = { Icon(Icons.Rounded.Person, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = artist.key,
                        subtitle = tx(fa, "${artist.value.size} tracks", "${artist.value.size} آهنگ"),
                        pinned = pinned,
                        pin = { vm.togglePin("artist", artist.key) },
                        play = { artist.value.firstOrNull()?.let { vm.play(it, artist.value) } },
                        fa = fa
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(artists, key = { it.key }) { artist ->
                    val pinned = pins.any { it.type == "artist" && it.key == artist.key }
                    CollectionRow(
                        icon = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
                        title = artist.key,
                        subtitle = tx(fa, "${artist.value.size} tracks", "${artist.value.size} آهنگ"),
                        pinned = pinned,
                        pin = { vm.togglePin("artist", artist.key) },
                        play = { artist.value.firstOrNull()?.let { vm.play(it, artist.value) } },
                        fa = fa
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeHeader(vm: NeoPlusViewModel, fa: Boolean, count: Int) {
    val settings by vm.settings.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tx(fa, "$count collections", "$count مجموعه"), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(selected = settings.libraryViewMode == "list", onClick = { vm.setLibraryViewMode("list") }, label = { Text(tx(fa, "List", "لیست")) })
        Spacer(Modifier.width(6.dp))
        FilterChip(selected = settings.libraryViewMode == "grid", onClick = { vm.setLibraryViewMode("grid") }, label = { Text(tx(fa, "Grid", "شبکه‌ای")) })
    }
}

@Composable
private fun CollectionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    pinned: Boolean,
    pin: () -> Unit,
    play: () -> Unit,
    fa: Boolean
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = play).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { tx(fa, "Unknown", "ناشناخته") }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        IconButton(pin) { Icon(if (pinned) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tx(fa, "Pin", "سنجاق")) }
    }
}

@Composable
private fun CollectionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    pinned: Boolean,
    pin: () -> Unit,
    play: () -> Unit,
    fa: Boolean
) {
    Card(Modifier.padding(6.dp).clickable(onClick = play)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.weight(1f))
                IconButton(pin) { Icon(if (pinned) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tx(fa, "Pin", "سنجاق")) }
            }
            Text(title.ifBlank { tx(fa, "Unknown", "ناشناخته") }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NestedFolderOrganizer(vm: NeoPlusViewModel, fa: Boolean) {
    val folders by vm.playlistFolders.collectAsState()
    val playlists by vm.playlists.collectAsState()
    var title by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<Long?>(null) }
    var parentMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(tx(fa, "Folder name", "نام پوشه")) }
            )
            Spacer(Modifier.width(6.dp))
            Box {
                TextButton({ parentMenu = true }) {
                    Icon(Icons.Rounded.Folder, null)
                    Spacer(Modifier.width(4.dp))
                    Text(folders.firstOrNull { it.id == parentId }?.title ?: tx(fa, "Root", "ریشه"))
                }
                DropdownMenu(parentMenu, { parentMenu = false }) {
                    DropdownMenuItem(text = { Text(tx(fa, "Root", "ریشه")) }, onClick = { parentId = null; parentMenu = false })
                    folders.forEach { folder ->
                        DropdownMenuItem(text = { Text(folder.title) }, onClick = { parentId = folder.id; parentMenu = false })
                    }
                }
            }
            Button({
                if (title.isNotBlank()) {
                    vm.createPlaylistFolder(title, parentId)
                    title = ""
                }
            }) { Text(tx(fa, "Add", "افزودن")) }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            val roots = folders.filter { it.parentId == null }.sortedBy { it.position }
            roots.forEach { root ->
                folderTreeItems(root, folders, playlists.count { it.folderId == root.id }, vm, fa, 0)
            }
            val orphans = folders.filter { f -> f.parentId != null && folders.none { it.id == f.parentId } }
            items(orphans, key = { "orphan-${it.id}" }) { folder ->
                FolderTreeRow(folder, folders, playlists.count { it.folderId == folder.id }, vm, fa, 0)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.folderTreeItems(
    folder: PlaylistFolderEntity,
    all: List<PlaylistFolderEntity>,
    directPlaylistCount: Int,
    vm: NeoPlusViewModel,
    fa: Boolean,
    depth: Int
) {
    item(key = "tree-${folder.id}") {
        FolderTreeRow(folder, all, directPlaylistCount, vm, fa, depth)
    }
    all.filter { it.parentId == folder.id }.sortedBy { it.position }.forEach { child ->
        folderTreeItems(child, all, 0, vm, fa, depth + 1)
    }
}

@Composable
private fun FolderTreeRow(
    folder: PlaylistFolderEntity,
    all: List<PlaylistFolderEntity>,
    playlistCount: Int,
    vm: NeoPlusViewModel,
    fa: Boolean,
    depth: Int
) {
    var menu by remember { mutableStateOf(false) }
    val invalidParents = remember(folder.id, all) { descendantsOf(folder.id, all) + folder.id }
    Row(
        Modifier.fillMaxWidth().padding(start = (16 + depth.coerceAtMost(6) * 20).dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(tx(fa, "$playlistCount playlists", "$playlistCount پلی‌لیست"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Box {
            TextButton({ menu = true }) { Text(tx(fa, "Parent", "والد")) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(tx(fa, "Root", "ریشه")) }, onClick = { vm.movePlaylistFolder(folder.id, null, folder.position); menu = false })
                all.filterNot { it.id in invalidParents }.forEach { candidate ->
                    DropdownMenuItem(text = { Text(candidate.title) }, onClick = { vm.movePlaylistFolder(folder.id, candidate.id, folder.position); menu = false })
                }
            }
        }
        TextButton({ vm.deletePlaylistFolder(folder.id) }) { Text(tx(fa, "Delete", "حذف")) }
    }
}

private fun descendantsOf(rootId: Long, all: List<PlaylistFolderEntity>): Set<Long> {
    val result = LinkedHashSet<Long>()
    val queue = ArrayDeque<Long>()
    queue.add(rootId)
    while (queue.isNotEmpty()) {
        val parent = queue.removeFirst()
        all.asSequence().filter { it.parentId == parent }.forEach { child ->
            if (result.add(child.id)) queue.add(child.id)
        }
    }
    return result
}

private fun tx(fa: Boolean, en: String, faText: String) = if (fa) faText else en
