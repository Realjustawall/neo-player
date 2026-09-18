package com.neoplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.media3.common.MediaItem
import com.neoplayer.app.data.PlaylistEntity
import com.neoplayer.app.data.PlaylistFolderEntity
import com.neoplayer.app.data.PlaylistPreferenceEntity
import com.neoplayer.app.data.SongEntity
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/**
 * Purely additive shell. NeoPlayerApp is rendered unchanged underneath and Neo+ opens only when
 * requested, so every pre-existing screen and gesture remains available exactly as before.
 */
@Composable
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
}

@Composable
private fun NeoPlusPanel(vm: NeoPlusViewModel, close: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val fa = settings.language == "fa" || (settings.language == "system" && Locale.getDefault().language == "fa")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(
        t(fa, "Playlists", "پلی‌لیست‌ها"),
        t(fa, "Audio", "صدا"),
        t(fa, "Library", "کتابخانه"),
        t(fa, "Search", "جستجو"),
        t(fa, "Cache", "کش")
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Settings, null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEO+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        t(fa, "Advanced offline features • no account, no server", "قابلیت‌های پیشرفته آفلاین • بدون حساب و سرور"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                IconButton(close) { Icon(Icons.Rounded.Close, t(fa, "Close", "بستن")) }
            }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                items(labels.size) { index ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(labels[index]) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp))
            when (tab) {
                0 -> PlaylistHub(vm, fa)
                1 -> AudioHub(vm, fa)
                2 -> LibraryHub(vm, fa)
                3 -> SearchHub(vm, fa)
                else -> CacheHub(vm, fa)
            }
        }
    }
}

@Composable
private fun PlaylistHub(vm: NeoPlusViewModel, fa: Boolean) {
    val playlists by vm.playlists.collectAsState()
    val folders by vm.playlistFolders.collectAsState()
    val pins by vm.pinnedCollections.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newFolder by remember { mutableStateOf("") }

    selectedId?.let { id ->
        val playlist = playlists.firstOrNull { it.id == id }
        if (playlist != null) {
            AdvancedPlaylist(vm, playlist, fa) { selectedId = null }
            return
        }
        selectedId = null
    }

    val orderedPlaylists = remember(playlists) {
        playlists.sortedWith(compareBy<PlaylistEntity> { it.customOrder }.thenByDescending { it.createdAt })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionCard {
                Text(t(fa, "Playlist folders", "پوشه‌های پلی‌لیست"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newFolder,
                        onValueChange = { newFolder = it },
                        label = { Text(t(fa, "New folder", "پوشه جدید")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton({
                        if (newFolder.isNotBlank()) {
                            vm.createPlaylistFolder(newFolder)
                            newFolder = ""
                        }
                    }) { Icon(Icons.Rounded.Add, t(fa, "Create", "ساخت")) }
                }
            }
        }
        items(folders, key = { "folder-${it.id}" }) { folder ->
            FolderRow(folder, orderedPlaylists.count { it.folderId == folder.id }, fa) { vm.deletePlaylistFolder(folder.id) }
        }
        item { SectionHeader(t(fa, "All playlists", "همه پلی‌لیست‌ها")) }
        itemsIndexed(orderedPlaylists, key = { _, value -> "playlist-${value.id}" }) { index, playlist ->
            val pinned = pins.any { it.type == "playlist" && it.key == playlist.id.toString() }
            PlaylistManagerRow(
                playlist = playlist,
                folders = folders,
                pinned = pinned,
                fa = fa,
                open = { selectedId = playlist.id },
                pin = { vm.togglePin("playlist", playlist.id.toString()) },
                moveFolder = { vm.movePlaylistToFolder(playlist.id, it) },
                moveUp = {
                    if (index > 0) {
                        val mutable = orderedPlaylists.map { it.id }.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index - 1, item)
                        vm.reorderPlaylistLibrary(mutable)
                    }
                },
                moveDown = {
                    if (index < orderedPlaylists.lastIndex) {
                        val mutable = orderedPlaylists.map { it.id }.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index + 1, item)
                        vm.reorderPlaylistLibrary(mutable)
                    }
                }
            )
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun FolderRow(folder: PlaylistFolderEntity, count: Int, fa: Boolean, delete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.title, fontWeight = FontWeight.SemiBold)
            Text(t(fa, "$count playlists", "$count پلی‌لیست"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        IconButton(delete) { Icon(Icons.Rounded.Delete, t(fa, "Delete folder", "حذف پوشه")) }
    }
}

@Composable
private fun PlaylistManagerRow(
    playlist: PlaylistEntity,
    folders: List<PlaylistFolderEntity>,
    pinned: Boolean,
    fa: Boolean,
    open: () -> Unit,
    pin: () -> Unit,
    moveFolder: (Long?) -> Unit,
    moveUp: () -> Unit,
    moveDown: () -> Unit
) {
    var folderMenu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = open),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.QueueMusic, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                val folder = folders.firstOrNull { it.id == playlist.folderId }?.title
                Text(folder ?: t(fa, "No folder", "بدون پوشه"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(pin) { Icon(if (pinned) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, t(fa, "Pin", "سنجاق")) }
            Box {
                IconButton({ folderMenu = true }) { Icon(Icons.Rounded.Folder, t(fa, "Move to folder", "انتقال به پوشه")) }
                DropdownMenu(folderMenu, { folderMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(t(fa, "No folder", "بدون پوشه")) },
                        onClick = { moveFolder(null); folderMenu = false }
                    )
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.title) },
                            onClick = { moveFolder(folder.id); folderMenu = false }
                        )
                    }
                }
            }
            IconButton(moveUp) { Icon(Icons.Rounded.ArrowUpward, t(fa, "Move up", "بالا")) }
            IconButton(moveDown) { Icon(Icons.Rounded.ArrowDownward, t(fa, "Move down", "پایین")) }
        }
    }
}

