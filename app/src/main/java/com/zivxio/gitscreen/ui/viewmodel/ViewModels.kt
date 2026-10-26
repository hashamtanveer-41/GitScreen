package com.zivxio.gitscreen.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zivxio.gitscreen.data.db.AppEntity
import com.zivxio.gitscreen.data.db.ScreenshotEntity
import com.zivxio.gitscreen.data.repository.AppSummary
import com.zivxio.gitscreen.data.repository.ScreenshotRepository
import com.zivxio.gitscreen.data.repository.VersionSummary
import com.zivxio.gitscreen.detection.ScreenshotOrganizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dependencies are wired by hand for the MVP (no Hilt yet), so every ViewModel
 * gets a small dedicated [ViewModelProvider.Factory] rather than one shared
 * factory trying to branch on every possible constructor.
 */

class HomeViewModel(private val repository: ScreenshotRepository) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppSummary>>(emptyList())
    val apps: StateFlow<List<AppSummary>> = _apps.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAppSummaries().collectLatest { _apps.value = it }
        }
    }

    fun deleteApp(appId: Long) {
        viewModelScope.launch {
            repository.deleteApp(appId)
        }
    }

    class Factory(private val repository: ScreenshotRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
    }
}

class AppDetailViewModel(
    private val repository: ScreenshotRepository,
    val appId: Long,
    val appName: String
) : ViewModel() {

    private val _versions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val versions: StateFlow<List<VersionSummary>> = _versions.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeVersionSummaries(appId).collectLatest { _versions.value = it }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _versions.value = repository.getVersionSummaries(appId)
        }
    }

    fun deleteVersion(versionId: Long) {
        viewModelScope.launch {
            repository.deleteVersion(versionId)
        }
    }

    class Factory(
        private val repository: ScreenshotRepository,
        private val appId: Long,
        private val appName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppDetailViewModel(repository, appId, appName) as T
    }
}

class VersionDetailViewModel(
    private val repository: ScreenshotRepository,
    val versionId: Long,
    val versionLabel: String
) : ViewModel() {

    private val _screenshots = MutableStateFlow<List<ScreenshotEntity>>(emptyList())
    val screenshots: StateFlow<List<ScreenshotEntity>> = _screenshots.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeScreenshots(versionId).collectLatest { _screenshots.value = it }
        }
    }

    fun deleteScreenshot(screenshotId: Long) {
        viewModelScope.launch {
            repository.deleteScreenshot(screenshotId)
        }
    }

    class Factory(
        private val repository: ScreenshotRepository,
        private val versionId: Long,
        private val versionLabel: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VersionDetailViewModel(repository, versionId, versionLabel) as T
    }
}

/**
 * Backs the manual-tag fallback screen shown when auto-detection fails.
 * Lets the user pick an existing app or type a new one, then a version label,
 * and saves the screenshot the same way the automatic path would.
 */
class ManualTagViewModel(
    private val repository: ScreenshotRepository,
    private val organizer: ScreenshotOrganizer,
    val screenshotUri: Uri,
    val takenAt: Long,
    val initialAppName: String = ""
) : ViewModel() {

    private val _existingApps = MutableStateFlow<List<AppEntity>>(emptyList())
    val existingApps: StateFlow<List<AppEntity>> = _existingApps.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        object Saved : SaveState()
        // GitScreen's background detection can succeed a moment after this
        // notification was shown (see the retry logic in ScreenshotWatcherService).
        // If that already happened, tell the user instead of filing a duplicate.
        object AlreadySaved : SaveState()
        data class Error(val message: String) : SaveState()
    }

    init {
        viewModelScope.launch {
            repository.observeApps().collectLatest { _existingApps.value = it }
        }
    }

    fun save(appName: String, versionLabel: String) {
        if (appName.isBlank() || versionLabel.isBlank()) {
            _saveState.value = SaveState.Error("Please fill in both fields")
            return
        }
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                if (repository.findByOriginalUri(screenshotUri.toString()) != null) {
                    _saveState.value = SaveState.AlreadySaved
                    return@launch
                }
                val existing = _existingApps.value.firstOrNull { it.displayName.equals(appName, ignoreCase = true) }
                val organizedUri = organizer.organize(screenshotUri, appName, versionLabel, takenAt)
                if (organizedUri == null) {
                    _saveState.value = SaveState.Error("Couldn't copy the screenshot file")
                    return@launch
                }
                val appEntity = repository.getOrCreateApp(appName, existing?.packageName)
                val versionEntity = repository.getOrCreateVersion(appEntity.id, versionLabel, null)
                repository.recordScreenshot(
                    versionId = versionEntity.id,
                    mediaUri = organizedUri.toString(),
                    originalUri = screenshotUri.toString(),
                    takenAt = takenAt,
                    wasAutoDetected = false
                )
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    class Factory(
        private val repository: ScreenshotRepository,
        private val organizer: ScreenshotOrganizer,
        private val screenshotUri: Uri,
        private val takenAt: Long,
        private val initialAppName: String = ""
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ManualTagViewModel(repository, organizer, screenshotUri, takenAt, initialAppName) as T
    }
}
