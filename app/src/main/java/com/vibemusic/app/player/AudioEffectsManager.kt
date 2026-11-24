package com.vibemusic.app.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val levelMillibels: Int
)

data class HardwareAudioEffectsState(
    val isEqSupported: Boolean = false,
    val isBassBoostSupported: Boolean = false,
    val isVirtualizerSupported: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBandInfo> = emptyList(),
    val minBandLevel: Int = -1500,
    val maxBandLevel: Int = 1500,
    val presets: List<String> = emptyList(),
    val selectedPresetIndex: Int = 0,
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0
)

class AudioEffectsManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var attachedSessionId: Int = 0

    private val _state = MutableStateFlow(HardwareAudioEffectsState())
    val state: StateFlow<HardwareAudioEffectsState> = _state.asStateFlow()

    val fallbackPresets = listOf(
        "Normal", "Rock", "Pop", "Jazz", "Classical", "Dance", "Hip-Hop", "Custom"
    )

    fun attachToSession(
        audioSessionId: Int,
        enabled: Boolean,
        presetIndex: Int,
        savedBandLevels: List<Int>,
        bassStrength: Int,
        virtualizerStrength: Int
    ) {
        if (audioSessionId == 0) return
        if (attachedSessionId == audioSessionId && equalizer != null) {
            applySettings(enabled, presetIndex, savedBandLevels, bassStrength, virtualizerStrength)
            return
        }

        release()
        attachedSessionId = audioSessionId

        var eqSupported = false
        var bands = emptyList<EqBandInfo>()
        var minLvl = -1500
        var maxLvl = 1500
        var presetNames = fallbackPresets

        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            eqSupported = true
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            minLvl = range[0].toInt()
            maxLvl = range[1].toInt()

            val devicePresets = (0 until eq.numberOfPresets.toInt()).map { idx ->
                eq.getPresetName(idx.toShort())
            }
            presetNames = (devicePresets + "Custom").distinct()

            bands = (0 until numBands).map { b ->
                val freq = eq.getCenterFreq(b.toShort()) / 1000
                val lvl = eq.getBandLevel(b.toShort()).toInt()
                EqBandInfo(b, freq, lvl)
            }
        } catch (e: Exception) {
            equalizer = null
            eqSupported = false
        }

        var bassSupported = false
        try {
            val bb = BassBoost(0, audioSessionId)
            if (bb.strengthSupported) {
                bassBoost = bb
                bassSupported = true
            } else {
                bb.release()
            }
        } catch (_: Exception) {
            bassBoost = null
        }

        var virtSupported = false
        try {
            val virt = Virtualizer(0, audioSessionId)
            if (virt.strengthSupported) {
                virtualizer = virt
                virtSupported = true
            } else {
                virt.release()
            }
        } catch (_: Exception) {
            virtualizer = null
        }

        _state.value = HardwareAudioEffectsState(
            isEqSupported = eqSupported,
            isBassBoostSupported = bassSupported,
            isVirtualizerSupported = virtSupported,
            enabled = enabled,
            bands = bands,
            minBandLevel = minLvl,
            maxBandLevel = maxLvl,
            presets = presetNames,
            selectedPresetIndex = presetIndex.coerceIn(0, (presetNames.size - 1).coerceAtLeast(0)),
            bassBoostStrength = bassStrength,
            virtualizerStrength = virtualizerStrength
        )

        applySettings(enabled, presetIndex, savedBandLevels, bassStrength, virtualizerStrength)
    }

    fun applySettings(
        enabled: Boolean,
        presetIndex: Int,
        savedBandLevels: List<Int>,
        bassStrength: Int,
        virtualizerStrength: Int
    ) {
        try {
            equalizer?.let { eq ->
                eq.enabled = enabled
                val numBands = eq.numberOfBands.toInt()
                val numDevicePresets = eq.numberOfPresets.toInt()
                if (presetIndex in 0 until numDevicePresets && savedBandLevels.isEmpty()) {
                    eq.usePreset(presetIndex.toShort())
                } else if (savedBandLevels.size == numBands) {
                    savedBandLevels.forEachIndexed { idx, lvl ->
                        val clamped = lvl.coerceIn(_state.value.minBandLevel, _state.value.maxBandLevel)
                        eq.setBandLevel(idx.toShort(), clamped.toShort())
                    }
                }
                val updatedBands = (0 until numBands).map { b ->
                    EqBandInfo(
                        index = b,
                        centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000,
                        levelMillibels = eq.getBandLevel(b.toShort()).toInt()
                    )
                }
                _state.value = _state.value.copy(
                    enabled = enabled,
                    bands = updatedBands,
                    selectedPresetIndex = presetIndex
                )
            }
        } catch (_: Exception) {
        }

        try {
            bassBoost?.let { bb ->
                bb.enabled = enabled && bassStrength > 0
                if (bb.strengthSupported) {
                    bb.setStrength(bassStrength.coerceIn(0, 1000).toShort())
                }
                _state.value = _state.value.copy(bassBoostStrength = bassStrength)
            }
        } catch (_: Exception) {
        }

        try {
            virtualizer?.let { virt ->
                virt.enabled = enabled && virtualizerStrength > 0
                if (virt.strengthSupported) {
                    virt.setStrength(virtualizerStrength.coerceIn(0, 1000).toShort())
                }
                _state.value = _state.value.copy(virtualizerStrength = virtualizerStrength)
            }
        } catch (_: Exception) {
        }
    }

    fun setBandLevel(bandIndex: Int, levelMillibels: Int): List<Int> {
        val eq = equalizer ?: return emptyList()
        return try {
            val clamped = levelMillibels.coerceIn(_state.value.minBandLevel, _state.value.maxBandLevel)
            eq.setBandLevel(bandIndex.toShort(), clamped.toShort())
            val numBands = eq.numberOfBands.toInt()
            val updatedBands = (0 until numBands).map { b ->
                EqBandInfo(
                    index = b,
                    centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000,
                    levelMillibels = eq.getBandLevel(b.toShort()).toInt()
                )
            }
            val customIdx = (_state.value.presets.size - 1).coerceAtLeast(0)
            _state.value = _state.value.copy(
                bands = updatedBands,
                selectedPresetIndex = customIdx
            )
            updatedBands.map { it.levelMillibels }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun selectPreset(presetIndex: Int): List<Int> {
        val eq = equalizer ?: return emptyList()
        return try {
            val numDevicePresets = eq.numberOfPresets.toInt()
            if (presetIndex in 0 until numDevicePresets) {
                eq.usePreset(presetIndex.toShort())
            }
            val numBands = eq.numberOfBands.toInt()
            val updatedBands = (0 until numBands).map { b ->
                EqBandInfo(
                    index = b,
                    centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000,
                    levelMillibels = eq.getBandLevel(b.toShort()).toInt()
                )
            }
            _state.value = _state.value.copy(
                bands = updatedBands,
                selectedPresetIndex = presetIndex
            )
            updatedBands.map { it.levelMillibels }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        attachedSessionId = 0
    }
}
