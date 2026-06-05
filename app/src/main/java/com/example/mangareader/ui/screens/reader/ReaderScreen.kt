package com.example.mangareader.ui.screens.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.mangareader.ui.reader.ReaderUiState
import com.example.mangareader.ui.reader.ReaderViewModel
import com.example.mangareader.domain.model.ReadingMode
import com.example.mangareader.data.preferences.PreferencesRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    mangaId: String,
    chapterIndex: Int,
    pageIndex: Int,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(
        key = "reader_${mangaId}_${chapterIndex}_$pageIndex",
        factory = ReaderViewModel.factory(mangaId, chapterIndex, pageIndex)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val saveAndGoBack = { viewModel.saveBeforeExit(onBack) }
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, state.loading, settingsVisible) {
        if (controlsVisible && !settingsVisible && !state.loading && state.pages.isNotEmpty()) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    BackHandler(onBack = saveAndGoBack)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.saveImmediately()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            state.loading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )

            state.pages.isNotEmpty() -> when (state.readingMode) {
                ReadingMode.VERTICAL -> VerticalReader(
                    state = state,
                    onPageVisible = viewModel::onPageVisible,
                    onLastPageReached = viewModel::onLastPageReached,
                    onReaderTap = { controlsVisible = !controlsVisible }
                )
                ReadingMode.HORIZONTAL -> HorizontalReader(
                    state = state,
                    onPageVisible = viewModel::onPageVisible,
                    onLastPageReached = viewModel::onLastPageReached,
                    onReaderTap = { controlsVisible = !controlsVisible }
                )
            }

            else -> ReaderError(
                message = state.errorMessage ?: "This chapter could not be opened.",
                onBack = onBack,
                onRetry = viewModel::retry
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !state.loading && state.pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                chapterTitle = state.chapter?.title.orEmpty(),
                currentPage = state.currentPageIndex + 1,
                totalPages = state.pages.size,
                onBack = saveAndGoBack,
                onOpenSettings = {
                    controlsVisible = true
                    settingsVisible = true
                }
            )
        }
    }

    if (settingsVisible) {
        ReaderSettingsSheet(
            readingMode = state.readingMode,
            imageWidthFraction = state.imageWidthFraction,
            onReadingModeChange = viewModel::setReadingMode,
            onImageWidthChange = viewModel::previewImageWidth,
            onImageWidthChangeFinished = viewModel::saveImageWidth,
            onDismiss = { settingsVisible = false }
        )
    }
}

@Composable
private fun VerticalReader(
    state: ReaderUiState,
    onPageVisible: (Int) -> Unit,
    onLastPageReached: () -> Unit,
    onReaderTap: () -> Unit
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.currentPageIndex
            .coerceIn(0, state.pages.lastIndex)
    )

    LaunchedEffect(listState, state.pages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPageVisible)
    }

    LaunchedEffect(listState, state.pages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.index == state.pages.lastIndex }
        }.distinctUntilChanged().collect { reachedLastPage ->
            if (reachedLastPage) onLastPageReached()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(onReaderTap) {
                detectTapGestures(onTap = { onReaderTap() })
            }
    ) {
        itemsIndexed(
            items = state.pages,
            key = { index, page -> "${page.absolutePath}:$index" }
        ) { index, page ->
            ReaderPage(
                page = page,
                pageNumber = index + 1,
                totalPages = state.pages.size,
                imageWidthFraction = state.imageWidthFraction
            )
        }
    }
}

@Composable
private fun HorizontalReader(
    state: ReaderUiState,
    onPageVisible: (Int) -> Unit,
    onLastPageReached: () -> Unit,
    onReaderTap: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentPageIndex.coerceIn(0, state.pages.lastIndex),
        pageCount = { state.pages.size }
    )

    LaunchedEffect(pagerState, state.pages.size) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onPageVisible(page)
                if (page == state.pages.lastIndex) onLastPageReached()
            }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(onReaderTap) {
                detectTapGestures(onTap = { onReaderTap() })
            }
    ) { index ->
        HorizontalReaderPage(
            page = state.pages[index],
            pageNumber = index + 1,
            totalPages = state.pages.size,
            imageWidthFraction = state.imageWidthFraction
        )
    }
}

@Composable
private fun ReaderPage(
    page: File,
    pageNumber: Int,
    totalPages: Int,
    imageWidthFraction: Float
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = page,
            contentDescription = "Page $pageNumber of $totalPages",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(imageWidthFraction),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            error = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Page $pageNumber could not be displayed",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun HorizontalReaderPage(
    page: File,
    pageNumber: Int,
    totalPages: Int,
    imageWidthFraction: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = page,
                    contentDescription = "Page $pageNumber of $totalPages",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(imageWidthFraction),
                    loading = { HorizontalPageLoading() },
                    error = { HorizontalPageError(pageNumber) }
                )
            }
        }
    }
}

@Composable
private fun HorizontalPageLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun HorizontalPageError(pageNumber: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 280.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Page $pageNumber could not be displayed",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReaderTopBar(
    chapterTitle: String,
    currentPage: Int,
    totalPages: Int,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.82f),
        contentColor = Color.White,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "$currentPage / $totalPages",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Tune, contentDescription = "Reader settings")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    readingMode: ReadingMode,
    imageWidthFraction: Float,
    onReadingModeChange: (ReadingMode) -> Unit,
    onImageWidthChange: (Float) -> Unit,
    onImageWidthChangeFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Reader Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Reading mode",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            ReaderSettingOption(
                title = "Vertical scrolling",
                subtitle = "Scroll continuously through every page.",
                selected = readingMode == ReadingMode.VERTICAL,
                onClick = { onReadingModeChange(ReadingMode.VERTICAL) }
            )
            ReaderSettingOption(
                title = "Horizontal pages",
                subtitle = "Swipe left or right one page at a time.",
                selected = readingMode == ReadingMode.HORIZONTAL,
                onClick = { onReadingModeChange(ReadingMode.HORIZONTAL) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Image width",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(imageWidthFraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Slider(
                value = imageWidthFraction,
                onValueChange = onImageWidthChange,
                onValueChangeFinished = onImageWidthChangeFinished,
                valueRange = PreferencesRepository.MIN_IMAGE_WIDTH..PreferencesRepository.MAX_IMAGE_WIDTH,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Applies to pages in both vertical and horizontal reading modes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReaderSettingOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val CONTROLS_AUTO_HIDE_MS = 3_000L

@Composable
private fun ReaderError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chapter unavailable",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Go back") }
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
