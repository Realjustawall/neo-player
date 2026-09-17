@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neoplayer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoplayer.app.data.AdvancedAudioAnalysisEntity
import com.neoplayer.app.data.PinnedCollectionEntity
import com.neoplayer.app.data.PlaylistEntity
import com.neoplayer.app.data.SongEntity
import java.util.Locale
import kotlinx.coroutines.delay

/** Final additive shell. Classic UI -> NEO+ -> Track+ all remain underneath unchanged. */
@Composable
fun NeoOfflineProApp(
    mainViewModel: MainViewModel,
    plusViewModel: NeoPlusViewModel,
    experienceViewModel: TrackExperienceViewModel,
    proViewModel: OfflineProViewModel
) {
    val song by proViewModel.currentSong.collectAsState()
    val profile by proViewModel.currentProfile.collectAsState()
    val wallpaper = profile?.backgroundImageUri?.takeIf { it.isNotBlank() }?.let {
        TrackWallpaperOverride(it, profile!!.backgroundOpacity, profile!!.backgroundBlurDp)
    }
    var open by rememberSaveable { mutableStateOf(false) }

    MaterialTheme {
        CompositionLocalProvider(LocalTrackWallpaperOverride provides wallpaper) {
            Box(Modifier.fillMaxSize()) {
                NeoUltimateApp(mainViewModel, plusViewModel, experienceViewModel)
                if (!open) {
                    FloatingActionButton(
                        onClick = { open = true },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(48.dp),
                        shape = CircleShape
                    ) { Text("P+", fontWeight = FontWeight.Black) }
                }
                if (open) OfflineProPanel(proViewModel, plusViewModel) { open = false }
            }
        }
    }
}

@Composable
private fun OfflineProPanel(vm: OfflineProViewModel, plusVm: NeoPlusViewModel, close: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val fa = settings.language == "fa" || (settings.language == "system" && Locale.getDefault().language == "fa")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Icons.Rounded.OfflineBolt to ptx(fa, "Offline Backup", "پشتیبان آفلاین"),
        Icons.Rounded.GraphicEq to ptx(fa, "Analysis", "آنالیز"),
        Icons.Rounded.Image to ptx(fa, "Visual Pro", "تصویر حرفه‌ای"),
        Icons.Rounded.QueueMusic to ptx(fa, "Playlists+", "پلی‌لیست+"),
        Icons.Rounded.Security to ptx(fa, "Offline mode", "حالت آفلاین")
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEO Offline Pro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(ptx(fa, "Local-only advanced playback", "پخش پیشرفته کاملاً محلی"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                IconButton(close) { Icon(Icons.Rounded.Close, ptx(fa, "Close", "بستن")) }
            }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                items(tabs.size) { index ->
                    val item = tabs[index]
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        leadingIcon = { Icon(item.first, null, Modifier.size(18.dp)) },
                        label = { Text(item.second) },
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp))
            when (tab) {
                0 -> BackupTab(vm, fa)
                1 -> AnalysisTab(vm, fa)
                2 -> VisualProTab(vm, fa)
                3 -> PlaylistProTab(vm, fa)
                else -> StrictOfflineTab(vm, plusVm, fa)
            }
        }
    }
}

