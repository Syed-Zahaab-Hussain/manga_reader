package com.example.mangareader.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    var pageJumpVisible by remember { mutableStateOf(false) }
    var jumpTargetPage by remember { mutableStateOf<Int?>(null) }
    var unlockButtonVisible by remember { mutableStateOf(false) }
    val readerTap = {
        if (state.controlsLocked) {
            unlockButtonVisible = true
        } else {
            controlsVisible = !controlsVisible
        }
    }
    val showStatusBar = state.loading ||
        state.pages.isEmpty() ||
        settingsVisible ||
        pageJumpVisible ||
        (controlsVisible && !state.controlsLocked)

    ReaderStatusBar(show = showStatusBar)

    LaunchedEffect(state.controlsLocked) {
        if (state.controlsLocked) controlsVisible = false
    }

    LaunchedEffect(unlockButtonVisible, state.controlsLocked) {
        if (unlockButtonVisible && state.controlsLocked) {
            delay(UNLOCK_BUTTON_HIDE_MS)
            unlockButtonVisible = false
        }
    }

    LaunchedEffect(controlsVisible, state.loading, settingsVisible, pageJumpVisible) {
        if (
            controlsVisible &&
            !state.controlsLocked &&
            !settingsVisible &&
            !pageJumpVisible &&
            !state.loading &&
            state.pages.isNotEmpty()
        ) {
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

            state.pages.isNotEmpty() -> key(state.chapter?.index) {
                when (state.readingMode) {
                    ReadingMode.VERTICAL -> VerticalReader(
                        state = state,
                        onPageVisible = viewModel::onPageVisible,
                        onLastPageReached = viewModel::onLastPageReached,
                        onReaderTap = readerTap,
                        jumpTargetPage = jumpTargetPage,
                        onJumpHandled = { jumpTargetPage = null }
                    )
                    ReadingMode.HORIZONTAL -> HorizontalReader(
                        state = state,
                        onPageVisible = viewModel::onPageVisible,
                        onLastPageReached = viewModel::onLastPageReached,
                        onReaderTap = readerTap,
                        jumpTargetPage = jumpTargetPage,
                        onJumpHandled = { jumpTargetPage = null }
                    )
                }
            }

            else -> ReaderError(
                message = state.errorMessage ?: "This chapter could not be opened.",
                onBack = onBack,
                onRetry = viewModel::retry
            )
        }

        AnimatedVisibility(
            visible = controlsVisible &&
                !state.controlsLocked &&
                !state.loading &&
                state.pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                chapterTitle = state.chapter?.title.orEmpty(),
                currentPage = state.currentPageIndex + 1,
                totalPages = state.pages.size,
                canOpenPreviousChapter = state.canOpenPreviousChapter,
                canOpenNextChapter = state.canOpenNextChapter,
                onBack = saveAndGoBack,
                onOpenPreviousChapter = {
                    jumpTargetPage = null
                    viewModel.openPreviousChapter()
                },
                onOpenNextChapter = {
                    jumpTargetPage = null
                    viewModel.openNextChapter()
                },
                onOpenPageJump = {
                    controlsVisible = true
                    pageJumpVisible = true
                },
                onOpenSettings = {
                    controlsVisible = true
                    settingsVisible = true
                }
            )
        }

        AnimatedVisibility(
            visible = unlockButtonVisible && state.controlsLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(8.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shape = RoundedCornerShape(999.dp)
            ) {
                IconButton(
                    onClick = {
                        unlockButtonVisible = false
                        controlsVisible = true
                        viewModel.setControlsLocked(false)
                    }
                ) {
                    Icon(
                        Icons.Filled.LockOpen,
                        contentDescription = "Unlock reader controls"
                    )
                }
            }
        }
    }

    if (settingsVisible) {
        ReaderSettingsSheet(
            readingMode = state.readingMode,
            imageWidthFraction = state.imageWidthFraction,
            showPageNumbers = state.showPageNumbers,
            zoomEnabled = state.zoomEnabled,
            controlsLocked = state.controlsLocked,
            onReadingModeChange = viewModel::setReadingMode,
            onImageWidthChange = viewModel::previewImageWidth,
            onImageWidthChangeFinished = viewModel::saveImageWidth,
            onShowPageNumbersChange = viewModel::setShowPageNumbers,
            onZoomEnabledChange = viewModel::setZoomEnabled,
            onControlsLockedChange = { locked ->
                viewModel.setControlsLocked(locked)
                if (locked) {
                    settingsVisible = false
                    controlsVisible = false
                    unlockButtonVisible = false
                }
            },
            onDismiss = { settingsVisible = false }
        )
    }

    if (pageJumpVisible && state.pages.isNotEmpty()) {
        PageJumpSheet(
            currentPage = state.currentPageIndex + 1,
            totalPages = state.pages.size,
            onJump = { pageNumber ->
                jumpTargetPage = pageNumber - 1
                pageJumpVisible = false
            },
            onDismiss = { pageJumpVisible = false }
        )
    }
}

