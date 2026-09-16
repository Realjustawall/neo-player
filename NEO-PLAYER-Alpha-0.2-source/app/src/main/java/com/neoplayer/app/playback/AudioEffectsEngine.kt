package com.neoplayer.app.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
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

class AudioEffectsEngine {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizerFx: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private val _state = MutableStateFlow(AudioEffectsState())
    val state = _state.asStateFlow()

    val presets = listOf("Normal", "Bass Boost", "Rock", "Pop", "Classical", "Jazz", "Electronic", "Vocal", "Custom")

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            virtualizerFx = Virtualizer(0, audioSessionId).apply { enabled = true }
            loudness = LoudnessEnhancer(audioSessionId).apply { enabled = true }
            _state.value = AudioEffectsState(available = true, bandLevels = List(equalizer!!.numberOfBands.toInt()) { 0 })
        }.onFailure { release() }
    }

    fun applyPreset(name: String) {
        val eq = equalizer ?: return
        val bandCount = eq.numberOfBands.toInt()
        val shape = when (name) {
            "Bass Boost" -> listOf(1f, .8f, .35f, 0f, 0f, 0f, .1f, .2f)
            "Rock" -> listOf(.7f, .45f, -.15f, -.3f, .05f, .4f, .65f, .7f)
            "Pop" -> listOf(-.1f, .15f, .4f, .55f, .35f, .1f, -.1f, -.15f)
            "Classical" -> listOf(.45f, .35f, .05f, -.1f, -.05f, .25f, .5f, .65f)
            "Jazz" -> listOf(.45f, .25f, -.05f, .2f, .35f, .25f, .4f, .55f)
            "Electronic" -> listOf(.65f, .5f, 0f, -.25f, .1f, .35f, .55f, .65f)
            "Vocal" -> listOf(-.3f, -.2f, .05f, .55f, .7f, .55f, .1f, -.15f)
            else -> List(8) { 0f }
        }
        val range = eq.bandLevelRange
        val amplitude = minOf(-range[0].toInt(), range[1].toInt())
        val levels = List(bandCount) { index ->
            val source = if (bandCount == 1) 0 else (index * (shape.lastIndex) / (bandCount - 1))
            (shape[source] * amplitude).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
        }
        levels.forEachIndexed { index, level -> runCatching { eq.setBandLevel(index.toShort(), level) } }
        _state.value = _state.value.copy(preset = name, bandLevels = levels)
    }

    fun setBand(index: Int, level: Short) {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        val safe = level.coerceIn(range[0], range[1])
        runCatching { eq.setBandLevel(index.toShort(), safe) }
        _state.value = _state.value.copy(preset = "Custom", bandLevels = _state.value.bandLevels.toMutableList().apply { if (index in indices) this[index] = safe })
    }

    fun setBass(strength: Int) { val safe = strength.coerceIn(0, 1000); runCatching { bassBoost?.setStrength(safe.toShort()) }; _state.value = _state.value.copy(bass = safe) }
    fun setVirtualizer(strength: Int) { val safe = strength.coerceIn(0, 1000); runCatching { virtualizerFx?.setStrength(safe.toShort()) }; _state.value = _state.value.copy(virtualizer = safe) }
    fun setLoudness(gainMb: Int) { val safe = gainMb.coerceIn(0, 1200); runCatching { loudness?.setTargetGain(safe) }; _state.value = _state.value.copy(loudnessMb = safe) }

    fun release() {
        runCatching { equalizer?.release() }; runCatching { bassBoost?.release() }; runCatching { virtualizerFx?.release() }; runCatching { loudness?.release() }
        equalizer = null; bassBoost = null; virtualizerFx = null; loudness = null
        _state.value = AudioEffectsState()
    }
}
