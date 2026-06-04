package com.example.mangareader.ui.navigation

import android.net.Uri

object Routes {
    const val SPLASH = "/"
    const val SETUP = "/setup"
    const val CHANGE_PIN = "/setup/change"
    const val LOGIN = "/login"
    const val LIBRARY = "/library"
    const val DETAIL = "/detail?mangaId={mangaId}"
    const val READER = "/reader?mangaId={mangaId}&chapterIndex={chapterIndex}&pageIndex={pageIndex}"
    const val SETTINGS = "/settings"

    fun detail(mangaId: String): String = "/detail?mangaId=${Uri.encode(mangaId)}"

    fun reader(mangaId: String, chapterIndex: Int, pageIndex: Int): String =
        "/reader?mangaId=${Uri.encode(mangaId)}&chapterIndex=$chapterIndex&pageIndex=$pageIndex"
}
