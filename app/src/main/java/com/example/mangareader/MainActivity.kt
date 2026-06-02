package com.example.mangareader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.example.mangareader.ui.navigation.AppNavHost
import com.example.mangareader.ui.theme.MangaReaderTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MangaReaderTheme {
                AppNavHost(navController = rememberNavController())
            }
        }
    }
}