@Composable
private fun AdvancedPlaylist(vm: NeoPlusViewModel, playlist: PlaylistEntity, fa: Boolean, back: () -> Unit) {
    val source by vm.playlistSongs(playlist.id).collectAsState(initial = emptyList())
    val hidden by vm.hiddenGlobalIds.collectAsState()
    val pref by vm.playlistPreference(playlist.id).collectAsState(initial = null)
    var query by rememberSaveable(playlist.id) { mutableStateOf("") }
    var sort by rememberSaveable(playlist.id, pref?.sortMode) { mutableStateOf(pref?.sortMode ?: "custom") }
    var ascending by rememberSaveable(playlist.id, pref?.ascending) { mutableStateOf(pref?.ascending ?: true) }
    var viewMode by rememberSaveable(playlist.id, pref?.viewMode) { mutableStateOf(pref?.viewMode ?: "list") }

    val filtered = remember(source, hidden, query, sort, ascending) {
        val visible = source.filterNot { it.id in hidden }.filter {
            query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
        val comparator = when (sort) {
            "title" -> compareBy<SongEntity> { it.title.lowercase(Locale.ROOT) }
            "artist" -> compareBy { it.artist.lowercase(Locale.ROOT) }
            "album" -> compareBy { it.album.lowercase(Locale.ROOT) }
            "date" -> compareBy { it.dateAdded }
            "duration" -> compareBy { it.durationMs }
            else -> null
        }
        if (comparator == null) visible else visible.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(back) { Text(t(fa, "Back", "بازگشت")) }
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(t(fa, "${filtered.size} visible tracks", "${filtered.size} آهنگ قابل نمایش"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = { filtered.firstOrNull()?.let { vm.play(it, filtered) } },
                enabled = filtered.isNotEmpty()
            ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(t(fa, "Play", "پخش")) }
        }
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            label = { Text(t(fa, "Search in playlist", "جستجو داخل پلی‌لیست")) },
            singleLine = true
        )
        LazyRow(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            val modes = listOf("custom", "title", "artist", "album", "date", "duration")
            items(modes) { mode ->
                FilterChip(
                    selected = sort == mode,
                    onClick = {
                        sort = mode
                        vm.savePlaylistPreference(playlist.id, sort, ascending, viewMode)
                    },
                    label = { Text(sortLabel(mode, fa)) },
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
            item {
                FilterChip(
                    selected = ascending,
                    onClick = {
                        ascending = !ascending
                        vm.savePlaylistPreference(playlist.id, sort, ascending, viewMode)
                    },
                    label = { Text(if (ascending) "↑" else "↓") },
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
            item {
                FilterChip(
                    selected = viewMode == "grid",
                    onClick = {
                        viewMode = if (viewMode == "grid") "list" else "grid"
                        vm.savePlaylistPreference(playlist.id, sort, ascending, viewMode)
                    },
                    label = { Text(if (viewMode == "grid") t(fa, "Grid", "شبکه‌ای") else t(fa, "List", "لیست")) },
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
        }
        if (viewMode == "grid") {
            LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize()) {
                gridItems(filtered, key = { it.id }) { song ->
                    SongGridCard(song, fa, { vm.play(song, filtered) }, { vm.toggleHidden(song.id) })
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { song ->
                    AdvancedSongRow(song, fa, { vm.play(song, filtered) }, { vm.toggleHidden(song.id) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SongGridCard(song: SongEntity, fa: Boolean, play: () -> Unit, hide: () -> Unit) {
    Card(Modifier.padding(6.dp).clickable(onClick = play)) {
        Column(Modifier.padding(12.dp)) {
            Icon(Icons.Rounded.MusicNote, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            TextButton(hide) { Text(t(fa, "Hide", "مخفی")) }
        }
    }
}

@Composable
private fun AdvancedSongRow(song: SongEntity, fa: Boolean, play: () -> Unit, hide: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = play).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        TextButton(hide) { Text(t(fa, "Hide", "مخفی")) }
    }
}

@Composable
private fun AudioHub(vm: NeoPlusViewModel, fa: Boolean) {
    val settings by vm.settings.collectAsState()
    val analysis by vm.currentAnalysis.collectAsState()
    val progress by vm.analysisProgress.collectAsState()
    val playback by vm.playback.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionCard {
                Text(t(fa, "Loudness normalization", "یکسان‌سازی بلندی صدا"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    t(fa, "On-device analysis keeps quiet and loud tracks closer in perceived volume.", "تحلیل روی دستگاه صدای آهنگ‌های کم‌صدا و پرصدا را به سطح نزدیک‌تری می‌رساند."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(fa, "Enabled", "فعال"), Modifier.weight(1f))
                    Switch(settings.loudnessNormalization, vm::setLoudnessNormalization)
                }
                Text(t(fa, "Target ${"%.1f".format(settings.normalizationTargetLufs)} LUFS", "هدف ${"%.1f".format(settings.normalizationTargetLufs)} LUFS"))
                Slider(
                    value = settings.normalizationTargetLufs,
                    onValueChange = vm::setNormalizationTarget,
                    valueRange = -23f..-8f,
                    steps = 14
                )
                analysis?.let {
                    Text("LUFS ${"%.1f".format(it.integratedLufs)} • Peak ${"%.1f".format(it.peakDb)} dB • BPM ${"%.0f".format(it.bpm)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(vm::analyzeCurrent, enabled = playback.current != null) { Text(t(fa, "Analyze current", "تحلیل آهنگ فعلی")) }
                    Button(if (progress.running) vm::cancelLibraryAnalysis else vm::analyzeLibrary) {
                        Text(if (progress.running) t(fa, "Stop analysis", "توقف تحلیل") else t(fa, "Analyze library", "تحلیل کتابخانه"))
                    }
                }
                if (progress.total > 0) {
                    Text("${progress.completed}/${progress.total} ${progress.currentTitle}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                progress.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
        item {
            SectionCard {
                Text(t(fa, "Transitions", "انتقال بین آهنگ‌ها"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t(fa, "AutoMix", "اتو میکس"), fontWeight = FontWeight.SemiBold)
                        Text(t(fa, "Use cached BPM analysis for tempo-aware local transitions.", "از BPM تحلیل‌شده برای انتقال هوشمند آفلاین استفاده می‌کند."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(settings.automixEnabled, vm::setAutomixEnabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t(fa, "Gapless", "پخش بدون فاصله"), fontWeight = FontWeight.SemiBold)
                        Text(t(fa, "Keep compatible album tracks continuous.", "ترک‌های سازگار آلبوم را بدون فاصله پخش می‌کند."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(settings.gaplessEnabled, vm::setGaplessEnabled)
                }
                Text(t(fa, "Crossfade duration remains available in the original Settings screen.", "مدت Crossfade همچنان در تنظیمات اصلی موجود است."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun LibraryHub(vm: NeoPlusViewModel, fa: Boolean) {
    val settings by vm.settings.collectAsState()
    val folders by vm.folders.collectAsState()
    val included by vm.includedFolders.collectAsState()
    val hiddenRows by vm.hiddenSongs.collectAsState()
    val songs by vm.songs.collectAsState()
    val pins by vm.pinnedCollections.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionCard {
                Text(t(fa, "Library layout", "نمای کتابخانه"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(settings.libraryViewMode == "list", { vm.setLibraryViewMode("list") }, { Text(t(fa, "List", "لیست")) })
                    FilterChip(settings.libraryViewMode == "grid", { vm.setLibraryViewMode("grid") }, { Text(t(fa, "Grid", "شبکه‌ای")) })
                }
            }
        }
        item {
            SectionCard {
                Text(t(fa, "Source folders", "پوشه‌های منبع"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (included.isEmpty()) t(fa, "All MediaStore folders are currently included.", "در حال حاضر همه پوشه‌های MediaStore خوانده می‌شوند.")
                    else t(fa, "Only selected folders are indexed. Exclusions still win.", "فقط پوشه‌های انتخاب‌شده ایندکس می‌شوند و Exclude اولویت دارد."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (included.isNotEmpty()) TextButton(vm::useAllSourceFolders) { Text(t(fa, "Use all folders", "استفاده از همه پوشه‌ها")) }
            }
        }
        items(folders, key = { "source-${it.relativePath}" }) { folder ->
            val selected = folder.relativePath.trim('/').replace('\\', '/') in included
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null)
                Spacer(Modifier.width(8.dp))
                Text(folder.relativePath, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton({ if (selected) vm.removeSourceFolder(folder.relativePath) else vm.addSourceFolder(folder.relativePath) }) {
                    Text(if (selected) t(fa, "Remove", "حذف") else t(fa, "Only this +", "افزودن"))
                }
            }
        }
        item { SectionHeader(t(fa, "Pinned collections", "موارد سنجاق‌شده")) }
        if (pins.isEmpty()) item { Hint(t(fa, "Pin playlists from the Playlists tab. Albums and artists can be pinned from search suggestions in future views without changing existing favorites.", "از بخش پلی‌لیست‌ها موارد را سنجاق کنید. Pin از Favoriteهای قبلی جدا نگه داشته شده است.")) }
        items(pins, key = { "pin-${it.type}-${it.key}" }) { pin ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("${pin.type}: ${pin.key}", Modifier.weight(1f)); TextButton({ vm.togglePin(pin.type, pin.key) }) { Text(t(fa, "Unpin", "برداشتن")) }
            }
        }
        item { SectionHeader(t(fa, "Hidden songs", "آهنگ‌های مخفی")) }
        if (hiddenRows.isEmpty()) item { Hint(t(fa, "No hidden songs.", "آهنگ مخفی وجود ندارد.")) }
        items(hiddenRows, key = { "hidden-${it.songId}-${it.scopeType}-${it.scopeKey}" }) { row ->
            val song = songs.firstOrNull { it.id == row.songId }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, null)
                Spacer(Modifier.width(8.dp))
                Text(song?.title ?: "#${row.songId}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton({ vm.toggleHidden(row.songId, row.scopeType, row.scopeKey) }) { Text(t(fa, "Unhide", "نمایش")) }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun SearchHub(vm: NeoPlusViewModel, fa: Boolean) {
    val songs by vm.songs.collectAsState()
    val settings by vm.settings.collectAsState()
    val hidden by vm.hiddenGlobalIds.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val suggestions = remember(query, songs, settings.recentSearches) { vm.suggestions(query) }
    val results = remember(query, songs, hidden) {
        if (query.isBlank()) emptyList() else songs.asSequence()
            .filterNot { it.id in hidden }
            .filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) || it.genre.contains(query, true) }
            .take(100)
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(12.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            label = { Text(t(fa, "Smart local search", "جستجوی هوشمند محلی")) },
            singleLine = true
        )
        if (query.isBlank() && settings.recentSearches.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t(fa, "Recent searches", "جستجوهای اخیر"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(vm::clearRecentSearches) { Text(t(fa, "Clear", "پاک کردن")) }
            }
        }
        LazyRow(Modifier.padding(horizontal = 8.dp)) {
            items(suggestions) { value ->
                FilterChip(
                    selected = false,
                    onClick = { query = value; vm.registerSearch(value) },
                    label = { Text(value, maxLines = 1) },
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { "search-${it.id}" }) { song ->
                AdvancedSongRow(
                    song,
                    fa,
                    play = { vm.registerSearch(query); vm.play(song, results) },
                    hide = { vm.toggleHidden(song.id) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CacheHub(vm: NeoPlusViewModel, fa: Boolean) {
    val cacheBytes by vm.cacheBytes.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionCard {
                Text(t(fa, "Storage & cache", "حافظه و کش"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatBytes(cacheBytes), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    t(fa, "Includes temporary artwork and local audio-analysis cache. Your music, playlists, favorites, lyrics and history are not stored here.", "شامل فایل‌های موقت کاور و کش تحلیل صوتی است. موسیقی، پلی‌لیست، علاقه‌مندی‌ها، متن آهنگ و تاریخچه حذف نمی‌شوند."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(vm::refreshCacheSize) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(4.dp)); Text(t(fa, "Refresh", "تازه‌سازی")) }
                    Button({ confirm = true }) { Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(4.dp)); Text(t(fa, "Clear cache", "پاک کردن کش")) }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t(fa, "Clear cache?", "کش پاک شود؟")) },
            text = { Text(t(fa, "Only regeneratable cache is removed. Library data stays intact.", "فقط داده‌های قابل بازسازی کش پاک می‌شوند و اطلاعات کتابخانه باقی می‌مانند.")) },
            confirmButton = { TextButton({ vm.clearCache(); confirm = false }) { Text(t(fa, "Clear", "پاک کردن")) } },
            dismissButton = { TextButton({ confirm = false }) { Text(t(fa, "Cancel", "لغو")) } }
        )
    }
}

@Composable
private fun SectionCard(content: @Composable Column.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp)
}

private fun sortLabel(mode: String, fa: Boolean): String = when (mode) {
    "title" -> t(fa, "Title", "عنوان")
    "artist" -> t(fa, "Artist", "هنرمند")
    "album" -> t(fa, "Album", "آلبوم")
    "date" -> t(fa, "Date", "تاریخ")
    "duration" -> t(fa, "Duration", "مدت")
    else -> t(fa, "Custom", "دستی")
}

private fun t(fa: Boolean, en: String, faText: String): String = if (fa) faText else en

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
