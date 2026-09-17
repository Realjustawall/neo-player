package com.neoplayer.app.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.neoplayer.app.data.TrackAudioEffectsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


data class AudioEffectsState(
    val available: Boolean = false,
    val preset: String = "Normal",
    val bass: Int = 0,
    val virtualizer: Int = 0,
    val loudnessMb: Int = 0,
    val bandLevels: List<Short> = emptyList()
)

/**
 * Owns platform audio effects for the active ExoPlayer audio session.
 * Individual effects are best-effort because vendor implementations vary widely.
 */
class AudioEffectsEngine {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizerFx: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var attachedSessionId: Int = -1
    private val _state = MutableStateFlow(AudioEffectsState())
    val state = _state.asStateFlow()

    val presets = listOf("Normal", "Bass Boost", "Rock", "Pop", "Classical", "Jazz", "Electronic", "Vocal", "Custom")

    fun snapshot(songId: Long) = TrackAudioEffectsEntity(
        songId = songId,
        preset = _state.value.preset,
        bass = _state.value.bass,
        virtualizer = _state.value.virtualizer,
        loudnessMb = _state.value.loudnessMb,
        bandLevels = _state.value.bandLevels.joinToString(",")
    )

    fun applyProfile(profile: TrackAudioEffectsEntity) {
        val bands = profile.bandLevels.split(",").mapNotNull { it.toShortOrNull() }
        if (profile.preset == "Custom" && bands.isNotEmpty()) {
            _state.value = _state.value.copy(preset = "Custom")
            bands.forEachIndexed(::setBand)
        } else {
            applyPreset(profile.preset)
        }
        setBass(profile.bass)
        setVirtualizer(profile.virtualizer)
        setLoudness(profile.loudnessMb)
    }

    fun resetForTrack() {
        applyPreset("Normal")
        setBass(0)
        setVirtualizer(0)
        setLoudness(0)
    }

    /**
     * Attaches only when the audio session actually changes. Recreating effects for every
     * STATE_READY transition causes audible glitches and unnecessary vendor-audio work.
     */
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedSessionId) return
        val desired = _state.value
        releaseHardware()
        attachedSessionId = audioSessionId

        equalizer = runCatching { Equalizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId).apply { enabled = true } }.getOrNull()
        virtualizerFx = runCatching { Virtualizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(audioSessionId).apply { enabled = true } }.getOrNull()

        val eqBandCount = equalizer?.numberOfBands?.toInt() ?: 0
        val restoredBands = when {
            eqBandCount == 0 -> desired.bandLevels
            desired.bandLevels.size == eqBandCount -> desired.bandLevels
            else -> List(eqBandCount) { index -> desired.bandLevels.getOrElse(index) { 0 } }
        }
        _state.value = desired.copy(
            available = listOf(equalizer, bassBoost, virtualizerFx, loudness).any { it != null },
            bandLevels = restoredBands
        )

        if (desired.preset == "Custom" && restoredBands.isNotEmpty()) {
            restoredBands.forEachIndexed(::setBand)
        } else {
            applyPreset(desired.preset)
        }
        setBass(desired.bass)
        setVirtualizer(desired.virtualizer)
        setLoudness(desired.loudnessMb)
    }

    fun applyPreset(name: String) {
        val safeName = name.takeIf { it in presets } ?: "Normal"
        val eq = equalizer
        if (eq == null) {
            _state.value = _state.value.copy(preset = safeName)
            return
        }

        val bandCount = eq.numberOfBands.toInt()
        val shape = when (safeName) {
            "Bass Boost" -> listOf(1f, .8f, .35f, 0f, 0f, 0f, .1f, .2f)
            "Rock" -> listOf(.7f, .45f, -.15f, -.3f, .05f, .4f, .65f, .7f)
            "Pop" -> listOf(-.1f, .15f, .4f, .55f, .35f, .1f, -.1f, -.15f)
            "Classical" -> listOf(.45f, .35f, .05f, -.1f, -.05f, .25f, .5f, .65f)
            "Jazz" -> listOf(.45f, .25f, -.05f, .2f, .35f, .25f, .4f, .55f)
            "Electronic" -> listOf(.65f, .5f, 0f, -.25f, .1f, .35f, .55f, .65f)
            "Vocal" -> listOf(-.3f, -.2f, .05f, .55f, .7f, .55f, .1f, -.15f)
            "Custom" -> return
            else -> List(8) { 0f }
        }
        val range = eq.bandLevelRange
        val amplitude = minOf(-range[0].toInt(), range[1].toInt())
        val levels = List(bandCount) { index ->
            val source = if (bandCount <= 1) 0 else index * shape.lastIndex / (bandCount - 1)
            (shape[source] * amplitude).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
        }
        levels.forEachIndexed { index, level -> runCatching { eq.setBandLevel(index.toShort(), level) } }
        _state.value = _state.value.copy(preset = safeName, bandLevels = levels)
    }

    fun setBand(index: Int, level: Short) {
        val currentBands = _state.value.bandLevels
        if (index !in currentBands.indices) return
        val eq = equalizer
        val safe = if (eq != null) {
            val range = eq.bandLevelRange
            level.coerceIn(range[0], range[1])
        } else {
            level
        }
        if (eq != null) runCatching { eq.setBandLevel(index.toShort(), safe) }
        _state.value = _state.value.copy(
            preset = "Custom",
            bandLevels = currentBands.toMutableList().apply { this[index] = safe }
        )
    }

    fun setBass(strength: Int) {
        val safe = strength.coerceIn(0, 1000)
        runCatching { bassBoost?.setStrength(safe.toShort()) }
        _state.value = _state.value.copy(bass = safe)
    }

    fun setVirtualizer(strength: Int) {
        val safe = strength.coerceIn(0, 1000)
        runCatching { virtualizerFx?.setStrength(safe.toShort()) }
        _state.value = _state.value.copy(virtualizer = safe)
    }

    fun setLoudness(gainMb: Int) {
        val safe = gainMb.coerceIn(0, 1200)
        runCatching { loudness?.setTargetGain(safe) }
        _state.value = _state.value.copy(loudnessMb = safe)
    }

    private fun releaseHardware() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizerFx?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        virtualizerFx = null
        loudness = null
        attachedSessionId = -1
    }

    fun release() {
        releaseHardware()
        _state.value = AudioEffectsState()
    }
}
