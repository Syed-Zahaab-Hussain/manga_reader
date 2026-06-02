package com.example.mangareader

import android.app.Application
import com.example.mangareader.di.AppContainer

class MangaReaderApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
