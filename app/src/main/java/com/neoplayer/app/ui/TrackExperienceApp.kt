@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neoplayer.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VideoLibrary
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.neoplayer.app.data.OfflineSpeechModelEntity
import com.neoplayer.app.data.TrackVisualProfileEntity
import com.neoplayer.app.domain.LocalRecommendation
import com.neoplayer.app.lyrics.OfflineLyricsTranscriber
import com.neoplayer.app.visual.LocalCanvasVideo
import com.neoplayer.app.visual.WaveformData
import java.util.Locale
import org.json.JSONArray

/** Final additive wrapper: NeoCompleteApp remains byte-for-byte compatible underneath. */
@Composable
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
    MaterialTheme {
        CompositionLocalProvider(LocalTrackThemeOverride provides themeOverride) {
        Box(Modifier.fillMaxSize()) {
            NeoCompleteApp(mainViewModel, plusViewModel)
            if (current != null && !open) {
                FloatingActionButton(
                    onClick = { open = true },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 42.dp).size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.AutoAwesome, "Track+")
                }
            }
            if (open && current != null) {
                TrackExperiencePanel(experienceViewModel) { open = false }
            }
        }
        }
    }
}

@Composable
private fun TrackExperiencePanel(vm: TrackExperienceViewModel, close: () -> Unit) {
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
    var tab by rememberSaveable { mutableIntStateOf(0) }

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
                        Text("Track+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    val tabs = listOf(
                        Icons.Rounded.VideoLibrary to tx(fa, "Visual", "تصویر"),
                        Icons.Rounded.Lyrics to tx(fa, "Lyrics AI", "متن هوشمند"),
                        Icons.Rounded.Recommend to tx(fa, "For you", "پیشنهادها")
                    )
                    items(tabs.size) { index ->
                        val item = tabs[index]
                        FilterChip(
                            selected = tab == index,
                            onClick = { tab = index },
                            leadingIcon = { Icon(item.first, null, Modifier.size(18.dp)) },
                            label = { Text(item.second) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(top = 6.dp))
                when (tab) {
                    0 -> VisualTrackTab(vm, fa)
                    1 -> OfflineLyricsTab(vm, fa)
                    else -> RecommendationTab(vm, fa)
                }
            }
        }
    }
}

