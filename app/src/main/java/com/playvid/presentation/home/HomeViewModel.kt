package com.playvid.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playvid.domain.model.HistoryItem
import com.playvid.domain.model.PlaybackRequest
import com.playvid.domain.repository.HistoryRepository
import com.playvid.domain.usecase.UrlValidation
import com.playvid.domain.usecase.ValidateStreamUrlUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val input: String = "",
    val error: String? = null,
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val historyRepo: HistoryRepository,
    private val validate: ValidateStreamUrlUseCase
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    /** null = still loading, empty = no history. */
    val recent: StateFlow<List<HistoryItem>?> = historyRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onInputChange(value: String) {
        _ui.update { it.copy(input = value, error = null) }
    }

    fun clearInput() = onInputChange("")

    fun removeFromHistory(id: Long) {
        viewModelScope.launch { historyRepo.delete(id) }
    }

    /** Validates the URL; opens the player only for a plausible video link. */
    fun play(onValid: (PlaybackRequest) -> Unit) {
        if (_ui.value.isLoading) return
        when (val result = validate(_ui.value.input)) {
            is UrlValidation.Invalid -> _ui.update { it.copy(error = result.message) }
            is UrlValidation.Valid -> viewModelScope.launch {
                _ui.update { it.copy(isLoading = true, error = null) }
                onValid(PlaybackRequest(source = result.url, title = result.title, isLocal = false))
                delay(600) // keep the "Loading..." state until the player screen has taken over
                _ui.update { it.copy(isLoading = false) }
            }
        }
    }
}