@Composable
private fun BackupTab(vm: OfflineProViewModel, fa: Boolean) {
    val settings by vm.settings.collectAsState()
    val items by vm.backupItems.collectAsState()
    val moods = remember { vm.availableMoods() }
    val library by vm.songs.collectAsState()
    val genres = remember(library) { vm.availableGenres() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ProCard {
                ToggleRow(ptx(fa, "Offline Backup", "پشتیبان آفلاین"), settings.offlineBackupEnabled) { vm.setBackupEnabled(it) }
                ToggleRow(ptx(fa, "Auto refresh from listening history", "به‌روزرسانی خودکار از سابقه پخش"), settings.offlineBackupAutoRefresh) { vm.setBackupAuto(it) }
                Text(ptx(fa, "Limit: ${settings.offlineBackupLimit}", "تعداد: ${settings.offlineBackupLimit}"), fontWeight = FontWeight.SemiBold)
                Slider(value = settings.offlineBackupLimit.toFloat(), onValueChange = { vm.setBackupLimit(it.toInt()) }, valueRange = 20f..300f, steps = 13)
                Text(ptx(fa, "Mood", "حال‌وهوا"), fontWeight = FontWeight.SemiBold)
                LazyRow { items(moods) { mood -> FilterChip(selected = settings.offlineBackupMood == mood, onClick = { vm.setBackupMood(mood) }, label = { Text(mood) }, modifier = Modifier.padding(end = 5.dp)) } }
                Text(ptx(fa, "Genre", "سبک"), fontWeight = FontWeight.SemiBold)
                LazyRow { items(genres) { genre -> FilterChip(selected = settings.offlineBackupGenre.equals(genre, true), onClick = { vm.setBackupGenre(genre) }, label = { Text(genre) }, modifier = Modifier.padding(end = 5.dp)) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.regenerateOfflineBackup() }) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(4.dp)); Text(ptx(fa, "Regenerate", "بازسازی")) }
                    OutlinedButton({ vm.playBackup() }, enabled = items.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text(ptx(fa, "Play", "پخش")) }
                    OutlinedButton({ vm.queueBackup() }, enabled = items.isNotEmpty()) { Icon(Icons.Rounded.QueueMusic, null); Text(ptx(fa, "Queue", "صف")) }
                }
            }
        }
        items(items, key = { it.song.id }) { value ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.playBackup(value.song) }.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(value.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${value.song.artist} • ${value.entry.mood} • ${value.entry.reason}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Text("${value.entry.score.toInt()}", color = MaterialTheme.colorScheme.primary)
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun AnalysisTab(vm: OfflineProViewModel, fa: Boolean) {
    val song by vm.currentSong.collectAsState()
    val analysis by vm.currentAnalysis.collectAsState()
    val replay by vm.currentReplayGain.collectAsState()
    val progress by vm.analysisProgress.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ProCard {
                Text(song?.let { "${it.title} • ${it.artist}" } ?: ptx(fa, "No active track", "آهنگی فعال نیست"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                analysis?.let { a ->
                    Text("BPM ${"%.1f".format(a.bpm)} • ${a.musicalKey.ifBlank { "Key ?" }} • ${a.camelotKey.ifBlank { "Camelot ?" }}")
                    Text("Mood ${a.mood} • Energy ${(a.energy * 100).toInt()}% • Dance ${(a.danceability * 100).toInt()}%")
                    Text("Beat ${(a.beatConfidence * 100).toInt()}% • Phrase ${(a.phraseConfidence * 100).toInt()}% • Key ${(a.keyConfidence * 100).toInt()}%")
                    Text("Centroid ${a.spectralCentroidHz.toInt()} Hz • DR ${"%.1f".format(a.dynamicRangeDb)} dB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } ?: Text(ptx(fa, "Not deeply analyzed yet", "هنوز آنالیز عمیق نشده"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                replay?.preferredGainDb?.let { Text("ReplayGain/R128 ${"%+.2f".format(it)} dB • ${replay?.source}") }
                if (progress.running) {
                    LinearProgressIndicator(progress = { if (progress.total > 1) (progress.completed + progress.fraction) / progress.total else progress.fraction }, modifier = Modifier.fillMaxWidth())
                    Text("${progress.completed}/${progress.total} ${progress.currentTitle}", fontSize = 12.sp)
                }
                progress.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.analyzeCurrent() }, enabled = song != null && !progress.running) { Text(ptx(fa, "Analyze track", "آنالیز آهنگ")) }
                    OutlinedButton({ vm.analyzeLibrary() }, enabled = !progress.running) { Text(ptx(fa, "Analyze library", "آنالیز کتابخانه")) }
                    if (progress.running) TextButton({ vm.cancelAnalysis() }) { Text(ptx(fa, "Stop", "توقف")) }
                }
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "What this adds", "چه چیزی اضافه می‌شود"), fontWeight = FontWeight.Bold)
                Text(ptx(fa, "Beat grid, downbeat/phase estimate, phrase grid, musical key + Camelot, mood, energy, danceability and spectral descriptors — all from local PCM.", "Beat grid، فاز ضرب، phrase grid، گام و Camelot، حال‌وهوا، انرژی، danceability و تحلیل طیفی؛ همگی از PCM محلی."))
            }
        }
    }
}

