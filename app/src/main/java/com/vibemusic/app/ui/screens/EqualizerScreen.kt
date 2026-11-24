package com.vibemusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibemusic.app.presentation.VibeViewModel

@Composable
fun EqualizerScreen(
    viewModel: VibeViewModel,
    onBack: () -> Unit
) {
    val eqState by viewModel.audioEffectsState.collectAsState()
    val prefs by viewModel.preferences.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Equalizer & Audio FX",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = prefs.eqEnabled,
                onCheckedChange = { viewModel.toggleEqualizer(it) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!eqState.isEqSupported && !eqState.isBassBoostSupported && !eqState.isVirtualizerSupported) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "Start playing a track to initialize the device's hardware audio equalizer session.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
            return
        }

        if (eqState.isEqSupported && eqState.presets.isNotEmpty()) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(eqState.presets) { index, name ->
                    FilterChip(
                        selected = eqState.selectedPresetIndex == index,
                        onClick = { viewModel.selectEqPreset(index) },
                        enabled = prefs.eqEnabled,
                        label = { Text(name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${eqState.bands.size}-Band Hardware Equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    eqState.bands.forEach { band ->
                        val freqLabel = if (band.centerFreqHz >= 1000) {
                            "${band.centerFreqHz / 1000} kHz"
                        } else {
                            "${band.centerFreqHz} Hz"
                        }
                        val dbValue = band.levelMillibels / 100f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = band.levelMillibels.toFloat(),
                                onValueChange = { viewModel.setEqBandLevel(band.index, it.toInt()) },
                                valueRange = eqState.minBandLevel.toFloat()..eqState.maxBandLevel.toFloat(),
                                enabled = prefs.eqEnabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format("%+.1f dB", dbValue),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(58.dp)
                            )
                        }
                    }
                }
            }
        }

        if (eqState.isBassBoostSupported) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Boost", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${prefs.bassBoostStrength / 10}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = prefs.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = prefs.eqEnabled
                    )
                }
            }
        }

        if (eqState.isVirtualizerSupported) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("3D Virtualizer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${prefs.virtualizerStrength / 10}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = prefs.virtualizerStrength.toFloat(),
                        onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = prefs.eqEnabled
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}
