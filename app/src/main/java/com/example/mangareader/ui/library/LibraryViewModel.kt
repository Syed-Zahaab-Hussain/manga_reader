package com.example.mangareader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.core.io.FolderAccess
import com.example.mangareader.di.AppContainer
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.MangaProgress
import com.example.mangareader.domain.model.ScanWarning
import com.example.mangareader.scanner.MangaScanner
import com.example.mangareader.scanner.ScanEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption(val label: String) {
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    CHAPTERS_DESC("Most Chapters"),
    CHAPTERS_ASC("Fewest Chapters"),
    LAST_READ("Last Read")
}

enum class MangaStatusFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    READING("Reading"),
    COMPLETED("Completed")
}

data class LibraryUiState(
    val initializing: Boolean = true,
    val storageAccessGranted: Boolean = true,
    val folderPath: String? = null,
    val folderMissing: Boolean = false,
    val items: List<MangaItem> = emptyList(),
    val progressByMangaId: Map<String, MangaProgress> = emptyMap(),
    val recentProgress: List<MangaProgress> = emptyList(),
    val scanning: Boolean = false,
    val scanCurrent: Int = 0,
    val scanTotal: Int = 0,
    val warnings: List<ScanWarning> = emptyList(),
    val showWarningsDialog: Boolean = false,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.TITLE_ASC,
    val filterOption: MangaStatusFilter = MangaStatusFilter.ALL,
    val errorMessage: String? = null
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val preferencesRepository = container.preferencesRepository
    private val progressRepository = container.progressRepository
    private val libraryCacheRepository = container.libraryCacheRepository
    private val scanner = MangaScanner()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var folderFile: File? = null
    private var allItems: List<MangaItem> = emptyList()
    private var progressMap: Map<String, MangaProgress> = emptyMap()
    private var forceRescanOnNextLoad = false

    init {
        bootstrap(initial = true)
    }

    fun recheckAccess() {
        if (_uiState.value.storageAccessGranted) return
        if (FolderAccess.hasStorageAccess(container.appContext)) {
            bootstrap(initial = false)
        }
    }

    fun refresh() {
        forceRescanOnNextLoad = true
        bootstrap(initial = false)
    }

    fun startScan() {
        val root = folderFile ?: return
        cancelScan()
        _uiState.update {
            it.copy(
                scanning = true,
                scanCurrent = 0,
                scanTotal = 0,
                warnings = emptyList(),
                showWarningsDialog = false
            )
        }
        scanJob = viewModelScope.launch {
            try {
                scanner.scan(root).collect { event ->
                    when (event) {
                        is ScanEvent.Progress -> _uiState.update {
                            it.copy(scanCurrent = event.current, scanTotal = event.total)
                        }
                        is ScanEvent.Batch -> {
                            allItems = allItems + event.items
                            applyDerivedLists()
                        }
                        is ScanEvent.Warning -> _uiState.update {
                            it.copy(warnings = it.warnings + event.warning)
                        }
                        ScanEvent.Completed -> finishScan()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                if (_uiState.value.scanning && scanJob?.isCancelled != false) {
                    _uiState.update { it.copy(scanning = false) }
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(scanning = false, scanCurrent = 0, scanTotal = 0) }
    }

    fun onFolderPicked(uri: Uri) {
        val folder = FolderAccess.treeUriToFolder(container.appContext, uri)
        if (folder == null || !folder.isDirectory) {
            val detail = FolderAccess.describeTreeUri(uri)
            _uiState.update {
                it.copy(
                    errorMessage = "Could not open the selected folder." +
                        " Make sure it is on this device's internal storage or SD card" +
                        " and that All Files Access is still enabled. ($detail)"
                )
            }
            return
        }
        viewModelScope.launch {
            preferencesRepository.setMangaFolder(folder.absolutePath)
            libraryCacheRepository.clear()
            forceRescanOnNextLoad = true
            bootstrap(initial = false)
        }
    }

    fun dismissWarnings() {
        _uiState.update { it.copy(showWarningsDialog = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { state ->
            state.copy(
                searchActive = active,
                searchQuery = if (active) state.searchQuery else ""
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyDerivedLists()
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        applyDerivedLists()
    }

    fun setFilterOption(filter: MangaStatusFilter) {
        _uiState.update { it.copy(filterOption = filter) }
        applyDerivedLists()
    }

    private fun bootstrap(initial: Boolean) {
        viewModelScope.launch {
            val granted = FolderAccess.hasStorageAccess(container.appContext)
            if (!granted) {
                _uiState.update {
                    it.copy(
                        initializing = false,
                        storageAccessGranted = false,
                        folderPath = null
                    )
                }
                return@launch
            }
            val prefsSnapshot = preferencesRepository.snapshot()
            folderFile = prefsSnapshot.mangaFolderPath
                ?.let(::File)
                ?.takeIf { it.isDirectory }
            _uiState.update { state ->
                state.copy(
                    storageAccessGranted = true,
                    folderPath = prefsSnapshot.mangaFolderPath,
                    folderMissing = prefsSnapshot.mangaFolderPath != null && folderFile == null
                )
            }
            loadProgress()
            loadLibraryOrScan(initial)
            _uiState.update { it.copy(initializing = false) }
        }
    }

    private suspend fun loadProgress() {
        progressMap = progressRepository.load(folderFile)
        _uiState.update {
            it.copy(
                progressByMangaId = progressMap,
                recentProgress = recentFrom(progressMap)
            )
        }
    }

    private suspend fun loadLibraryOrScan(initial: Boolean) {
        val cached = libraryCacheRepository.load()
        val cacheMatches =
            cached != null &&
                cached.folderPath == _uiState.value.folderPath &&
                cached.folderPath != null
        if (!forceRescanOnNextLoad && cacheMatches && cached != null) {
            allItems = cached.items
            applyDerivedLists()
            return
        }
        forceRescanOnNextLoad = false
        if (folderFile == null) return
        allItems = emptyList()
        applyDerivedLists()
        startScan()
    }

    private suspend fun finishScan() {
        libraryCacheRepository.save(_uiState.value.folderPath, allItems)
        _uiState.update {
            it.copy(
                scanning = false,
                showWarningsDialog = it.warnings.isNotEmpty()
            )
        }
    }

    private fun applyDerivedLists() {
        val state = _uiState.value
        val filtered = filterAndSort(
            items = allItems,
            query = state.searchQuery,
            filter = state.filterOption,
            sort = state.sortOption
        )
        _uiState.update { it.copy(items = filtered) }
    }

    private fun statusOf(progress: MangaProgress?): MangaStatusFilter = when {
        progress == null -> MangaStatusFilter.UNREAD
        progress.totalChapters > 0 && progress.completedChapters.size >= progress.totalChapters ->
            MangaStatusFilter.COMPLETED
        else -> MangaStatusFilter.READING
    }

    private fun filterAndSort(
        items: List<MangaItem>,
        query: String,
        filter: MangaStatusFilter,
        sort: SortOption
    ): List<MangaItem> {
        val filtered = items.asSequence().filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query.trim(), ignoreCase = true)
            val matchesFilter = filter == MangaStatusFilter.ALL ||
                statusOf(progressMap[item.id]) == filter
            matchesQuery && matchesFilter
        }.toList()

        return when (sort) {
            SortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.CHAPTERS_DESC -> filtered.sortedByDescending { it.chapters.size }
            SortOption.CHAPTERS_ASC -> filtered.sortedBy { it.chapters.size }
            SortOption.LAST_READ -> filtered.sortedByDescending { item ->
                progressMap[item.id]?.lastReadTimestamp ?: 0L
            }
        }
    }

    private fun recentFrom(progress: Map<String, MangaProgress>): List<MangaProgress> =
        progress.values
            .filter { it.lastReadTimestamp > 0L }
            .sortedByDescending { it.lastReadTimestamp }
            .take(RECENT_LIMIT)

    companion object {
        private const val RECENT_LIMIT = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MangaReaderApp
                LibraryViewModel(app.container)
            }
        }
    }
}
