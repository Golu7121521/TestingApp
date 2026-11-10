package com.playvid.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playvid.PlayVidApp
import com.playvid.presentation.local.LocalFoldersViewModel
import com.playvid.presentation.home.HomeViewModel
import com.playvid.presentation.playlist.PlaylistsViewModel
import com.playvid.presentation.settings.SettingsViewModel
import com.playvid.presentation.server.ServerViewModel

/** Gives ViewModel factories access to the Application (and its AppContainer). */
fun CreationExtras.app(): PlayVidApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PlayVidApp

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(app().container.historyRepository, app().container.validateUrl) }
        initializer { ServerViewModel() } // Naya ServerViewModel add kiya gaya hai
        initializer { SettingsViewModel(app().container.settingsRepository, app().container.historyRepository) }
        initializer { LocalFoldersViewModel(app()) }
        initializer { PlaylistsViewModel(app(), app().container.playlistRepository) }
    }
}
