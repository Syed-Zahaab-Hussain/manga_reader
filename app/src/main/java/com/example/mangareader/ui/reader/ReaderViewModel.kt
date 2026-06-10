package com.example.mangareader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.core.io.PortablePath
import com.example.mangareader.di.AppContainer
import com.example.mangareader.domain.model.ChapterItem
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.MangaProgress
import com.example.mangareader.domain.model.ReadingMode
import com.example.mangareader.domain.model.pageIndexForChapter
import com.example.mangareader.data.preferences.PreferencesRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReaderUiState(
    val loading: Boolean = true,
    val manga: MangaItem? = null,
    val chapter: ChapterItem? = null,
    val pages: List<File> = emptyList(),
    val currentPageIndex: Int = 0,
    val readingMode: ReadingMode = ReadingMode.VERTICAL,
    val imageWidthFraction: Float = PreferencesRepository.DEFAULT_IMAGE_WIDTH,
    val showPageNumbers: Boolean = false,
    val zoomEnabled: Boolean = false,
    val controlsLocked: Boolean = false,
    val canOpenPreviousChapter: Boolean = false,
    val canOpenNextChapter: Boolean = false,
    val errorMessage: String? = null
)

class ReaderViewModel(
    private val mangaId: String,
    chapterIndex: Int,
    private val requestedPageIndex: Int,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var mangaRoot: File? = null
    private var existingProgress: MangaProgress? = null
    private var saveJob: Job? = null
    private var chapterLoadJob: Job? = null
    private var activeChapterIndex = chapterIndex
    private var preload: ChapterPreload? = null
    private var completionRecorded = false

    init {
        loadChapter(chapterIndex = activeChapterIndex, pageIndex = requestedPageIndex)
    }

    fun retry() {
        loadChapter(chapterIndex = activeChapterIndex, pageIndex = 0)
    }

    fun setReadingMode(mode: ReadingMode) {
        _uiState.update { it.copy(readingMode = mode) }
        viewModelScope.launch {
            container.preferencesRepository.setReadingMode(mode)
        }
    }

    fun previewImageWidth(fraction: Float) {
        _uiState.update {
            it.copy(
                imageWidthFraction = fraction.coerceIn(
                    PreferencesRepository.MIN_IMAGE_WIDTH,
                    PreferencesRepository.MAX_IMAGE_WIDTH
                )
            )
        }
    }

    fun saveImageWidth() {
        val fraction = _uiState.value.imageWidthFraction
        viewModelScope.launch {
            container.preferencesRepository.setImageWidth(fraction)
        }
    }

    fun setShowPageNumbers(show: Boolean) {
        _uiState.update { it.copy(showPageNumbers = show) }
        viewModelScope.launch {
            container.preferencesRepository.setShowPageNumbers(show)
        }
    }

    fun setZoomEnabled(enabled: Boolean) {
        _uiState.update { it.copy(zoomEnabled = enabled) }
        viewModelScope.launch {
            container.preferencesRepository.setZoomEnabled(enabled)
        }
    }

    fun setControlsLocked(locked: Boolean) {
        _uiState.update { it.copy(controlsLocked = locked) }
        viewModelScope.launch {
            container.preferencesRepository.setControlsLocked(locked)
        }
    }

    fun onPageVisible(pageIndex: Int) {
        val state = _uiState.value
        if (state.loading || state.pages.isEmpty()) return
        val safeIndex = pageIndex.coerceIn(0, state.pages.lastIndex)
        if (safeIndex != state.currentPageIndex) {
            _uiState.update { it.copy(currentPageIndex = safeIndex) }
        }
        if (shouldPreloadNextChapter(safeIndex, state.pages.lastIndex)) {
            preloadNextChapter()
        }
        scheduleProgressSave()
    }

    fun onLastPageReached() {
        if (completionRecorded) return
        completionRecorded = true
        saveProgress(markCompleted = true)
        preloadNextChapter()
    }

    fun openPreviousChapter() {
        openAdjacentChapter(offset = -1)
    }

    fun openNextChapter() {
        openAdjacentChapter(offset = 1)
    }

    fun saveBeforeExit(onSaved: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persistProgress(markCompleted = completionRecorded)
            onSaved()
        }
    }

    fun saveImmediately() {
        saveJob?.cancel()
        saveProgress(markCompleted = completionRecorded)
    }

    private fun openAdjacentChapter(offset: Int) {
        val state = _uiState.value
        if (state.loading) return
        val manga = state.manga ?: return
        val currentChapter = state.chapter ?: return
        val currentPosition = manga.chapters.indexOfFirst { it.index == currentChapter.index }
        val targetChapter = manga.chapters.getOrNull(currentPosition + offset) ?: return
        loadChapter(
            chapterIndex = targetChapter.index,
            pageIndex = existingProgress?.pageIndexForChapter(targetChapter.index) ?: 0,
            saveCurrentChapter = true
        )
    }

    private fun loadChapter(
        chapterIndex: Int,
        pageIndex: Int,
        saveCurrentChapter: Boolean = false
    ) {
        chapterLoadJob?.cancel()
        chapterLoadJob = viewModelScope.launch {
            if (saveCurrentChapter) {
                saveJob?.cancelAndJoin()
                persistProgress(markCompleted = completionRecorded)
            } else {
                saveJob?.cancel()
            }
            activeChapterIndex = chapterIndex
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val result = runCatching {
                val manga = _uiState.value.manga ?: container.libraryCacheRepository.load()
                    ?.items
                    ?.firstOrNull { it.id == mangaId }
                    ?: error("This manga is no longer available. Rescan the Library and try again.")
                val chapter = manga.chapters.firstOrNull { it.index == chapterIndex }
                    ?: error("This chapter is no longer available.")
                val preferences = container.preferencesRepository.snapshot()
                if (mangaRoot == null) {
                    mangaRoot = preferences.mangaFolderPath
                        ?.let(::File)
                        ?.takeIf { it.isDirectory }
                        ?: error("The selected manga folder is missing or unavailable.")
                }
                if (existingProgress == null) {
                    existingProgress = container.progressRepository.load(mangaRoot)[mangaId]
                }
                val pages = loadPagesUsingPreload(chapter)
                val initialPage = pageIndex.coerceIn(0, pages.lastIndex)
                val chapterPosition = manga.chapters.indexOfFirst { it.index == chapter.index }
                LoadedChapter(
                    manga = manga,
                    chapter = chapter,
                    pages = pages,
                    initialPage = initialPage,
                    readingMode = preferences.readingMode,
                    imageWidthFraction = preferences.imageWidthFraction,
                    showPageNumbers = preferences.showPageNumbers,
                    zoomEnabled = preferences.zoomEnabled,
                    controlsLocked = preferences.controlsLocked,
                    canOpenPreviousChapter = chapterPosition > 0,
                    canOpenNextChapter = chapterPosition in 0 until manga.chapters.lastIndex
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
            _uiState.value = result.fold(
                onSuccess = { loaded ->
                    ReaderUiState(
                        loading = false,
                        manga = loaded.manga,
                        chapter = loaded.chapter,
                        pages = loaded.pages,
                        currentPageIndex = loaded.initialPage,
                        readingMode = loaded.readingMode,
                        imageWidthFraction = loaded.imageWidthFraction,
                        showPageNumbers = loaded.showPageNumbers,
                        zoomEnabled = loaded.zoomEnabled,
                        controlsLocked = loaded.controlsLocked,
                        canOpenPreviousChapter = loaded.canOpenPreviousChapter,
                        canOpenNextChapter = loaded.canOpenNextChapter
                    )
                },
                onFailure = { error ->
                    ReaderUiState(
                        loading = false,
                        errorMessage = error.message ?: "This chapter could not be opened."
                    )
                }
            )
            result.getOrNull()?.let { loaded ->
                completionRecorded = loaded.chapter.index in
                    existingProgress?.completedChapters.orEmpty()
                if (shouldPreloadNextChapter(loaded.initialPage, loaded.pages.lastIndex)) {
                    preloadNextChapter()
                }
            }
        }
    }

    private suspend fun loadPagesUsingPreload(chapter: ChapterItem): List<File> {
        val pending = preload?.takeIf { it.chapterIndex == chapter.index }
        if (pending != null) {
            preload = null
            return pending.pages.await().getOrElse {
                container.chapterPageRepository.loadPages(chapter)
            }
        }
        preload?.pages?.cancel()
        preload = null
        return container.chapterPageRepository.loadPages(chapter)
    }

    private fun preloadNextChapter() {
        val state = _uiState.value
        val manga = state.manga ?: return
        val chapter = state.chapter ?: return
        val currentPosition = manga.chapters.indexOfFirst { it.index == chapter.index }
        val nextChapter = manga.chapters.getOrNull(currentPosition + 1) ?: return
        if (preload?.chapterIndex == nextChapter.index) return
        preload?.pages?.cancel()
        preload = ChapterPreload(
            chapterIndex = nextChapter.index,
            pages = viewModelScope.async {
                runCatching { container.chapterPageRepository.loadPages(nextChapter) }
            }
        )
    }

    private fun scheduleProgressSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(PROGRESS_SAVE_DEBOUNCE_MS)
            persistProgress(markCompleted = completionRecorded)
        }
    }

    private fun saveProgress(markCompleted: Boolean) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            persistProgress(markCompleted)
        }
    }

    private suspend fun persistProgress(markCompleted: Boolean) {
        val root = mangaRoot ?: return
        val state = _uiState.value
        val manga = state.manga ?: return
        val chapter = state.chapter ?: return
        val updated = buildReaderProgress(
            manga = manga,
            chapter = chapter,
            pageIndex = state.currentPageIndex,
            prior = existingProgress,
            markCompleted = markCompleted,
            timestamp = System.currentTimeMillis(),
            mangaRoot = root
        )
        container.progressRepository.upsert(root, updated)
        existingProgress = updated
    }

    private data class LoadedChapter(
        val manga: MangaItem,
        val chapter: ChapterItem,
        val pages: List<File>,
        val initialPage: Int,
        val readingMode: ReadingMode,
        val imageWidthFraction: Float,
        val showPageNumbers: Boolean,
        val zoomEnabled: Boolean,
        val controlsLocked: Boolean,
        val canOpenPreviousChapter: Boolean,
        val canOpenNextChapter: Boolean
    )

    private data class ChapterPreload(
        val chapterIndex: Int,
        val pages: Deferred<Result<List<File>>>
    )

    companion object {
        private const val PROGRESS_SAVE_DEBOUNCE_MS = 500L

        fun factory(
            mangaId: String,
            chapterIndex: Int,
            pageIndex: Int
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MangaReaderApp
                ReaderViewModel(mangaId, chapterIndex, pageIndex, app.container)
            }
        }
    }
}

