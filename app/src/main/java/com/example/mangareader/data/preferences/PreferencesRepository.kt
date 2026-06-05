package com.example.mangareader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mangareader.domain.model.PageFit
import com.example.mangareader.domain.model.ReaderPreferences
import com.example.mangareader.domain.model.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "manga_reader_preferences"
)

class PreferencesRepository(private val context: Context) {

    val preferences: Flow<ReaderPreferences> = context.dataStore.data.map { it.toReaderPreferences() }

    suspend fun snapshot(): ReaderPreferences = preferences.first()

    suspend fun setMangaFolder(path: String?) = edit { prefs ->
        if (path.isNullOrEmpty()) prefs.remove(Keys.FOLDER) else prefs[Keys.FOLDER] = path
    }

    suspend fun setReadingMode(mode: ReadingMode) = edit { it[Keys.READING_MODE] = mode.name }

    suspend fun setHorizontalPageFit(fit: PageFit) = edit { it[Keys.PAGE_FIT] = fit.name }

    suspend fun setImageWidth(fraction: Float) = edit {
        it[Keys.IMAGE_WIDTH] = fraction.coerceIn(MIN_IMAGE_WIDTH, MAX_IMAGE_WIDTH)
    }

    suspend fun setShowPageNumbers(show: Boolean) = edit { it[Keys.SHOW_PAGE_NUMBERS] = show }

    suspend fun setZoomEnabled(enabled: Boolean) = edit { it[Keys.ZOOM_ENABLED] = enabled }

    suspend fun setControlsLocked(locked: Boolean) = edit { it[Keys.CONTROLS_LOCKED] = locked }

    suspend fun clearAll() = edit { it.clear() }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { transform(it) }
    }

    private fun Preferences.toReaderPreferences(): ReaderPreferences = ReaderPreferences(
        mangaFolderPath = this[Keys.FOLDER],
        readingMode = enumOrDefault(this[Keys.READING_MODE], ReadingMode.VERTICAL),
        horizontalPageFit = enumOrDefault(this[Keys.PAGE_FIT], PageFit.WIDTH),
        imageWidthFraction = this[Keys.IMAGE_WIDTH] ?: DEFAULT_IMAGE_WIDTH,
        showPageNumbers = this[Keys.SHOW_PAGE_NUMBERS] ?: false,
        zoomEnabled = this[Keys.ZOOM_ENABLED] ?: false,
        controlsLocked = this[Keys.CONTROLS_LOCKED] ?: false
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private object Keys {
        val FOLDER = stringPreferencesKey("manga_folder_path")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val PAGE_FIT = stringPreferencesKey("horizontal_page_fit")
        val IMAGE_WIDTH = floatPreferencesKey("reader_image_width")
        val SHOW_PAGE_NUMBERS = booleanPreferencesKey("reader_show_page_numbers")
        val ZOOM_ENABLED = booleanPreferencesKey("reader_zoom_enabled")
        val CONTROLS_LOCKED = booleanPreferencesKey("reader_controls_locked")
    }

    companion object {
        const val MIN_IMAGE_WIDTH = 0.4f
        const val MAX_IMAGE_WIDTH = 1.0f
        const val DEFAULT_IMAGE_WIDTH = 1.0f
    }
}
