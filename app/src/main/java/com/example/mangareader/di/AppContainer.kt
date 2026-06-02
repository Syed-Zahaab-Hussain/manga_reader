package com.example.mangareader.di

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.example.mangareader.data.auth.BiometricAuthenticator
import com.example.mangareader.data.auth.PinRepository
import com.example.mangareader.data.backup.BackupRepository
import com.example.mangareader.data.cache.ArchiveExtractCache
import com.example.mangareader.data.cache.LibraryCacheRepository
import com.example.mangareader.data.cache.ThumbnailCache
import com.example.mangareader.data.preferences.PreferencesRepository
import com.example.mangareader.data.progress.ProgressRepository

class AppContainer(val appContext: Context) {

    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }

    val pinRepository: PinRepository by lazy { PinRepository(appContext) }

    val progressRepository: ProgressRepository by lazy { ProgressRepository() }

    val libraryCacheRepository: LibraryCacheRepository by lazy { LibraryCacheRepository(appContext) }

    val thumbnailCache: ThumbnailCache by lazy { ThumbnailCache(appContext) }

    val archiveExtractCache: ArchiveExtractCache by lazy { ArchiveExtractCache(appContext) }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(preferencesRepository, progressRepository)
    }

    fun biometricAuthenticator(activity: FragmentActivity): BiometricAuthenticator =
        BiometricAuthenticator(activity)
}
