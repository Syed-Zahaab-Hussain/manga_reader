package com.example.mangareader.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mangareader.domain.model.ChapterItem
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.MangaProgress
import com.example.mangareader.ui.components.CoverImage
import com.example.mangareader.ui.detail.DetailUiState
import com.example.mangareader.ui.detail.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mangaId: String,
    onBack: () -> Unit,
    onOpenChapter: (mangaId: String, chapterIndex: Int, pageIndex: Int) -> Unit,
    viewModel: DetailViewModel = viewModel(
        key = "detail_$mangaId",
        factory = DetailViewModel.factory(mangaId)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.manga?.title ?: "Manga Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
        }
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            state.manga != null -> DetailContent(
                state = state,
                onToggleChapterOrder = viewModel::toggleChapterOrder,
                onOpenChapter = onOpenChapter,
                modifier = Modifier.padding(innerPadding)
            )

            else -> DetailError(
                message = state.errorMessage ?: "This manga could not be found.",
                onBack = onBack,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailUiState,
    onToggleChapterOrder: () -> Unit,
    onOpenChapter: (mangaId: String, chapterIndex: Int, pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val manga = requireNotNull(state.manga)
    val chapters = if (state.chaptersAscending) {
        manga.chapters.sortedBy { it.index }
    } else {
        manga.chapters.sortedByDescending { it.index }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "cover") {
            CoverBanner(manga)
        }

        state.progress?.let { progress ->
            manga.chapters.firstOrNull { it.index == progress.chapterIndex }?.let { chapter ->
                item(key = "continue") {
                    ContinueReadingCard(
                        chapter = chapter,
                        progress = progress,
                        onClick = {
                            onOpenChapter(manga.id, chapter.index, progress.pageIndex)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }
        }

        item(key = "chapter_header") {
            ChapterHeader(
                chapterCount = manga.chapters.size,
                ascending = state.chaptersAscending,
                onToggleOrder = onToggleChapterOrder
            )
        }

        items(chapters, key = { it.index }) { chapter ->
            ChapterRow(
                chapter = chapter,
                progress = state.progress,
                onClick = {
                    val savedPage = state.progress
                        ?.takeIf { it.chapterIndex == chapter.index }
                        ?.pageIndex
                        ?: 0
                    onOpenChapter(manga.id, chapter.index, savedPage)
                }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun CoverBanner(manga: MangaItem) {
    var titleExpanded by remember(manga.id) { mutableStateOf(false) }
    var titleWasTruncated by remember(manga.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        CoverImage(
            coverPath = manga.coverPath,
            archivePath = manga.coverArchivePath,
            entryName = manga.coverEntryName,
            contentDescription = manga.title,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                overflow = if (titleExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!titleExpanded) titleWasTruncated = result.hasVisualOverflow
                },
                modifier = Modifier
                    .animateContentSize()
                    .clickable(enabled = titleWasTruncated) {
                        titleExpanded = !titleExpanded
                    }
            )
            if (titleWasTruncated) {
                Text(
                    text = if (titleExpanded) "Tap title to collapse" else "Tap title to expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = chapterCountLabel(manga.chapters.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(
    chapter: ChapterItem,
    progress: MangaProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Continue Reading",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pageProgressLabel(progress.pageIndex, chapter.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Continue")
            }
        }
    }
}

@Composable
private fun ChapterHeader(
    chapterCount: Int,
    ascending: Boolean,
    onToggleOrder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = chapterCountLabel(chapterCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleOrder) {
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = if (ascending) {
                    "Show newest chapters first"
                } else {
                    "Show oldest chapters first"
                }
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterItem,
    progress: MangaProgress?,
    onClick: () -> Unit
) {
    val completed = chapter.index in progress?.completedChapters.orEmpty()
    val inProgress = !completed && progress?.chapterIndex == chapter.index

    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (inProgress) {
                        pageProgressLabel(progress?.pageIndex ?: 0, chapter.pageCount)
                    } else {
                        pageCountLabel(chapter.pageCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                completed -> ChapterBadge("DONE", MaterialTheme.colorScheme.tertiary)
                inProgress -> ChapterBadge("CONTINUE", MaterialTheme.colorScheme.primary)
                else -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChapterBadge(label: String, color: Color) {
    Surface(
        color = color,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailError(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Manga unavailable",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back to Library") }
    }
}

private fun chapterCountLabel(count: Int): String =
    "$count ${if (count == 1) "chapter" else "chapters"}"

private fun pageCountLabel(count: Int): String =
    "$count ${if (count == 1) "page" else "pages"}"

private fun pageProgressLabel(pageIndex: Int, pageCount: Int): String {
    val currentPage = (pageIndex + 1).coerceAtLeast(1)
    return if (pageCount > 0) {
        "Page ${currentPage.coerceAtMost(pageCount)} of $pageCount"
    } else {
        "Page $currentPage"
    }
}
