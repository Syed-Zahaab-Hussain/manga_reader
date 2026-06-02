package com.example.mangareader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.mangareader.MangaReaderApp
import java.io.File

@Composable
fun CoverImage(
    coverPath: String?,
    archivePath: String?,
    entryName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var archiveCoverFile by remember(archivePath, entryName) {
        mutableStateOf<File?>(null)
    }

    LaunchedEffect(archivePath, entryName) {
        if (coverPath == null && archivePath != null && entryName != null) {
            val app = context.applicationContext as MangaReaderApp
            archiveCoverFile = app.container.thumbnailCache.obtainFromArchiveEntry(
                archivePath = archivePath,
                entryName = entryName,
                archiveMtimeMs = File(archivePath).takeIf { it.isFile }?.lastModified()
            )
        }
    }

    val model: Any? = when {
        coverPath != null -> File(coverPath).takeIf { it.isFile }
        else -> archiveCoverFile
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
