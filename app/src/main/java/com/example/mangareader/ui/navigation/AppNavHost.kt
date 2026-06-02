package com.example.mangareader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mangareader.ui.screens.DetailScreen
import com.example.mangareader.ui.screens.ReaderScreen
import com.example.mangareader.ui.screens.SettingsScreen
import com.example.mangareader.ui.screens.SetupScreen
import com.example.mangareader.ui.screens.library.LibraryScreen
import com.example.mangareader.ui.screens.login.LoginScreen
import com.example.mangareader.ui.screens.splash.SplashScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onGoToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onGoToLibrary = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                actions = listOf(
                    com.example.mangareader.ui.screens.PlaceholderAction("Continue to Library") {
                        navController.navigate(Routes.LIBRARY) { popUpTo(Routes.SETUP) { inclusive = true } }
                    }
                )
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onUnlocked = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenDetail = { navController.navigate(Routes.DETAIL) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.DETAIL) { DetailScreen() }
        composable(Routes.READER) { ReaderScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
