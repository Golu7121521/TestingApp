package com.streamx.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.core.common.UiState
import com.streamx.app.domain.model.Video
import com.streamx.app.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Video>>> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(HomeCategories.first())
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        load()
    }

    fun onCategorySelected(category: String) {
        if (category == _selectedCategory.value) return
        _selectedCategory.value = category
        load()
    }

    fun refresh() {
        _isRefreshing.value = true
        load()
    }

    private fun load() {
        viewModelScope.launch {
            if (!_isRefreshing.value) _uiState.value = UiState.Loading
            try {
                val videos = videoRepository.getFeed(_selectedCategory.value)
                _uiState.value = if (videos.isEmpty()) UiState.Empty else UiState.Success(videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Could not load the feed.")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