@Composable
private fun VerticalReader(
    state: ReaderUiState,
    onPageVisible: (Int) -> Unit,
    onLastPageReached: () -> Unit,
    onReaderTap: () -> Unit,
    jumpTargetPage: Int?,
    onJumpHandled: () -> Unit
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


    LaunchedEffect(jumpTargetPage) {
        jumpTargetPage?.let { target ->
            listState.animateScrollToItem(target.coerceIn(0, state.pages.lastIndex))
            onJumpHandled()
        }
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
                imageWidthFraction = state.imageWidthFraction,
                showPageNumber = state.showPageNumbers,
                zoomEnabled = state.zoomEnabled
            )
        }
    }
}

@Composable
private fun HorizontalReader(
    state: ReaderUiState,
    onPageVisible: (Int) -> Unit,
    onLastPageReached: () -> Unit,
    onReaderTap: () -> Unit,
    jumpTargetPage: Int?,
    onJumpHandled: () -> Unit
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


    LaunchedEffect(jumpTargetPage) {
        jumpTargetPage?.let { target ->
            pagerState.animateScrollToPage(target.coerceIn(0, state.pages.lastIndex))
            onJumpHandled()
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
            imageWidthFraction = state.imageWidthFraction,
            showPageNumber = state.showPageNumbers,
            zoomEnabled = state.zoomEnabled
        )
    }
}

@Composable
private fun ReaderPage(
    page: File,
    pageNumber: Int,
    totalPages: Int,
    imageWidthFraction: Float,
    showPageNumber: Boolean,
    zoomEnabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZoomablePage(enabled = zoomEnabled) { zoomModifier ->
            SubcomposeAsyncImage(
                model = page,
                contentDescription = "Page $pageNumber of $totalPages",
                contentScale = ContentScale.FillWidth,
                modifier = zoomModifier.fillMaxWidth(imageWidthFraction),
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
        if (showPageNumber) PageNumberLabel(pageNumber, totalPages)
    }
}

@Composable
private fun HorizontalReaderPage(
    page: File,
    pageNumber: Int,
    totalPages: Int,
    imageWidthFraction: Float,
    showPageNumber: Boolean,
    zoomEnabled: Boolean
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
            ZoomablePage(enabled = zoomEnabled) { zoomModifier ->
                SubcomposeAsyncImage(
                    model = page,
                    contentDescription = "Page $pageNumber of $totalPages",
                    contentScale = ContentScale.FillWidth,
                    modifier = zoomModifier.fillMaxWidth(imageWidthFraction),
                    loading = { HorizontalPageLoading() },
                    error = { HorizontalPageError(pageNumber) }
                )
            }
            if (showPageNumber) PageNumberLabel(pageNumber, totalPages)
        }
    }
}