internal fun shouldPreloadNextChapter(currentPageIndex: Int, lastPageIndex: Int): Boolean {
    if (lastPageIndex < 0) return false
    return currentPageIndex >= (lastPageIndex - PRELOAD_REMAINING_PAGE_COUNT).coerceAtLeast(0)
}

private const val PRELOAD_REMAINING_PAGE_COUNT = 2

internal fun buildReaderProgress(
    manga: MangaItem,
    chapter: ChapterItem,
    pageIndex: Int,
    prior: MangaProgress?,
    markCompleted: Boolean,
    timestamp: Long,
    mangaRoot: File? = null
): MangaProgress {
    val completed = if (markCompleted) {
        prior?.completedChapters.orEmpty() + chapter.index
    } else {
        prior?.completedChapters.orEmpty()
    }
    val priorChapterPages = prior?.let { progress ->
        progress.chapterPageIndices + (progress.chapterIndex to progress.pageIndex)
    }.orEmpty()
    return MangaProgress(
        mangaId = manga.id,
        mangaTitle = manga.title,
        coverPath = PortablePath.relativeToRoot(mangaRoot, manga.coverPath),
        coverArchivePath = PortablePath.relativeToRoot(mangaRoot, manga.coverArchivePath),
        coverEntryName = manga.coverEntryName,
        chapterIndex = chapter.index,
        pageIndex = pageIndex,
        totalChapters = manga.chapters.size,
        completedChapters = completed,
        lastReadTimestamp = timestamp,
        chapterPageIndices = priorChapterPages + (chapter.index to pageIndex),
        recentlyReadDismissedAt = 0L
    )
}
