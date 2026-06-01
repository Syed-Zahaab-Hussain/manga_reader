package com.example.mangareader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MangaReaderColorScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = BackgroundDark,
    primaryContainer = SurfaceTeal,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = BackgroundDark,
    secondaryContainer = SurfaceTeal,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentTeal,
    onTertiary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceTeal,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceTeal,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary,
    outlineVariant = SurfaceTeal,
    error = DangerRed,
    onError = TextPrimary,
    errorContainer = BackgroundDark,
    onErrorContainer = DangerRed
)

@Composable
fun MangaReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MangaReaderColorScheme,
        typography = Typography,
        content = content
    )
}
