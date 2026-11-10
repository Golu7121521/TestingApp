package com.streamx.app.core.common

/**
 * Generic UI state used by every screen. Every screen must handle all four states
 * (loading, success, empty, error) per the master prompt.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val retryable: Boolean = true) : UiState<Nothing>
}
