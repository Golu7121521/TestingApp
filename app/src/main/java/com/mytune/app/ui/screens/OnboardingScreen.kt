package com.mytune.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mytune.app.di.AppContainer
import com.mytune.app.domain.Catalog
import com.mytune.app.domain.PickedArtist
import com.mytune.app.ui.components.Artwork
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MIN_LANGS = 3
private const val MIN_ARTISTS = 3

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    val country = MutableStateFlow("India")
    val languages = MutableStateFlow<List<String>>(emptyList())
    val picked = MutableStateFlow<List<PickedArtist>>(emptyList())
    /** Artists offered for the chosen languages (name -> resolved artist, null while unresolved / not found). */
    val offered = MutableStateFlow<List<PickedArtist>>(emptyList())
    val loading = MutableStateFlow(false)
    val saving = MutableStateFlow(false)

    private val cache = HashMap<String, PickedArtist?>()

    init {
        viewModelScope.launch {
            // Pre-fill when the user re-opens the page from Settings.
            val p = c.settings.prefsNow()
            if (p.languages.isNotEmpty()) {
                country.value = p.country
                picked.value = p.artists
                setLanguages(p.languages)
            }
        }
    }

    fun toggleLanguage(l: String) {
        val cur = languages.value
        setLanguages(if (l in cur) cur - l else cur + l)
    }

    private fun setLanguages(l: List<String>) {
        languages.value = l
        loadArtists()
    }

    private var job: kotlinx.coroutines.Job? = null
    private fun loadArtists() {
        job?.cancel()
        val names = Catalog.artistsFor(languages.value).take(36)
        if (names.isEmpty()) { offered.value = emptyList(); return }
        job = viewModelScope.launch {
            loading.value = true
            val res = coroutineScope {
                names.map { n ->
                    async {
                        if (!cache.containsKey(n)) {
                            cache[n] = c.repository.searchArtists(n, 1).getOrNull()?.firstOrNull()
                                ?.let { PickedArtist(it.id, it.title.ifBlank { n }, it.artworkUrl) }
                        }
                        cache[n]
                    }
                }.mapNotNull { it.await() }
            }
            // keep already picked artists visible even if their language was unchecked
            offered.value = (res + picked.value).distinctBy { it.id }
            loading.value = false
        }
    }

    fun toggleArtist(a: PickedArtist) {
        picked.value = if (picked.value.any { it.id == a.id }) picked.value.filter { it.id != a.id } else picked.value + a
    }

    fun finish() {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch { c.settings.saveOnboarding(country.value, languages.value, picked.value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(c: AppContainer) {
    val vmKey = remember { "onboarding_" + System.nanoTime() }
    val vm: OnboardingViewModel = viewModel(key = vmKey, factory = viewModelFactory { initializer { OnboardingViewModel(c) } })
    val country by vm.country.collectAsState()
    val langs by vm.languages.collectAsState()
    val picked by vm.picked.collectAsState()
    val offered by vm.offered.collectAsState()
    val loading by vm.loading.collectAsState()
    val saving by vm.saving.collectAsState()
    val ready = langs.size >= MIN_LANGS && picked.size >= MIN_ARTISTS
    var countryMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), MaterialTheme.colorScheme.background)))) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp)) {
            item {
                Text("Welcome to MyTune", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Tell us what you love and we will tune your home feed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Text("Country", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Box {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { countryMenu = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(country, Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                        Catalog.countries.forEach { cn -> DropdownMenuItem(text = { Text(cn) }, onClick = { vm.country.value = cn; countryMenu = false }) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Song languages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${langs.size}/$MIN_LANGS+", color = if (langs.size >= MIN_LANGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Text("Choose at least $MIN_LANGS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Catalog.languages.forEach { l ->
                        FilterChip(
                            selected = l in langs, onClick = { vm.toggleLanguage(l) }, label = { Text(l) },
                            leadingIcon = if (l in langs) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favourite artists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${picked.size}/$MIN_ARTISTS+", color = if (picked.size >= MIN_ARTISTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (langs.isEmpty()) "Pick your languages first, artists will appear here" else "Choose at least $MIN_ARTISTS",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (loading && offered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            // Artist grid as rows of 3 (a LazyVerticalGrid cannot be nested inside a LazyColumn)
            val rows = offered.chunked(3)
            items(rows.size) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rows[r].forEach { a ->
                        val sel = picked.any { it.id == a.id }
                        Column(Modifier.weight(1f).clickable { vm.toggleArtist(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Artwork(
                                    a.image,
                                    Modifier.size(92.dp).border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    CircleShape,
                                )
                                if (sel) Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    repeat(3 - rows[r].size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Continue button
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
                .navigationBarsPadding().padding(20.dp),
        ) {
            Button(
                onClick = { vm.finish() }, enabled = ready && !saving,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(27.dp),
            ) {
                Text(
                    if (ready) "Continue" else if (langs.size < MIN_LANGS) "Select ${MIN_LANGS - langs.size} more language(s)" else "Select ${MIN_ARTISTS - picked.size} more artist(s)",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
