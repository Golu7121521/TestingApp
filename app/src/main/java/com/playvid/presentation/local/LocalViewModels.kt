package com.playvid.presentation.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The "Local" tab's first screen: every device folder that has at least one video. */
class LocalFoldersViewModel(app: Application) : AndroidViewModel(app) {
    private val _folders = MutableStateFlow<List<LocalFolder>?>(null) // null = still loading
    val folders: StateFlow<List<LocalFolder>?> = _folders

    fun load() {
        viewModelScope.launch { _folders.value = LocalVideoScanner.scanFolders(getApplication()) }
    }
}

/** The videos inside one folder, opened from [LocalFoldersViewModel]. */
class LocalFolderViewModel(app: Application, private val folderId: Long) : AndroidViewModel(app) {
    private val _videos = MutableStateFlow<List<LocalVideo>?>(null)
    val videos: StateFlow<List<LocalVideo>?> = _videos

    init {
        viewModelScope.launch { _videos.value = LocalVideoScanner.scanFolder(getApplication(), folderId) }
    }
}
