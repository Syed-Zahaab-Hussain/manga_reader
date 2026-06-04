package com.example.mangareader.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.core.io.FolderAccess
import com.example.mangareader.data.backup.BackupRepository
import com.example.mangareader.data.backup.ImportResult
import com.example.mangareader.data.backup.RejectionReason
import com.example.mangareader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class CacheClearTarget(val label: String) {
    THUMBNAILS("thumbnail cache"),
    EXTRACTED_CHAPTERS("extracted chapters"),
    ALL("all storage caches")
}

data class SettingsUiState(
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val storageAccessGranted: Boolean = false,
    val hasPin: Boolean = false,
    val biometricEnabled: Boolean = false,
    val folderPath: String? = null,
    val folderAvailable: Boolean = false,
    val thumbnailCacheBytes: Long = 0L,
    val extractedCacheBytes: Long = 0L,
    val pendingImportUri: Uri? = null,
    val pendingCacheClear: CacheClearTarget? = null,
    val feedbackMessage: String? = null
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val preferencesRepository = container.preferencesRepository
    private val libraryCacheRepository = container.libraryCacheRepository
    private val thumbnailCache = container.thumbnailCache
    private val archiveExtractCache = container.archiveExtractCache
    private val backupRepository = container.backupRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun recheckStorageAccess() {
        _uiState.update {
            it.copy(storageAccessGranted = FolderAccess.hasStorageAccess(container.appContext))
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (!_uiState.value.hasPin) return
        container.pinRepository.biometricEnabled = enabled
        _uiState.update {
            it.copy(
                biometricEnabled = enabled,
                feedbackMessage = if (enabled) {
                    "Fingerprint login enabled."
                } else {
                    "Fingerprint login disabled."
                }
            )
        }
    }

    fun onFolderPicked(uri: Uri) {
        val folder = FolderAccess.treeUriToFolder(container.appContext, uri)
        if (folder == null || !folder.isDirectory) {
            val detail = FolderAccess.describeTreeUri(uri)
            _uiState.update {
                it.copy(
                    feedbackMessage = "Could not open that folder. Choose a folder on internal storage or an SD card. ($detail)"
                )
            }
            return
        }

        runOperation {
            preferencesRepository.setMangaFolder(folder.absolutePath)
            libraryCacheRepository.clear()
            _uiState.update {
                it.copy(
                    folderPath = folder.absolutePath,
                    folderAvailable = true,
                    storageAccessGranted = true
                )
            }
            "Manga folder updated. The Library will rescan when you return."
        }
    }

    fun exportBackup(uri: Uri) {
        runOperation {
            val output = container.appContext.contentResolver.openOutputStream(uri)
                ?: error("Could not open the selected backup destination.")
            backupRepository.export(output, currentMangaRoot())
            "Backup exported successfully."
        }
    }

    fun prepareImport(uri: Uri) {
        if (!_uiState.value.folderAvailable) {
            _uiState.update {
                it.copy(feedbackMessage = "Select an available manga folder before importing progress.")
            }
            return
        }
        _uiState.update { it.copy(pendingImportUri = uri) }
    }

    fun cancelImport() {
        _uiState.update { it.copy(pendingImportUri = null) }
    }

    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        _uiState.update { it.copy(pendingImportUri = null) }
        runOperation {
            val input = container.appContext.contentResolver.openInputStream(uri)
                ?: error("Could not open the selected backup file.")
            when (val result = backupRepository.import(input, currentMangaRoot())) {
                is ImportResult.Success -> {
                    "Backup imported: ${result.importedEntries} progress entries, " +
                        "${result.mergedEntries} merged."
                }
                is ImportResult.Rejected -> rejectionMessage(result.reason)
            }
        }
    }

    fun requestCacheClear(target: CacheClearTarget) {
        _uiState.update { it.copy(pendingCacheClear = target) }
    }

    fun cancelCacheClear() {
        _uiState.update { it.copy(pendingCacheClear = null) }
    }

    fun confirmCacheClear() {
        val target = _uiState.value.pendingCacheClear ?: return
        _uiState.update { it.copy(pendingCacheClear = null) }
        runOperation {
            when (target) {
                CacheClearTarget.THUMBNAILS -> thumbnailCache.clear()
                CacheClearTarget.EXTRACTED_CHAPTERS -> archiveExtractCache.clearAll()
                CacheClearTarget.ALL -> {
                    thumbnailCache.clear()
                    archiveExtractCache.clearAll()
                }
            }
            refreshCacheSizes()
            "Cleared ${target.label}."
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val result = runCatching {
                val preferences = preferencesRepository.snapshot()
                val folder = preferences.mangaFolderPath?.let(::File)
                val thumbnailBytes = thumbnailCache.sizeBytes()
                val extractedBytes = archiveExtractCache.sizeBytes()
                SettingsUiState(
                    loading = false,
                    storageAccessGranted = FolderAccess.hasStorageAccess(container.appContext),
                    hasPin = container.pinRepository.hasPin(),
                    biometricEnabled = container.pinRepository.biometricEnabled,
                    folderPath = preferences.mangaFolderPath,
                    folderAvailable = folder?.isDirectory == true,
                    thumbnailCacheBytes = thumbnailBytes,
                    extractedCacheBytes = extractedBytes
                )
            }
            _uiState.value = result.getOrElse { error ->
                SettingsUiState(
                    loading = false,
                    storageAccessGranted = FolderAccess.hasStorageAccess(container.appContext),
                    feedbackMessage = error.message ?: "Could not load settings."
                )
            }
        }
    }

    private fun runOperation(block: suspend () -> String) {
        if (_uiState.value.operationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true, feedbackMessage = null) }
            val message = runCatching { block() }
                .getOrElse { it.message ?: "The operation could not be completed." }
            _uiState.update {
                it.copy(operationInProgress = false, feedbackMessage = message)
            }
        }
    }

    private suspend fun refreshCacheSizes() {
        val thumbnailBytes = thumbnailCache.sizeBytes()
        val extractedBytes = archiveExtractCache.sizeBytes()
        _uiState.update {
            it.copy(
                thumbnailCacheBytes = thumbnailBytes,
                extractedCacheBytes = extractedBytes
            )
        }
    }

    private fun currentMangaRoot(): File? = _uiState.value.folderPath
        ?.let(::File)
        ?.takeIf { it.isDirectory }

    private fun rejectionMessage(reason: RejectionReason): String = when (reason) {
        RejectionReason.INVALID_FORMAT -> "That file is not a valid Manga Reader backup."
        RejectionReason.WRONG_APP -> "That backup belongs to a different application."
        RejectionReason.NEWER_SCHEMA -> "This backup requires a newer version of Manga Reader."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MangaReaderApp
                SettingsViewModel(app.container)
            }
        }

        fun defaultBackupFileName(): String = BackupRepository.defaultFileName()
    }
}