@Composable
private fun ZoomablePage(
    enabled: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    var scale by remember(enabled) { mutableStateOf(MIN_ZOOM) }
    var offset by remember(enabled) { mutableStateOf(Offset.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = calculateZoomScale(scale, zoomChange)
        val maxOffsetX = contentSize.width * (newScale - MIN_ZOOM) / 2f
        val maxOffsetY = contentSize.height * (newScale - MIN_ZOOM) / 2f
        scale = newScale
        offset = if (newScale == MIN_ZOOM) {
            Offset.Zero
        } else {
            Offset(
                x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        content(
            Modifier
                .onSizeChanged { contentSize = it }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .then(
                    if (enabled) {
                        Modifier.transformable(
                            state = transformState,
                            lockRotationOnZoomPan = true,
                            canPan = { scale > MIN_ZOOM }
                        )
                    } else {
                        Modifier
                    }
                )
        )
    }
}

@Composable
private fun PageNumberLabel(pageNumber: Int, totalPages: Int) {
    Text(
        text = "$pageNumber / $totalPages",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.64f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
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
    canOpenPreviousChapter: Boolean,
    canOpenNextChapter: Boolean,
    onBack: () -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    onOpenPageJump: () -> Unit,
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
            IconButton(
                onClick = onOpenPreviousChapter,
                enabled = canOpenPreviousChapter
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter")
            }
            IconButton(
                onClick = onOpenNextChapter,
                enabled = canOpenNextChapter
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter")
            }
            Surface(
                onClick = onOpenPageJump,
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
private fun PageJumpSheet(
    currentPage: Int,
    totalPages: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pageText by rememberSaveable(currentPage, totalPages) {
        mutableStateOf(currentPage.toString())
    }
    val validPageNumber = pageText.toIntOrNull()?.takeIf { it in 1..totalPages }
    val validPage = validPageNumber != null
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val submit = {
        validPageNumber?.let { pageNumber ->
            keyboardController?.hide()
            onJump(pageNumber)
        }
        Unit
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Jump to Page",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter a page number from 1 to $totalPages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { input ->
                        if (input.all(Char::isDigit)) pageText = input
                    },
                    label = { Text("Page number") },
                    singleLine = true,
                    isError = pageText.isNotEmpty() && !validPage,
                    supportingText = {
                        if (pageText.isNotEmpty() && !validPage) {
                            Text("Choose a page between 1 and $totalPages.")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = submit,
                    enabled = validPage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Jump")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    readingMode: ReadingMode,
    imageWidthFraction: Float,
    showPageNumbers: Boolean,
    zoomEnabled: Boolean,
    controlsLocked: Boolean,
    onReadingModeChange: (ReadingMode) -> Unit,
    onImageWidthChange: (Float) -> Unit,
    onImageWidthChangeFinished: () -> Unit,
    onShowPageNumbersChange: (Boolean) -> Unit,
    onZoomEnabledChange: (Boolean) -> Unit,
    onControlsLockedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
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
                Spacer(modifier = Modifier.height(16.dp))
                ReaderToggleSetting(
                    title = "Pinch-to-zoom",
                    subtitle = "Zoom and pan individual pages up to 4×.",
                    checked = zoomEnabled,
                    onCheckedChange = onZoomEnabledChange
                )
                ReaderToggleSetting(
                    title = "Lock reader controls",
                    subtitle = "Keep the app bar hidden until controls are unlocked.",
                    checked = controlsLocked,
                    onCheckedChange = onControlsLockedChange
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowPageNumbersChange(!showPageNumbers) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show page numbers",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Display the current and total page count below every page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showPageNumbers,
                        onCheckedChange = onShowPageNumbersChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderToggleSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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

@Composable
private fun ReaderStatusBar(show: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    val window = activity.window

    LaunchedEffect(window, view, show) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    DisposableEffect(window, view) {
        onDispose {
            WindowCompat.getInsetsController(window, view)
                .show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val CONTROLS_AUTO_HIDE_MS = 3_000L
private const val UNLOCK_BUTTON_HIDE_MS = 3_000L
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f

internal fun calculateZoomScale(currentScale: Float, zoomChange: Float): Float =
    (currentScale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)

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