@Composable
private fun VisualProTab(vm: OfflineProViewModel, fa: Boolean) {
    val song by vm.currentSong.collectAsState()
    val profile by vm.currentProfile.collectAsState()
    val spectrum by vm.spectrum.collectAsState()
    val spectrumLoading by vm.spectrumLoading.collectAsState()
    val playback by vm.playback.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.setBackgroundImage(uri) }
    var opacity by remember(profile?.songId, profile?.backgroundOpacity) { mutableFloatStateOf(profile?.backgroundOpacity ?: .28f) }
    var blur by remember(profile?.songId, profile?.backgroundBlurDp) { mutableFloatStateOf((profile?.backgroundBlurDp ?: 18).toFloat()) }
    var clipStart by remember(profile?.songId, profile?.canvasStartMs) { mutableLongStateOf(profile?.canvasStartMs ?: 0L) }
    var clipEnd by remember(profile?.songId, profile?.canvasEndMs, song?.durationMs) { mutableLongStateOf(profile?.canvasEndMs?.takeIf { it > 0 } ?: (song?.durationMs ?: 0L)) }
    var clipSpeed by remember(profile?.songId, profile?.canvasPlaybackSpeed) { mutableFloatStateOf(profile?.canvasPlaybackSpeed ?: 1f) }

    LaunchedEffect(spectrum != null, song?.id) {
        while (song != null) { vm.refreshPosition(); delay(80L) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ProCard {
                Text(ptx(fa, "Independent player wallpaper", "پس‌زمینه مستقل پلیر"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(profile?.backgroundImageUri?.takeIf { it.isNotBlank() } ?: ptx(fa, "No wallpaper selected", "پس‌زمینه انتخاب نشده"), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ picker.launch(arrayOf("image/*")) }, enabled = song != null) { Text(ptx(fa, "Choose image", "انتخاب تصویر")) }
                    OutlinedButton({ vm.setBackgroundImage(null) }, enabled = !profile?.backgroundImageUri.isNullOrBlank()) { Text(ptx(fa, "Clear", "پاک کردن")) }
                }
                Text(ptx(fa, "Wallpaper visibility ${(opacity * 100).toInt()}%", "نمایانی پس‌زمینه ${(opacity * 100).toInt()}%"))
                Slider(opacity, { opacity = it }, valueRange = 0f..0.85f, onValueChangeFinished = { vm.setBackgroundStyle(opacity, blur.toInt()) })
                Text(ptx(fa, "Blur ${blur.toInt()} dp", "محو ${blur.toInt()} dp"))
                Slider(blur, { blur = it }, valueRange = 0f..48f, onValueChangeFinished = { vm.setBackgroundStyle(opacity, blur.toInt()) })
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "Playback-aligned FFT spectrum", "طیف FFT هماهنگ با پخش"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val frame = spectrum?.frameAt(playback.positionMs)
                if (frame != null) SpectrumPreview(frame) else Text(ptx(fa, "Generate once; then it follows playback without microphone permission.", "یک‌بار تولید می‌شود؛ سپس بدون مجوز میکروفون با پخش هماهنگ است."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ vm.ensureSpectrum() }, enabled = song != null && !spectrumLoading) { Text(if (spectrumLoading) ptx(fa, "Generating…", "در حال تولید…") else ptx(fa, "Generate FFT", "تولید FFT")) }
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "Canvas clip editor", "ویرایشگر برش Canvas"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val duration = (song?.durationMs ?: 0L).coerceAtLeast(1L)
                Text(ptx(fa, "Start ${clipStart / 1000f}s", "شروع ${clipStart / 1000f} ثانیه"))
                Slider(clipStart.toFloat(), { clipStart = it.toLong().coerceAtMost(clipEnd - 250L) }, valueRange = 0f..duration.toFloat(), onValueChangeFinished = { vm.setCanvasClip(clipStart, clipEnd, clipSpeed) })
                Text(ptx(fa, "End ${clipEnd / 1000f}s", "پایان ${clipEnd / 1000f} ثانیه"))
                Slider(clipEnd.toFloat(), { clipEnd = it.toLong().coerceAtLeast(clipStart + 250L).coerceAtMost(duration) }, valueRange = 0f..duration.toFloat(), onValueChangeFinished = { vm.setCanvasClip(clipStart, clipEnd, clipSpeed) })
                Text(ptx(fa, "Speed ${"%.2f".format(clipSpeed)}×", "سرعت ${"%.2f".format(clipSpeed)}×"))
                Slider(clipSpeed, { clipSpeed = it }, valueRange = .5f..2f, onValueChangeFinished = { vm.setCanvasClip(clipStart, clipEnd, clipSpeed) })
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

private data class PlaylistFilters(
    val query: String = "",
    val genre: String = "all",
    val mood: String = "all",
    val key: String = "all",
    val minBpm: Float = 0f,
    val maxBpm: Float = 220f,
    val minYear: Int = 0,
    val maxYear: Int = 9999,
    val minDurationMs: Long = 0L,
    val maxDurationMs: Long = Long.MAX_VALUE
)

@Composable
private fun PlaylistProTab(vm: OfflineProViewModel, fa: Boolean) {
    val playlists by vm.playlists.collectAsState()
    val folders by vm.playlistFolders.collectAsState()
    val pins by vm.pinnedCollections.collectAsState()
    val analyses by vm.advancedAnalyses.collectAsState()
    val library by vm.songs.collectAsState()
    val transferStatus by vm.playlistTransferStatus.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var importTitle by rememberSaveable { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<PlaylistEntity?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val playlist = pendingExport
        if (uri != null && playlist != null) vm.exportPlaylistM3u8(uri, playlist)
        pendingExport = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPlaylistM3u8(uri, importTitle.takeIf { it.isNotBlank() })
    }

    selectedId?.let { id ->
        playlists.firstOrNull { it.id == id }?.let { playlist ->
            PlaylistProDetail(vm, playlist, analyses, fa) { selectedId = null }
            return
        }
        selectedId = null
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ProCard {
                Text(ptx(fa, "M3U8 import / export", "ورود / خروج M3U8"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(importTitle, { importTitle = it }, Modifier.fillMaxWidth(), label = { Text(ptx(fa, "Imported playlist title (optional)", "نام پلی‌لیست واردشده (اختیاری)")) }, singleLine = true)
                OutlinedButton({ importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "text/plain", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text(ptx(fa, "Import local playlist", "ورود پلی‌لیست محلی")) }
                transferStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "Pinned collections order", "ترتیب مجموعه‌های سنجاق‌شده"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (pins.isEmpty()) Text(ptx(fa, "No pinned collections", "مجموعه سنجاق‌شده‌ای نیست"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                pins.forEachIndexed { index, pin ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(pinLabel(pin, playlists, library), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton({ vm.reorderPinned(index, index - 1) }, enabled = index > 0) { Icon(Icons.Rounded.ArrowUpward, null) }
                        IconButton({ vm.reorderPinned(index, index + 1) }, enabled = index < pins.lastIndex) { Icon(Icons.Rounded.ArrowDownward, null) }
                    }
                }
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "Playlist folders — accurate direct counts", "پوشه‌های پلی‌لیست — شمارش مستقیم دقیق"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                folders.sortedWith(compareBy({ it.parentId ?: Long.MIN_VALUE }, { it.position })).forEach { folder ->
                    val direct = playlists.count { it.folderId == folder.id }
                    val parent = folders.firstOrNull { it.id == folder.parentId }?.title
                    val siblings = folders.filter { it.parentId == folder.parentId }.sortedWith(compareBy({ it.position }, { it.createdAt }))
                    val siblingIndex = siblings.indexOfFirst { it.id == folder.id }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.title, fontWeight = FontWeight.SemiBold)
                            Text(ptx(fa, "$direct direct playlists${parent?.let { " • parent: $it" }.orEmpty()}", "$direct پلی‌لیست مستقیم${parent?.let { " • والد: $it" }.orEmpty()}"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.reorderFolder(folder, -1) }, enabled = siblingIndex > 0) { Icon(Icons.Rounded.ArrowUpward, null) }
                        IconButton({ vm.reorderFolder(folder, 1) }, enabled = siblingIndex in 0 until siblings.lastIndex) { Icon(Icons.Rounded.ArrowDownward, null) }
                    }
                }
            }
        }
        items(playlists.sortedWith(compareBy<PlaylistEntity> { it.customOrder }.thenByDescending { it.createdAt }), key = { it.id }) { playlist ->
            val pinned = pins.any { it.type == "playlist" && it.key == playlist.id.toString() }
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { selectedId = playlist.id }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(playlist.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(folders.firstOrNull { it.id == playlist.folderId }?.title ?: ptx(fa, "Root", "ریشه"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ vm.togglePin("playlist", playlist.id.toString()) }) { Text(if (pinned) ptx(fa, "Unpin", "برداشتن") else ptx(fa, "Pin", "سنجاق")) }
                    TextButton({ pendingExport = playlist; exportLauncher.launch("${safeFileName(playlist.title)}.m3u8") }) { Text(ptx(fa, "Export", "خروجی")) }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun PlaylistProDetail(
    vm: OfflineProViewModel,
    playlist: PlaylistEntity,
    analyses: List<AdvancedAudioAnalysisEntity>,
    fa: Boolean,
    back: () -> Unit
) {
    val raw by vm.rawPlaylistSongs(playlist.id).collectAsState(initial = emptyList())
    val scopedHidden by vm.playlistScopedHiddenIds(playlist.id).collectAsState(initial = emptyList())
    val hidden = remember(scopedHidden) { scopedHidden.toSet() }
    val analysisById = remember(analyses) { analyses.associateBy { it.songId } }
    var query by rememberSaveable(playlist.id) { mutableStateOf("") }
    var genre by rememberSaveable(playlist.id) { mutableStateOf("all") }
    var mood by rememberSaveable(playlist.id) { mutableStateOf("all") }
    var key by rememberSaveable(playlist.id) { mutableStateOf("all") }
    var minBpm by rememberSaveable(playlist.id) { mutableFloatStateOf(0f) }
    var maxBpm by rememberSaveable(playlist.id) { mutableFloatStateOf(220f) }
    var minYear by rememberSaveable(playlist.id) { mutableIntStateOf(0) }
    var maxYear by rememberSaveable(playlist.id) { mutableIntStateOf(2100) }
    var minMinutes by rememberSaveable(playlist.id) { mutableFloatStateOf(0f) }
    var maxMinutes by rememberSaveable(playlist.id) { mutableFloatStateOf(30f) }
    var folderMenu by remember { mutableStateOf(false) }
    val folders by vm.playlistFolders.collectAsState()

    val filtered = remember(raw, hidden, analysisById, query, genre, mood, key, minBpm, maxBpm, minYear, maxYear, minMinutes, maxMinutes) {
        raw.filter { song ->
            val a = analysisById[song.id]
            val qOk = query.isBlank() || sequenceOf(song.title, song.artist, song.album, song.genre).any { it.contains(query, true) }
            val genreOk = genre == "all" || song.genre.equals(genre, true)
            val moodOk = mood == "all" || a?.mood.equals(mood, true)
            val keyOk = key == "all" || a?.camelotKey.equals(key, true) || a?.musicalKey.equals(key, true)
            val bpm = a?.bpm ?: 0f
            val bpmOk = bpm == 0f || bpm in minBpm..maxBpm
            val yearOk = song.year == 0 || song.year in minYear..maxYear
            val durationMin = (minMinutes * 60_000f).toLong()
            val durationMax = (maxMinutes * 60_000f).toLong()
            val durationOk = song.durationMs in durationMin..durationMax
            qOk && genreOk && moodOk && keyOk && bpmOk && yearOk && durationOk
        }
    }
    val genres = remember(raw) { listOf("all") + raw.map { it.genre }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }.sorted() }
    val moods = remember(analyses) { listOf("all") + analyses.map { it.mood }.filter { it.isNotBlank() && it != "unknown" }.distinct().sorted() }
    val keys = remember(analyses) { listOf("all") + analyses.map { it.camelotKey }.filter { it.isNotBlank() }.distinct().sorted() }
    val visiblePlayable = filtered.filterNot { it.id in hidden }
    val totalMs = visiblePlayable.sumOf { it.durationMs }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(back) { Text(ptx(fa, "Back", "بازگشت")) }
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(ptx(fa, "${visiblePlayable.size}/${raw.size} visible • ${formatDuration(totalMs)}", "${visiblePlayable.size}/${raw.size} قابل پخش • ${formatDuration(totalMs)}"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                OutlinedButton({ folderMenu = true }) { Icon(Icons.Rounded.Folder, null); Text(ptx(fa, "Folder", "پوشه")) }
                DropdownMenu(folderMenu, { folderMenu = false }) {
                    DropdownMenuItem(text = { Text(ptx(fa, "Root", "ریشه")) }, onClick = { vm.movePlaylistToFolder(playlist.id, null); folderMenu = false })
                    folders.forEach { folder -> DropdownMenuItem(text = { Text(folder.title) }, onClick = { vm.movePlaylistToFolder(playlist.id, folder.id); folderMenu = false }) }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ProCard {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, label = { Text(ptx(fa, "Filter this playlist", "فیلتر این پلی‌لیست")) }, singleLine = true)
                    Text(ptx(fa, "Genre", "سبک"), fontWeight = FontWeight.SemiBold)
                    LazyRow { items(genres) { v -> FilterChip(genre == v, { genre = v }, { Text(v) }, modifier = Modifier.padding(end = 4.dp)) } }
                    Text(ptx(fa, "Mood", "حال‌وهوا"), fontWeight = FontWeight.SemiBold)
                    LazyRow { items(moods) { v -> FilterChip(mood == v, { mood = v }, { Text(v) }, modifier = Modifier.padding(end = 4.dp)) } }
                    Text(ptx(fa, "Key / Camelot", "گام / Camelot"), fontWeight = FontWeight.SemiBold)
                    LazyRow { items(keys) { v -> FilterChip(key == v, { key = v }, { Text(v) }, modifier = Modifier.padding(end = 4.dp)) } }
                    Text("BPM ${minBpm.toInt()}–${maxBpm.toInt()}")
                    RangeSliders(minBpm, maxBpm, 0f, 220f, { minBpm = it.coerceAtMost(maxBpm) }, { maxBpm = it.coerceAtLeast(minBpm) })
                    Text(ptx(fa, "Year $minYear–$maxYear", "سال $minYear–$maxYear"))
                    RangeSliders(minYear.toFloat(), maxYear.toFloat(), 1950f, 2100f, { minYear = it.toInt().coerceAtMost(maxYear) }, { maxYear = it.toInt().coerceAtLeast(minYear) })
                    Text(ptx(fa, "Duration ${minMinutes.toInt()}–${maxMinutes.toInt()} min", "مدت ${minMinutes.toInt()}–${maxMinutes.toInt()} دقیقه"))
                    RangeSliders(minMinutes, maxMinutes, 0f, 30f, { minMinutes = it.coerceAtMost(maxMinutes) }, { maxMinutes = it.coerceAtLeast(minMinutes) })
                    Button(
                        onClick = { visiblePlayable.firstOrNull()?.let { vm.playPlaylistTrack(it, visiblePlayable) } },
                        enabled = visiblePlayable.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(ptx(fa, "Play filtered tracks", "پخش آهنگ‌های فیلترشده")) }
                }
            }
            items(filtered, key = { it.id }) { song ->
                val a = analysisById[song.id]
                val scoped = song.id in hidden
                val rawIndex = raw.indexOfFirst { it.id == song.id }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(enabled = !scoped) { if (!scoped) vm.playPlaylistTrack(song, raw.filterNot { it.id in hidden }) }) {
                            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${song.artist} • ${a?.bpm?.takeIf { it > 0 }?.let { "${it.toInt()} BPM • " }.orEmpty()}${a?.mood ?: "unanalyzed"} • ${a?.camelotKey.orEmpty()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ vm.togglePlaylistHidden(playlist.id, song.id) }) { Text(if (scoped) ptx(fa, "Unhide", "نمایش") else ptx(fa, "Hide here", "مخفی اینجا")) }
                        IconButton({ moveRawTrack(vm, playlist.id, raw, rawIndex, -1) }, enabled = rawIndex > 0) { Icon(Icons.Rounded.ArrowUpward, null) }
                        IconButton({ moveRawTrack(vm, playlist.id, raw, rawIndex, 1) }, enabled = rawIndex in 0 until raw.lastIndex) { Icon(Icons.Rounded.ArrowDownward, null) }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun RangeSliders(low: Float, high: Float, min: Float, max: Float, setLow: (Float) -> Unit, setHigh: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(low, setLow, Modifier.weight(1f), valueRange = min..max)
        Slider(high, setHigh, Modifier.weight(1f), valueRange = min..max)
    }
}

private fun moveRawTrack(vm: OfflineProViewModel, playlistId: Long, raw: List<SongEntity>, index: Int, delta: Int) {
    val target = index + delta
    if (index !in raw.indices || target !in raw.indices) return
    val ids = raw.map { it.id }.toMutableList()
    val moved = ids.removeAt(index)
    ids.add(target, moved)
    vm.reorderPlaylistTracks(playlistId, ids)
}

private fun pinLabel(pin: PinnedCollectionEntity, playlists: List<PlaylistEntity>, songs: List<SongEntity>): String = when (pin.type) {
    "playlist" -> playlists.firstOrNull { it.id.toString() == pin.key }?.title?.let { "Playlist • $it" } ?: "Playlist • ${pin.key}"
    "album" -> pin.key.toLongOrNull()?.let { id -> songs.firstOrNull { it.albumId == id }?.album }?.let { "Album • $it" } ?: "Album • ${pin.key}"
    "artist" -> "Artist • ${pin.key}"
    "genre" -> "Genre • ${pin.key}"
    "folder" -> "Folder • ${pin.key}"
    else -> "${pin.type} • ${pin.key}"
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun safeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "playlist" }

@Composable
private fun StrictOfflineTab(vm: OfflineProViewModel, plusVm: NeoPlusViewModel, fa: Boolean) {
    val settings by vm.settings.collectAsState()
    val cache by plusVm.cacheBreakdown.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ProCard {
                ToggleRow(ptx(fa, "Strict Offline Mode", "حالت کاملاً آفلاین"), settings.strictOfflineMode) { vm.setStrictOffline(it) }
                Text(ptx(fa, "When enabled, optional runtime online lyric providers are hard-disabled. Local library, sidecar LRC, Vosk AI, Canvas, analysis and recommendations keep working.", "با فعال شدن، provider آنلاین متن در زمان اجرا کاملاً غیرفعال می‌شود؛ کتابخانه محلی، LRC، هوش Vosk، Canvas، تحلیل و پیشنهادها کار می‌کنند."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "Granular cache manager", "مدیریت جزئی کش"), fontWeight = FontWeight.Bold)
                Text(ptx(fa, "Total ${formatBytes(cache.totalBytes)} • Waveform ${formatBytes(cache.waveformBytes)} • Spectrum ${formatBytes(cache.spectrumBytes)} • Other ${formatBytes(cache.otherBytes)}", "کل ${formatBytes(cache.totalBytes)} • موج ${formatBytes(cache.waveformBytes)} • طیف ${formatBytes(cache.spectrumBytes)} • سایر ${formatBytes(cache.otherBytes)}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { OutlinedButton({ plusVm.clearWaveformCache() }) { Text(ptx(fa, "Waveforms", "موج‌ها")) } }
                    item { OutlinedButton({ plusVm.clearSpectrumCache() }) { Text(ptx(fa, "Spectrum", "طیف")) } }
                    item { OutlinedButton({ plusVm.clearAnalysisCache() }) { Text(ptx(fa, "Analysis", "آنالیز")) } }
                }
                Button({ plusVm.clearCache() }) { Text(ptx(fa, "Clear all regeneratable cache", "پاک‌کردن کل کش قابل بازسازی")) }
                Text(ptx(fa, "Library database, playlists, themes, Canvas links and manual lyrics are not deleted.", "دیتابیس کتابخانه، پلی‌لیست‌ها، تم‌ها، لینک Canvas و متن دستی حذف نمی‌شوند."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "Normalization source", "منبع نرمال‌سازی"), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("smart", "replaygain", "analysis").forEach { mode ->
                        FilterChip(selected = settings.normalizationMode == mode, onClick = { vm.setNormalizationMode(mode) }, label = { Text(mode) })
                    }
                }
                Text(ptx(fa, "Smart prefers ReplayGain/R128 tags and falls back to local loudness analysis.", "Smart ابتدا ReplayGain/R128 را استفاده می‌کند و در نبود آن به تحلیل بلندی صدای محلی برمی‌گردد."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow(ptx(fa, "Phrase/key aware AutoMix", "AutoMix آگاه از Phrase/Key"), settings.advancedAutomixEnabled) { vm.setAdvancedAutomix(it) }
            }
        }
        item {
            ProCard {
                Text(ptx(fa, "Playback persistence", "ماندگاری پخش"), fontWeight = FontWeight.Bold)
                ToggleRow(ptx(fa, "Remember queue", "حفظ صف پخش"), settings.rememberQueue) { vm.setRememberQueue(it) }
                ToggleRow(ptx(fa, "Resume last position", "ادامه از موقعیت قبلی"), settings.resumeLastSong) { vm.setResumeLastSong(it) }
                Text(ptx(fa, "Default speed ${"%.2f".format(Locale.ROOT, settings.defaultSpeed)}×", "سرعت پیش‌فرض ${"%.2f".format(Locale.ROOT, settings.defaultSpeed)}×"))
                Slider(settings.defaultSpeed, vm::setDefaultSpeed, valueRange = .5f..2f, steps = 5)
                Text(ptx(fa, "Lyrics source mode", "حالت منبع متن"), fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("auto", "offline", "online")) { mode ->
                        FilterChip(
                            selected = settings.lyricsMode == mode,
                            onClick = { vm.setLyricsMode(mode) },
                            enabled = mode != "online" || !settings.strictOfflineMode,
                            label = { Text(mode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked, onChecked)
    }
}

@Composable
private fun SpectrumPreview(values: FloatArray) {
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val width = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, v ->
            val h = size.height * v.coerceIn(.02f, 1f)
            drawRoundRect(
                color = Color(0xFF8C7CFF),
                topLeft = androidx.compose.ui.geometry.Offset(index * (width + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(width.coerceAtLeast(1f), h),
                cornerRadius = CornerRadius(width / 2f, width / 2f)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(Locale.ROOT, bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(Locale.ROOT, bytes / 1_048_576.0)
    bytes >= 1024L -> "%.1f KB".format(Locale.ROOT, bytes / 1024.0)
    else -> "$bytes B"
}

private fun ptx(fa: Boolean, en: String, faText: String) = if (fa) faText else en
