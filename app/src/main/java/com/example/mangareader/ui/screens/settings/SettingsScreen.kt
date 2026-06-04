package com.example.mangareader.ui.screens.settings

import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.core.io.FolderAccess
import com.example.mangareader.ui.settings.CacheClearTarget
import com.example.mangareader.ui.settings.SettingsUiState
import com.example.mangareader.ui.settings.SettingsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = remember(context) { context.findFragmentActivity() }
    val container = remember { (context.applicationContext as MangaReaderApp).container }
    val biometricAvailable = remember(activity) {
        activity?.let { container.biometricAuthenticator(it) }
            ?.let { runCatching { it.isAvailable() }.getOrDefault(false) }
            ?: false
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.onFolderPicked(uri)
        }
    }
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.recheckStorageAccess()
        if (granted) folderPickerLauncher.launch(null)
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.recheckStorageAccess()
        if (FolderAccess.hasStorageAccess(context)) folderPickerLauncher.launch(null)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.prepareImport(uri)
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                SettingsContent(
                    state = state,
                    biometricAvailable = biometricAvailable,
                    onChooseFolder = {
                        when {
                            FolderAccess.hasStorageAccess(context) -> folderPickerLauncher.launch(null)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                allFilesAccessLauncher.launch(FolderAccess.allFilesAccessIntent(context))
                            }
                            else -> legacyPermissionLauncher.launch(FolderAccess.legacyReadPermission())
                        }
                    },
                    onClearCache = viewModel::requestCacheClear,
                    onExportBackup = {
                        exportLauncher.launch(SettingsViewModel.defaultBackupFileName())
                    },
                    onImportBackup = {
                        importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                    },
                    onBiometricChange = viewModel::setBiometricEnabled,
                    onChangePin = onChangePin
                )
            }

            if (state.operationInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    state.pendingImportUri?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Import backup?") },
            text = {
                Text(
                    "Reader preferences will be restored and reading progress will be merged. " +
                        "Newer progress wins, while completed chapters are preserved."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmImport) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) { Text("Cancel") }
            }
        )
    }

    state.pendingCacheClear?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCacheClear,
            title = { Text("Clear ${target.label}?") },
            text = {
                Text("This removes temporary app files only. Your manga and reading progress will not be deleted.")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmCacheClear) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelCacheClear) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    biometricAvailable: Boolean,
    onChooseFolder: () -> Unit,
    onClearCache: (CacheClearTarget) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onChangePin: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SettingsSection(title = "Library") {
                SettingsActionRow(
                    icon = Icons.Filled.Folder,
                    title = "Manga Folder",
                    subtitle = state.folderPath ?: "No folder selected",
                    actionLabel = if (state.folderPath == null) "Choose" else "Change",
                    enabled = !state.operationInProgress,
                    onAction = onChooseFolder
                )
                if (state.folderPath != null && !state.folderAvailable) {
                    Text(
                        text = "The selected folder is currently unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Storage") {
                CacheRow(
                    title = "Thumbnail Cache",
                    size = formatBytes(state.thumbnailCacheBytes),
                    enabled = !state.operationInProgress,
                    onClear = { onClearCache(CacheClearTarget.THUMBNAILS) }
                )
                CacheRow(
                    title = "Extracted Chapters",
                    size = formatBytes(state.extractedCacheBytes),
                    enabled = !state.operationInProgress,
                    onClear = { onClearCache(CacheClearTarget.EXTRACTED_CHAPTERS) }
                )
                OutlinedButton(
                    onClick = { onClearCache(CacheClearTarget.ALL) },
                    enabled = !state.operationInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Clear All Storage Cache")
                }
            }
        }

        if (state.hasPin) {
            item {
                SettingsSection(title = "Security") {
                    if (biometricAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text("Fingerprint Login", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Use an enrolled fingerprint when unlocking the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.biometricEnabled,
                                onCheckedChange = onBiometricChange,
                                enabled = !state.operationInProgress
                            )
                        }
                    }
                    SettingsActionRow(
                        icon = Icons.Filled.Lock,
                        title = "Change PIN",
                        subtitle = "Verify your current PIN before creating a new one.",
                        actionLabel = "Change",
                        enabled = !state.operationInProgress,
                        onAction = onChangePin
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Backup") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Export reader preferences and reading progress, or merge them from an existing backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportBackup,
                            enabled = !state.operationInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Export")
                        }
                        Button(
                            onClick = onImportBackup,
                            enabled = state.folderAvailable && !state.operationInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Import")
                        }
                    }
                    if (!state.folderAvailable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select an available manga folder before importing reading progress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onAction, enabled = enabled) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun CacheRow(
    title: String,
    size: String,
    enabled: Boolean,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClear, enabled = enabled) {
            Text("Clear")
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
