package com.example.mangareader.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.di.AppContainer
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.MangaProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DetailUiState(
    val loading: Boolean = true,
    val manga: MangaItem? = null,
    val progress: MangaProgress? = null,
    val chaptersAscending: Boolean = true,
    val errorMessage: String? = null
)

class DetailViewModel(
    private val mangaId: String,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadManga()
    }

    fun toggleChapterOrder() {
        _uiState.update { it.copy(chaptersAscending = !it.chaptersAscending) }
    }

    fun refreshProgress() {
        if (_uiState.value.manga == null) return
        viewModelScope.launch {
            runCatching {
                val root = container.preferencesRepository.snapshot().mangaFolderPath
                    ?.let(::File)
                    ?.takeIf { it.isDirectory }
                container.progressRepository.load(root)[mangaId]
            }.onSuccess { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
    }

    private fun loadManga() {
        viewModelScope.launch {
            val result = runCatching {
                val manga = container.libraryCacheRepository.load()
                    ?.items
                    ?.firstOrNull { it.id == mangaId }
                    ?: error("This manga is no longer available in the library cache. Rescan the Library and try again.")
                val root = container.preferencesRepository.snapshot().mangaFolderPath
                    ?.let(::File)
                    ?.takeIf { it.isDirectory }
                val progress = container.progressRepository.load(root)[mangaId]
                manga to progress
            }
            _uiState.value = result.fold(
                onSuccess = { (manga, progress) ->
                    DetailUiState(
                        loading = false,
                        manga = manga,
                        progress = progress
                    )
                },
                onFailure = { error ->
                    DetailUiState(
                        loading = false,
                        errorMessage = error.message ?: "Could not load this manga."
                    )
                }
            )
        }
    }

    companion object {
        fun factory(mangaId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MangaReaderApp
                DetailViewModel(mangaId, app.container)
            }
        }
    }
}