@Composable
private fun VisualTrackTab(vm: TrackExperienceViewModel, fa: Boolean) {
    val context = LocalContext.current
    val song by vm.currentSong.collectAsState()
    val playback by vm.playback.collectAsState()
    val profile by vm.currentProfile.collectAsState()
    val waveform by vm.waveform.collectAsState()
    val waveformLoading by vm.waveformLoading.collectAsState()
    val settings by vm.settings.collectAsState()
    val current = profile ?: song?.let { TrackVisualProfileEntity(it.id) }
    var accentHex by rememberSaveable(song?.id, current?.accentArgb) {
        mutableStateOf(formatArgbHex(current?.accentArgb?.takeIf { it != 0 } ?: 0xFFFF6B35.toInt()))
    }
    var backgroundHex by rememberSaveable(song?.id, current?.backgroundArgb) {
        mutableStateOf(formatArgbHex(current?.backgroundArgb?.takeIf { it != 0 } ?: 0xFF101010.toInt()))
    }
    var secondaryHex by rememberSaveable(song?.id, current?.secondaryArgb) {
        mutableStateOf(formatArgbHex(current?.secondaryArgb?.takeIf { it != 0 } ?: 0xFFFFB08F.toInt()))
    }
    var hexError by rememberSaveable(song?.id) { mutableStateOf(false) }
    var canvasFailed by remember(current?.canvasUri) { mutableStateOf(false) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.persistUriPermission(it); vm.setArtwork(it.toString()) }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.persistUriPermission(it); vm.setCanvas(it.toString()) }
    }
    if (song == null || current == null) return
    LaunchedEffect(song!!.id) { vm.ensureWaveform(song!!) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(
                Modifier.fillMaxWidth().padding(14.dp).aspectRatio(9f / 14f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (current.canvasEnabled && !current.canvasUri.isNullOrBlank() && !canvasFailed) {
                    LocalCanvasVideo(
                        uri = current.canvasUri!!,
                        audioPositionMs = playback.positionMs,
                        playing = playback.playing,
                        fit = current.canvasFit,
                        startMs = current.canvasStartMs,
                        endMs = current.canvasEndMs,
                        playbackSpeed = current.canvasPlaybackSpeed,
                        modifier = Modifier.fillMaxSize(),
                        onError = { canvasFailed = true }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(song!!.artworkUri)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build(),
                        contentDescription = song!!.title,
                        contentScale = if (current.canvasFit == "fit") ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (current.visualizerMode != "off") {
                    TrackVisualizer(
                        waveform = waveform,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        mode = current.visualizerMode,
                        sensitivity = current.visualizerSensitivity,
                        animationIntensity = if (settings.reduceMotion) 0f else current.animationIntensity,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                    )
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(song!!.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song!!.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = playback.positionMs.toFloat().coerceAtLeast(0f),
                    onValueChange = { vm.seekTo(it.toLong()) },
                    valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(vm::previous) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(34.dp)) }
                    IconButton(vm::togglePlayback, Modifier.size(64.dp)) {
                        Icon(if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(46.dp))
                    }
                    IconButton(vm::next) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(34.dp)) }
                }
            }
        }
        item {
            TrackSection(tx(fa, "Per-song artwork & Canvas", "عکس و ویدیوی اختصاصی آهنگ")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ imageLauncher.launch(arrayOf("image/*")) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(4.dp)); Text(tx(fa, "Artwork", "عکس"))
                    }
                    OutlinedButton({ videoLauncher.launch(arrayOf("video/*")) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.VideoLibrary, null); Spacer(Modifier.width(4.dp)); Text("Canvas")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = current.canvasEnabled,
                        onClick = { vm.setCanvasEnabled(!current.canvasEnabled) },
                        label = { Text(tx(fa, "Canvas enabled", "ویدیو فعال")) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = current.canvasFit == "crop", onClick = { vm.setCanvasFit("crop") }, label = { Text(tx(fa, "Crop", "برش")) })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = current.canvasFit == "fit", onClick = { vm.setCanvasFit("fit") }, label = { Text(tx(fa, "Fit", "کامل")) })
                }
                if (canvasFailed) {
                    Text(tx(fa, "Canvas file is unavailable; artwork fallback is active.", "فایل Canvas در دسترس نیست؛ عکس آهنگ به‌صورت جایگزین نمایش داده می‌شود."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                TextButton({ vm.setCanvas(null); canvasFailed = false }) { Text(tx(fa, "Remove Canvas only", "حذف فقط ویدیو")) }
                TextButton({ vm.setArtwork(null) }) { Text(tx(fa, "Restore album artwork", "بازگردانی عکس آلبوم")) }
            }
        }
        item {
            TrackSection(tx(fa, "Per-song player theme", "تم اختصاصی هر آهنگ")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(vm::extractThemeFromArtwork, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Palette, null); Spacer(Modifier.width(4.dp)); Text(tx(fa, "From artwork", "از روی عکس"))
                    }
                    OutlinedButton(vm::resetTheme, Modifier.weight(1f)) { Text(tx(fa, "Use app theme", "تم برنامه")) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presets = listOf(
                        Triple(0xFFFF6B35.toInt(), 0xFF160A06.toInt(), 0xFFFFB08F.toInt()),
                        Triple(0xFF1DB954.toInt(), 0xFF06150C.toInt(), 0xFF8BE3A8.toInt()),
                        Triple(0xFF8B5CF6.toInt(), 0xFF11091F.toInt(), 0xFFC4A8FF.toInt()),
                        Triple(0xFF06B6D4.toInt(), 0xFF041519.toInt(), 0xFF8AE9F7.toInt()),
                        Triple(0xFFEC4899.toInt(), 0xFF1B0711.toInt(), 0xFFFFA6CF.toInt()),
                        Triple(0xFFFFC107.toInt(), 0xFF1D1602.toInt(), 0xFFFFE08A.toInt())
                    )
                    items(presets) { preset ->
                        val selected = current.themeMode == "custom" && current.accentArgb == preset.first
                        Box(
                            Modifier.size(if (selected) 46.dp else 42.dp).padding(if (selected) 2.dp else 0.dp)
                                .clip(CircleShape).background(Color(preset.first))
                                .clickable {
                                    accentHex = formatArgbHex(preset.first)
                                    backgroundHex = formatArgbHex(preset.second)
                                    secondaryHex = formatArgbHex(preset.third)
                                    hexError = false
                                    vm.setTheme("custom", preset.first, preset.second, preset.third)
                                }
                        )
                    }
                }
                Text(tx(fa, "Manual HEX (ARGB/RGB)", "رنگ دستی HEX (ARGB/RGB)"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = accentHex, onValueChange = { accentHex = it; hexError = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(tx(fa, "Accent #RRGGBB", "رنگ اصلی #RRGGBB")) }
                )
                OutlinedTextField(
                    value = backgroundHex, onValueChange = { backgroundHex = it; hexError = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(tx(fa, "Background #RRGGBB", "پس‌زمینه #RRGGBB")) }
                )
                OutlinedTextField(
                    value = secondaryHex, onValueChange = { secondaryHex = it; hexError = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(tx(fa, "Secondary #RRGGBB", "رنگ دوم #RRGGBB")) }
                )
                Button(
                    onClick = {
                        val a = parseArgbHex(accentHex)
                        val b = parseArgbHex(backgroundHex)
                        val c = parseArgbHex(secondaryHex)
                        if (a == null || b == null || c == null) hexError = true
                        else { hexError = false; vm.setTheme("custom", a, b, c) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tx(fa, "Apply exact colors", "اعمال دقیق رنگ‌ها")) }
                if (hexError) Text(tx(fa, "Invalid HEX color", "رنگ HEX نامعتبر است"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
        item {
            TrackSection(tx(fa, "Visualizer", "ویژوالایزر")) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("waveform", "bars", "pulse", "off")) { mode ->
                        FilterChip(
                            selected = current.visualizerMode == mode,
                            onClick = { vm.setVisualizerMode(mode) },
                            label = { Text(mode) }
                        )
                    }
                }
                Text(tx(fa, "Sensitivity", "حساسیت"), fontSize = 12.sp)
                Slider(current.visualizerSensitivity, vm::setVisualizerSensitivity, valueRange = .25f..3f)
                Text(tx(fa, "Animation intensity", "شدت انیمیشن"), fontSize = 12.sp)
                Slider(current.animationIntensity, vm::setAnimationIntensity, valueRange = 0f..2f)
                if (waveformLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton({ vm.rebuildWaveform() }) {
                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(4.dp)); Text(tx(fa, "Rebuild waveform", "ساخت دوباره موج"))
                }
            }
        }
        item {
            TextButton(vm::clearVisualProfile, Modifier.fillMaxWidth().padding(bottom = 40.dp)) {
                Text(tx(fa, "Reset all Track+ visual settings for this song", "بازنشانی همه تنظیمات تصویری این آهنگ"))
            }
        }
    }
}

@Composable
private fun OfflineLyricsTab(vm: TrackExperienceViewModel, fa: Boolean) {
    val song by vm.currentSong.collectAsState()
    val models by vm.speechModels.collectAsState()
    val selectedId by vm.selectedModelId.collectAsState()
    val transcript by vm.currentTranscript.collectAsState()
    val classicLyrics by vm.currentClassicLyrics.collectAsState()
    val forcedAlignmentStatus by vm.forcedAlignmentStatus.collectAsState()
    val state by vm.transcription.collectAsState()
    val playback by vm.playback.collectAsState()
    var modelMenu by remember { mutableStateOf(false) }
    var importName by rememberSaveable { mutableStateOf("") }
    var importLanguage by rememberSaveable { mutableStateOf("fa") }
    val modelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importSpeechModel(it, importName.ifBlank { "Imported model" }, importLanguage) }
    }
    if (song == null) return

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TrackSection(tx(fa, "100% on-device lyric generation", "تولید متن کاملاً روی دستگاه")) {
                Text(
                    tx(
                        fa,
                        "Bundled English and Persian mobile models work without internet. You can also import another compatible Vosk model ZIP. Audio never leaves the phone.",
                        "مدل‌های انگلیسی و فارسی موبایل داخل برنامه بدون اینترنت کار می‌کنند. مدل سازگار Vosk دیگری هم می‌توانی با ZIP وارد کنی. صدا از گوشی خارج نمی‌شود."
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Box {
                    OutlinedButton({ modelMenu = true }, Modifier.fillMaxWidth()) {
                        Text(
                            when (selectedId) {
                                OfflineLyricsTranscriber.BUNDLED_ENGLISH_MODEL_ID -> tx(fa, "Built-in English model", "مدل انگلیسی داخلی")
                                OfflineLyricsTranscriber.BUNDLED_PERSIAN_MODEL_ID -> tx(fa, "Built-in Persian model", "مدل فارسی داخلی")
                                else -> models.firstOrNull { it.id == selectedId }?.displayName ?: selectedId
                            }
                        )
                    }
                    DropdownMenu(modelMenu, { modelMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(tx(fa, "Built-in English • ~40 MB model", "انگلیسی داخلی • مدل حدود ۴۰ مگابایت")) },
                            onClick = { vm.chooseBuiltInEnglish(); modelMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(tx(fa, "Built-in Persian • ~53 MB model", "فارسی داخلی • مدل حدود ۵۳ مگابایت")) },
                            onClick = { vm.chooseBuiltInPersian(); modelMenu = false }
                        )
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text("${model.displayName} • ${model.language}") },
                                onClick = { vm.chooseSpeechModel(model); modelMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(importName, { importName = it }, Modifier.fillMaxWidth(), label = { Text(tx(fa, "Imported model name", "نام مدل واردشده")) }, singleLine = true)
                OutlinedTextField(importLanguage, { importLanguage = it }, Modifier.fillMaxWidth(), label = { Text(tx(fa, "Language code", "کد زبان")) }, singleLine = true)
                OutlinedButton({ modelLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text(tx(fa, "Import offline Vosk model ZIP", "ورود ZIP مدل آفلاین Vosk"))
                }
                if (models.isNotEmpty()) {
                    Text(tx(fa, "Imported models", "مدل‌های واردشده"), fontWeight = FontWeight.SemiBold)
                    models.forEach { model -> ImportedModelRow(model, selectedId == model.id, vm, fa) }
                }
            }
        }
        item {
            TrackSection(tx(fa, "Generate synchronized lyrics", "ساخت متن زمان‌بندی‌شده")) {
                if (state.running) {
                    LinearProgressIndicator(progress = { state.progress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${(state.progress.fraction * 100).toInt()}% • ${state.progress.partialText}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Button(vm::cancelTranscription, Modifier.fillMaxWidth()) { Text(tx(fa, "Stop", "توقف")) }
                } else {
                    Button(vm::generateLyricsOffline, Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text(tx(fa, "Generate from audio", "تولید از روی صدا"))
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                transcript?.let {
                    Text(
                        tx(fa, "Average confidence: ${(it.averageConfidence * 100).toInt()}%", "میانگین اطمینان: ${(it.averageConfidence * 100).toInt()}٪"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    FilledTonalButton(vm::applyGeneratedLyricsToClassic, Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lyrics, null); Spacer(Modifier.width(4.dp));
                        Text(tx(fa, "Use/replace in classic Lyrics", "استفاده/جایگزینی در متن اصلی"))
                    }
                    if (!classicLyrics?.original.isNullOrBlank()) {
                        OutlinedButton(vm::forceAlignExistingLyrics, Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.GraphicEq, null); Spacer(Modifier.width(4.dp))
                            Text(tx(fa, "Force-align existing lyrics to word timing", "هم‌تراز کردن متن فعلی با زمان کلمات"))
                        }
                        Text(
                            tx(fa, "This is explicit: it uses your existing lyric text and local Vosk timestamps, then writes synchronized LRC only when you press the button.", "این عملیات فقط با دستور خودت انجام می‌شود: متن فعلی را با زمان‌بندی محلی Vosk هماهنگ می‌کند و سپس LRC زمان‌بندی‌شده می‌سازد."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    }
                    forcedAlignmentStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                    Text(
                        tx(fa, "Manual/provider lyrics are never overwritten automatically.", "متن دستی یا متن سرویس هیچ‌وقت خودکار بازنویسی نمی‌شود."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    )
                    OutlinedButton(vm::clearGeneratedLyrics, Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(4.dp)); Text(tx(fa, "Clear AI transcript cache", "پاک‌کردن کش متن هوشمند"))
                    }
                }
            }
        }
        transcript?.let { value ->
            item { Text(tx(fa, "Word-by-word karaoke", "کارائوکه کلمه‌به‌کلمه"), Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            val words = remember(value.wordTimedJson) { parseUiWords(value.wordTimedJson) }
            val chunks = remember(words) { words.chunked(6) }
            val active = findCurrentWordIndex(words, playback.positionMs)
            items(chunks) { chunk -> KaraokeLine(chunk, active) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ImportedModelRow(model: OfflineSpeechModelEntity, selected: Boolean, vm: TrackExperienceViewModel, fa: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(model.displayName, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(model.language, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({ vm.deleteSpeechModel(model) }) { Icon(Icons.Rounded.Delete, tx(fa, "Delete model", "حذف مدل")) }
    }
}

private data class UiWord(val word: String, val startMs: Long, val endMs: Long, val index: Int)

private fun parseUiWords(json: String): List<UiWord> = runCatching {
    val array = JSONArray(json)
    List(array.length()) { i ->
        val item = array.getJSONObject(i)
        UiWord(item.optString("word"), item.optLong("startMs"), item.optLong("endMs"), i)
    }.filter { it.word.isNotBlank() }
}.getOrDefault(emptyList())

private fun findCurrentWordIndex(words: List<UiWord>, positionMs: Long): Int {
    if (words.isEmpty()) return -1
    var low = 0
    var high = words.lastIndex
    var candidate = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val word = words[mid]
        if (word.startMs <= positionMs) { candidate = word.index; low = mid + 1 } else high = mid - 1
    }
    return candidate
}

@Composable
private fun KaraokeLine(chunk: List<UiWord>, active: Int) {
    val text = buildAnnotatedString {
        chunk.forEachIndexed { index, word ->
            if (index > 0) append(" ")
            val start = length
            append(word.word)
            val end = length
            addStyle(
                SpanStyle(
                    color = if (word.index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (word.index == active) FontWeight.Black else FontWeight.Medium
                ),
                start,
                end
            )
        }
    }
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), fontSize = 18.sp, lineHeight = 25.sp)
}

@Composable
private fun RecommendationTab(vm: TrackExperienceViewModel, fa: Boolean) {
    val values by vm.recommendations.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TrackSection(tx(fa, "Local smart queue", "صف هوشمند محلی")) {
                Text(
                    tx(
                        fa,
                        "Uses artist, genre, year, BPM, favorites, play/skip history and local feedback. No server or account.",
                        "با هنرمند، ژانر، سال، BPM، علاقه‌مندی، سابقه پخش/ردکردن و بازخورد محلی کار می‌کند؛ بدون سرور و حساب."
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Button({ vm.queueRecommendations(12) }, Modifier.fillMaxWidth(), enabled = values.isNotEmpty()) {
                    Icon(Icons.Rounded.QueueMusic, null); Spacer(Modifier.width(5.dp)); Text(tx(fa, "Add top 12 to queue", "افزودن ۱۲ پیشنهاد به صف"))
                }
            }
        }
        items(values, key = { it.song.id }) { candidate -> RecommendationRow(candidate, vm, fa) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun RecommendationRow(value: LocalRecommendation, vm: TrackExperienceViewModel, fa: Boolean) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { vm.playRecommendation(value) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = value.song.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(value.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(value.song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(value.reasons.take(3).joinToString(" • "), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            }
            IconButton({ vm.boostRecommendation(value.song.id) }) { Icon(Icons.Rounded.ThumbUp, tx(fa, "More like this", "بیشتر مثل این")) }
            IconButton({ vm.dismissRecommendation(value.song.id) }) { Icon(Icons.Rounded.ThumbDown, tx(fa, "Less like this", "کمتر مثل این")) }
        }
    }
}

@Composable
private fun TrackSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun TrackVisualizer(
    waveform: WaveformData?,
    positionMs: Long,
    durationMs: Long,
    mode: String,
    sensitivity: Float,
    animationIntensity: Float,
    modifier: Modifier = Modifier
) {
    val pulse = if (animationIntensity > 0f) {
        val transition = rememberInfiniteTransition(label = "track-visualizer")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulse"
        )
        animated
    } else 0f
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.background
    val values = waveform?.amplitudes
    val sampled = remember(values, mode) {
        if (values == null || values.isEmpty()) emptyList()
        else {
            val maxBars = if (mode == "bars") 72 else 128
            val step = (values.size / maxBars).coerceAtLeast(1)
            values.indices.step(step).map { values[it] }.take(maxBars)
        }
    }
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = .62f))))) {
        if (mode == "pulse") {
            val radius = size.minDimension * (.16f + pulse * .18f * animationIntensity)
            drawCircle(primary.copy(alpha = .15f + pulse * .2f), radius, center)
            drawCircle(secondary.copy(alpha = .12f), radius * 1.45f, center)
            return@Canvas
        }
        if (sampled.isEmpty()) return@Canvas
        val barWidth = size.width / sampled.size.coerceAtLeast(1)
        sampled.forEachIndexed { index, raw ->
            val amp = (raw * sensitivity).coerceIn(.03f, 1f)
            val active = index.toFloat() / sampled.size.coerceAtLeast(1) <= progress
            val h = if (mode == "bars") size.height * amp else size.height * (.18f + amp * .62f)
            val x = index * barWidth + barWidth * .18f
            val color = if (active) primary else onSurface.copy(alpha = .28f)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth * .64f, h),
                cornerRadius = CornerRadius(barWidth * .3f)
            )
        }
    }
}

private fun blend(a: Color, b: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

private fun formatArgbHex(value: Int): String = "#%08X".format(value)

private fun parseArgbHex(raw: String): Int? = runCatching {
    val value = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    when (value.length) {
        6 -> (0xFF000000L or value.toLong(16)).toInt()
        8 -> value.toLong(16).toInt()
        else -> return@runCatching null
    }
}.getOrNull()

private fun tx(fa: Boolean, english: String, persian: String): String = if (fa) persian else english
