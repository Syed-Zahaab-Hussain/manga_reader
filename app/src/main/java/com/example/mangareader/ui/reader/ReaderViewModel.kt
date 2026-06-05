package com.example.mangareader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.di.AppContainer
import com.example.mangareader.domain.model.ChapterItem
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.MangaProgress
import com.example.mangareader.domain.model.ReadingMode
import com.example.mangareader.data.preferences.PreferencesRepository
import kotlinx.coroutines.Job
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
    val errorMessage: String? = null
)

class ReaderViewModel(
    private val mangaId: String,
    private val chapterIndex: Int,
    private val requestedPageIndex: Int,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var mangaRoot: File? = null
    private var existingProgress: MangaProgress? = null
    private var saveJob: Job? = null
    private var completionRecorded = false

    init {
        loadChapter()
    }

    fun retry() {
        loadChapter()
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

    fun onPageVisible(pageIndex: Int) {
        val state = _uiState.value
        if (state.loading || state.pages.isEmpty()) return
        val safeIndex = pageIndex.coerceIn(0, state.pages.lastIndex)
        if (safeIndex != state.currentPageIndex) {
            _uiState.update { it.copy(currentPageIndex = safeIndex) }
        }
        scheduleProgressSave()
    }

    fun onLastPageReached() {
        if (completionRecorded) return
        completionRecorded = true
        saveProgress(markCompleted = true)
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

    private fun loadChapter() {
        saveJob?.cancel()
        _uiState.value = ReaderUiState(loading = true)
        viewModelScope.launch {
            val result = runCatching {
                val manga = container.libraryCacheRepository.load()
                    ?.items
                    ?.firstOrNull { it.id == mangaId }
                    ?: error("This manga is no longer available. Rescan the Library and try again.")
                val chapter = manga.chapters.firstOrNull { it.index == chapterIndex }
                    ?: error("This chapter is no longer available.")
                val preferences = container.preferencesRepository.snapshot()
                mangaRoot = preferences.mangaFolderPath
                    ?.let(::File)
                    ?.takeIf { it.isDirectory }
                    ?: error("The selected manga folder is missing or unavailable.")
                existingProgress = container.progressRepository.load(mangaRoot)[mangaId]
                val pages = container.chapterPageRepository.loadPages(chapter)
                val initialPage = requestedPageIndex.coerceIn(0, pages.lastIndex)
                LoadedChapter(
                    manga = manga,
                    chapter = chapter,
                    pages = pages,
                    initialPage = initialPage,
                    readingMode = preferences.readingMode,
                    imageWidthFraction = preferences.imageWidthFraction
                )
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
                        imageWidthFraction = loaded.imageWidthFraction
                    )
                },
                onFailure = { error ->
                    ReaderUiState(
                        loading = false,
                        errorMessage = error.message ?: "This chapter could not be opened."
                    )
                }
            )
        }
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
            timestamp = System.currentTimeMillis()
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
        val imageWidthFraction: Float
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

internal fun buildReaderProgress(
    manga: MangaItem,
    chapter: ChapterItem,
    pageIndex: Int,
    prior: MangaProgress?,
    markCompleted: Boolean,
    timestamp: Long
): MangaProgress {
    val completed = if (markCompleted) {
        prior?.completedChapters.orEmpty() + chapter.index
    } else {
        prior?.completedChapters.orEmpty()
    }
    return MangaProgress(
        mangaId = manga.id,
        mangaTitle = manga.title,
        coverPath = manga.coverPath,
        coverArchivePath = manga.coverArchivePath,
        coverEntryName = manga.coverEntryName,
        chapterIndex = chapter.index,
        pageIndex = pageIndex,
        totalChapters = manga.chapters.size,
        completedChapters = completed,
        lastReadTimestamp = timestamp
    )
}
