package com.example.mangareader.ui.screens.library

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mangareader.core.io.FolderAccess
import com.example.mangareader.domain.model.ScanWarning
import com.example.mangareader.ui.components.MangaCard
import com.example.mangareader.ui.components.RecentlyReadCard
import com.example.mangareader.ui.library.LibraryUiState
import com.example.mangareader.ui.library.LibraryViewModel
import com.example.mangareader.ui.library.MangaStatusFilter
import com.example.mangareader.ui.library.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    reloadRequested: Boolean = false,
    onReloadHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(reloadRequested) {
        if (reloadRequested) {
            viewModel.reload()
            onReloadHandled()
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
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
    ) { viewModel.recheckAccess() }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.recheckAccess() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LibraryTopBar(
            searchActive = state.searchActive,
            searchQuery = state.searchQuery,
            sortOption = state.sortOption,
            filterOption = state.filterOption,
            onSearchActivate = { viewModel.setSearchActive(true) },
            onSearchClose = { viewModel.setSearchActive(false) },
            onSearchQueryChange = viewModel::setSearchQuery,
            onSortSelect = viewModel::setSortOption,
            onFilterSelect = viewModel::setFilterOption,
            onOpenSettings = onOpenSettings
        )

        when {
            state.initializing -> LibraryLoadingIndicator()

            !state.storageAccessGranted -> CenteredMessage(
                title = "Storage access needed",
                message = "Manga Reader needs access to your files to find your manga."
            ) {
                Button(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        allFilesAccessLauncher.launch(FolderAccess.allFilesAccessIntent(context))
                    } else {
                        legacyPermissionLauncher.launch(FolderAccess.legacyReadPermission())
                    }
                }) { Text("Grant access") }
            }

            state.folderPath == null -> CenteredMessage(
                title = "No folder selected",
                message = "Choose the folder that contains your manga."
            ) {
                Button(onClick = { pickFolderLauncher.launch(null) }) { Text("Choose folder") }
            }

            state.folderMissing -> CenteredMessage(
                title = "Folder unavailable",
                message = "The selected folder could not be found. It may have been moved or removed."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFolderLauncher.launch(null) }) { Text("Choose folder") }
                    Button(onClick = { viewModel.refresh() }) { Text("Try again") }
                }
            }

            else -> PullToRefreshBox(
                isRefreshing = state.scanning,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LibraryContent(
                    state = state,
                    viewModel = viewModel,
                    onPickFolder = { pickFolderLauncher.launch(null) },
                    onOpenDetail = onOpenDetail
                )
            }
        }
    }

    if (state.showWarningsDialog) {
        ScanWarningsDialog(warnings = state.warnings, onDismiss = viewModel::dismissWarnings)
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun LibraryLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    searchActive: Boolean,
    searchQuery: String,
    sortOption: SortOption,
    filterOption: MangaStatusFilter,
    onSearchActivate: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onFilterSelect: (MangaStatusFilter) -> Unit,
    onOpenSettings: () -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterSheetOpen by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Manga Reader",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = {
                                if (option == sortOption) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onSortSelect(option)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
            BadgedBox(
                badge = {
                    if (filterOption != MangaStatusFilter.ALL) {
                        Badge { Text("!") }
                    }
                }
            ) {
                IconButton(onClick = { filterSheetOpen = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                }
            }
            IconButton(onClick = onSearchActivate) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        if (searchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search by title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                trailingIcon = {
                    IconButton(onClick = onSearchClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                }
            )
        }

        if (filterSheetOpen) {
            ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Filter", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MangaStatusFilter.entries.forEach { option ->
                            FilterChip(
                                selected = option == filterOption,
                                onClick = {
                                    onFilterSelect(option)
                                    filterSheetOpen = false
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun BoxWithMenu(
    expanded: Boolean,
    onMenuDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box {
        content()
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onPickFolder: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    if (state.items.isEmpty() && !state.scanning) {
        val searching = state.searchQuery.isNotBlank()
        CenteredMessage(
            title = if (searching) "No matches" else "No manga found",
            message = if (searching) {
                "Try a different search term or clear filters."
            } else {
                "The selected folder does not contain any recognizable manga."
            }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickFolder) { Text("Choose folder") }
                Button(onClick = { viewModel.refresh() }) { Text("Rescan") }
            }
        }
        return
    }

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        GridCells.Fixed(4)
    } else {
        GridCells.Adaptive(minSize = 110.dp)
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.recentProgress.isNotEmpty() && !state.searchActive) {
            item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Recently Read", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems(state.recentProgress) { progress ->
                            RecentlyReadCard(
                                progress = progress,
                                coverPath = progress.coverPath,
                                coverArchivePath = progress.coverArchivePath,
                                coverEntryName = progress.coverEntryName,
                                onClick = { onOpenDetail(progress.mangaId) },
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.scanning) {
            item(key = "scan_progress", span = { GridItemSpan(maxLineSpan) }) {
                ScanProgressSection(
                    current = state.scanCurrent,
                    total = state.scanTotal,
                    onCancel = viewModel::cancelScan
                )
            }
        }

        items(state.items, key = { it.id }) { item ->
            MangaCard(
                item = item,
                progress = state.progressByMangaId[item.id],
                onClick = { onOpenDetail(item.id) }
            )
        }
    }
}

@Composable
private fun ScanProgressSection(current: Int, total: Int, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Scanning... $current / $total",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        LinearProgressIndicator(
            progress = { if (total > 0) current.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ScanWarningsDialog(warnings: List<ScanWarning>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Scan warnings") },
        text = {
            Column {
                warnings.take(MAX_DIALOG_WARNINGS).forEach { warning ->
                    Text("${warning.path}: ${warning.message}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (warnings.size > MAX_DIALOG_WARNINGS) {
                    Text(
                        "...and ${warnings.size - MAX_DIALOG_WARNINGS} more",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    )
}

@Composable
private fun CenteredMessage(
    title: String,
    message: String,
    actions: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        actions()
    }
}

private const val MAX_DIALOG_WARNINGS = 8
