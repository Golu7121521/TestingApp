package com.playvid.presentation.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playvid.download.CompletedItem
import com.playvid.download.DownloadJob
import com.playvid.download.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    /** In-progress / paused / just-failed downloads. */
    val jobs: StateFlow<List<DownloadJob>> = DownloadRepository.jobs

    /** Finished files found on disk. null = still loading the first time. */
    private val _completed = MutableStateFlow<List<CompletedItem>?>(null)
    val completed: StateFlow<List<CompletedItem>?> = _completed

    init {
        refresh()
        // Re-scan whenever the active list shrinks (a job finished, failed, or was dismissed).
        viewModelScope.launch {
            var lastSize = jobs.value.size
            jobs.collect { list ->
                if (list.size < lastSize) refresh()
                lastSize = list.size
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { _completed.value = DownloadRepository.listCompleted(getApplication()) }
    }

    fun pause(id: Int) = DownloadRepository.pause(id)
    fun resume(id: Int) = DownloadRepository.resumeJob(id)
    fun cancel(id: Int) = DownloadRepository.cancel(id)
}
