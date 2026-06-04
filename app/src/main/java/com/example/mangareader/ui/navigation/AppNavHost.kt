package com.example.mangareader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.mangareader.ui.screens.ReaderScreen
import com.example.mangareader.ui.screens.detail.DetailScreen
import com.example.mangareader.ui.screens.library.LibraryScreen
import com.example.mangareader.ui.screens.login.LoginScreen
import com.example.mangareader.ui.screens.settings.SettingsScreen
import com.example.mangareader.ui.screens.setup.SetupScreen
import com.example.mangareader.ui.screens.splash.SplashScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    onSessionAuthenticated: () -> Unit = {},
    onAppReset: () -> Unit = {},
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
                onGoToSetup = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                changeMode = false,
                onComplete = {
                    onSessionAuthenticated()
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CHANGE_PIN) {
            SetupScreen(
                changeMode = true,
                onComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onUnlocked = {
                    onSessionAuthenticated()
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LIBRARY) { backStackEntry ->
            val reloadRequested by backStackEntry.savedStateHandle
                .getStateFlow(LIBRARY_RELOAD_KEY, false)
                .collectAsStateWithLifecycle()
            LibraryScreen(
                onOpenDetail = { mangaId -> navController.navigate(Routes.detail(mangaId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                reloadRequested = reloadRequested,
                onReloadHandled = {
                    backStackEntry.savedStateHandle[LIBRARY_RELOAD_KEY] = false
                }
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("mangaId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId").orEmpty()
            DetailScreen(
                mangaId = mangaId,
                onBack = { navController.popBackStack() },
                onOpenChapter = { id, chapterIndex, pageIndex ->
                    navController.navigate(Routes.reader(id, chapterIndex, pageIndex))
                }
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("mangaId") { type = NavType.StringType },
                navArgument("chapterIndex") { type = NavType.IntType },
                navArgument("pageIndex") { type = NavType.IntType }
            )
        ) {
            ReaderScreen()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onChangePin = { navController.navigate(Routes.CHANGE_PIN) },
                onResetComplete = {
                    onAppReset()
                    navController.navigate(Routes.SETUP) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(LIBRARY_RELOAD_KEY, true)
                    navController.popBackStack()
                }
            )
        }
    }
}

private const val LIBRARY_RELOAD_KEY = "library_reload_requested"
