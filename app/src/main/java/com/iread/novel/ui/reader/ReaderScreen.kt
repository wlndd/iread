package com.iread.novel.ui.reader

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

private val PaperYellow = Color(0xFFFFF8E7)
private val InkBrown = Color(0xFF3C3227)

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOffsetChanged: (Int) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = PaperYellow, contentColor = InkBrown) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = InkBrown)
            }
            state.errorMessage != null -> ReaderError(state.errorMessage, onBack)
            state.chapters.isEmpty() -> ReaderError("这本书没有可阅读的章节", onBack)
            else -> ReaderContent(state, onBack, onOpenChapter, onOffsetChanged)
        }
    }
}

@Composable
private fun ReaderError(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("返回书架") }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOffsetChanged: (Int) -> Unit,
) {
    val chapter = state.chapters[state.chapterIndex]
    val paragraphs = remember(chapter.body) { chapter.body.paragraphsWithOffsets() }
    val listState = remember(state.chapterIndex) { LazyListState() }
    val currentOffset by rememberUpdatedState(state.characterOffset)
    var restoring by remember(state.chapterIndex) { mutableStateOf(true) }
    var restoredItemIndex by remember(state.chapterIndex) { mutableStateOf(-1) }

    LaunchedEffect(state.chapterIndex) {
        val targetParagraph = paragraphs.indexOfLast { it.startOffset <= currentOffset }.coerceAtLeast(0)
        restoredItemIndex = targetParagraph + 1
        listState.scrollToItem(targetParagraph + 1)
    }
    LaunchedEffect(state.chapterIndex, listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { itemIndex ->
                if (restoring) {
                    if (itemIndex == restoredItemIndex) restoring = false
                    return@collect
                }
                val paragraphIndex = (itemIndex - 1).coerceIn(0, paragraphs.lastIndex)
                onOffsetChanged(paragraphs[paragraphIndex].startOffset)
            }
    }

    Column(Modifier.fillMaxSize()) {
        ReaderTopBar(title = state.bookTitle, onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 20.dp),
        ) {
            item(key = "chapter-${chapter.index}") {
                Text(
                    text = chapter.title,
                    style = chapterTitleStyle,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                )
            }
            itemsIndexed(paragraphs, key = { index, _ -> "chapter-${chapter.index}-paragraph-$index" }) { _, paragraph ->
                Text(
                    text = paragraph.text,
                    style = paragraphStyle,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                )
            }
        }
        ReaderChapterNavigator(
            chapterIndex = state.chapterIndex,
            chapterCount = state.chapters.size,
            onOpenChapter = onOpenChapter,
        )
    }
}

@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PaperYellow).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回书架", tint = InkBrown)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, color = InkBrown),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ReaderChapterNavigator(chapterIndex: Int, chapterCount: Int, onOpenChapter: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PaperYellow).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onOpenChapter(chapterIndex - 1) }, enabled = chapterIndex > 0) {
            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "上一章", tint = InkBrown)
        }
        Text("第 ${chapterIndex + 1} / $chapterCount 章", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = InkBrown))
        IconButton(onClick = { onOpenChapter(chapterIndex + 1) }, enabled = chapterIndex < chapterCount - 1) {
            Icon(Icons.Outlined.ArrowForwardIos, contentDescription = "下一章", tint = InkBrown)
        }
    }
}

private val chapterTitleStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    color = InkBrown,
)

private val paragraphStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 19.sp,
    lineHeight = 32.sp,
    textIndent = androidx.compose.ui.text.style.TextIndent(firstLine = 2.em),
    color = InkBrown,
)

private data class ReaderParagraph(val text: String, val startOffset: Int)

private fun String.paragraphsWithOffsets(): List<ReaderParagraph> {
    if (isEmpty()) return listOf(ReaderParagraph("", 0))
    val result = mutableListOf<ReaderParagraph>()
    var start = 0
    split('\n').forEach { rawParagraph ->
        result += ReaderParagraph(rawParagraph.removeSuffix("\r"), start)
        start += rawParagraph.length + 1
    }
    return result
}
