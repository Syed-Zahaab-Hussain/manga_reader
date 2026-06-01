package com.example.mangareader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mangareader.ui.screens.DetailScreen
import com.example.mangareader.ui.screens.LibraryScreen
import com.example.mangareader.ui.screens.LoginScreen
import com.example.mangareader.ui.screens.ReaderScreen
import com.example.mangareader.ui.screens.SettingsScreen
import com.example.mangareader.ui.screens.SetupScreen
import com.example.mangareader.ui.screens.SplashScreen

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
                onTimeout = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETUP) { SetupScreen() }
        composable(Routes.LOGIN) { LoginScreen() }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenDetail = { navController.navigate(Routes.DETAIL) },
                onOpenReader = { navController.navigate(Routes.READER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.DETAIL) { DetailScreen() }
        composable(Routes.READER) { ReaderScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
