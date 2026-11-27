package com.mytune.app.audio

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.mytune.app.data.repository.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Five UI bands (dB gains), bass and surround strength (0..1000). */
data class EqPreset(val name: String, val bands: List<Float>, val bass: Int, val surround: Int)

data class EqState(
    val enabled: Boolean = false,
    val preset: String = "Normal",
    val bands: List<Float> = List(5) { 0f }, // dB, -12..12
    val bass: Int = 0,
    val surround: Int = 0,
)

/**
 * App-wide equalizer on top of the system AudioEffect API, attached to the ExoPlayer audio session.
 * UI always shows five bands; devices with a different band count are mapped by frequency.
 */
class EqualizerManager(context: android.content.Context, private val settings: SettingsStore, private val scope: CoroutineScope) {
    companion object {
        const val MAX_DB = 12f
        val BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        private val BAND_HZ = listOf(60, 230, 910, 3600, 14000)

        val PRESETS = listOf(
            EqPreset("Normal", listOf(0f, 0f, 0f, 0f, 0f), 0, 0),
            EqPreset("Bass Boost", listOf(7f, 5f, 1f, 0f, 0f), 700, 0),
            EqPreset("Low Bass", listOf(-6f, -3f, 0f, 0f, 0f), 0, 0),
            EqPreset("Large Room", listOf(5f, 3f, 0f, 2f, 4f), 200, 600),
            EqPreset("Rock", listOf(5f, 3f, -2f, 3f, 5f), 300, 150),
            EqPreset("Dance", listOf(6f, 4f, 0f, 3f, 4f), 600, 250),
            EqPreset("Stadium", listOf(4f, 2f, 0f, 3f, 5f), 300, 800),
            EqPreset("Pop", listOf(-2f, 2f, 5f, 2f, -2f), 100, 100),
            EqPreset("Jazz", listOf(4f, 2f, -2f, 2f, 5f), 150, 100),
            EqPreset("Vocal", listOf(-3f, 0f, 5f, 4f, 1f), 0, 0),
            EqPreset("Classical", listOf(4f, 3f, -2f, 3f, 4f), 0, 200),
            EqPreset("Treble Boost", listOf(-2f, 0f, 1f, 5f, 8f), 0, 0),
        )
    }

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private val am = context.getSystemService(AudioManager::class.java)
    private val _headphones = MutableStateFlow(false)
    /** True while wired or wireless earphones are the audio output. The equalizer only works then. */
    val headphones: StateFlow<Boolean> = _headphones.asStateFlow()

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var session = 0
    private var loaded = false

    /** Called by PlaybackService whenever the player's audio session id is (re)known. */
    fun attach(sessionId: Int) {
        if (sessionId == 0) return
        scope.launch {
            if (!loaded) { load(); loaded = true }
            if (sessionId != session || eq == null) {
                release()
                session = sessionId
                eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
                bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
                virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
            }
            apply()
        }
    }

    fun release() {
        runCatching { eq?.release() }; runCatching { bass?.release() }; runCatching { virt?.release() }
        eq = null; bass = null; virt = null
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun setBand(i: Int, db: Float) = update { s ->
        s.copy(bands = s.bands.toMutableList().also { it[i] = db.coerceIn(-MAX_DB, MAX_DB) }, preset = "Custom")
    }
    fun setBass(v: Int) = update { it.copy(bass = v.coerceIn(0, 1000), preset = "Custom") }
    fun setSurround(v: Int) = update { it.copy(surround = v.coerceIn(0, 1000), preset = "Custom") }
    fun applyPreset(p: EqPreset) = update { it.copy(preset = p.name, bands = p.bands, bass = p.bass, surround = p.surround, enabled = true) }
    fun reset() = update { EqState(enabled = it.enabled) }

    private fun update(f: (EqState) -> EqState) {
        _state.value = f(_state.value)
        apply()
        save()
    }

    private fun checkHeadphones() {
        val types = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID,
            26, // TYPE_BLE_HEADSET (API 31)
            27, // TYPE_BLE_SPEAKER
            30, // TYPE_BLE_BROADCAST
        )
        _headphones.value = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types } }.getOrDefault(false)
        apply()
    }

    init {
        checkHeadphones()
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = checkHeadphones()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = checkHeadphones()
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private fun apply() {
        val s = _state.value.let { it.copy(enabled = it.enabled && _headphones.value) }
        runCatching {
            eq?.let { e ->
                e.enabled = s.enabled
                val range = e.bandLevelRange
                val n = e.numberOfBands.toInt()
                for (b in 0 until n) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000
                    // nearest UI band on a log-frequency scale
                    val idx = BAND_HZ.indices.minByOrNull { Math.abs(Math.log10(BAND_HZ[it].toDouble()) - Math.log10(hz.coerceAtLeast(1).toDouble())) } ?: 0
                    val mb = (s.bands[idx] * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
            }
        }
        runCatching { bass?.let { it.enabled = s.enabled && s.bass > 0; if (it.strengthSupported) it.setStrength(s.bass.toShort()) } }
        runCatching { virt?.let { it.enabled = s.enabled && s.surround > 0; if (it.strengthSupported) it.setStrength(s.surround.toShort()) } }
    }

    private fun save() {
        val s = _state.value
        val j = JSONObject().put("on", s.enabled).put("preset", s.preset).put("bass", s.bass).put("surround", s.surround)
            .put("bands", JSONArray().also { a -> s.bands.forEach { a.put(it.toDouble()) } })
        scope.launch { settings.saveEq(j.toString()) }
    }

    private suspend fun load() {
        val raw = settings.eq.first()
        if (raw.isBlank()) return
        runCatching {
            val j = JSONObject(raw)
            val a = j.getJSONArray("bands")
            _state.value = EqState(
                enabled = j.optBoolean("on"),
                preset = j.optString("preset", "Normal"),
                bands = List(5) { a.optDouble(it, 0.0).toFloat() },
                bass = j.optInt("bass"),
                surround = j.optInt("surround"),
            )
        }
    }

    /** Loads the saved state even before playback has started (so the dialog shows real values). */
    fun ensureLoaded() { scope.launch { if (!loaded) { load(); loaded = true } } }
}
