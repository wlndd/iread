package com.iread.novel.ui.reader

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import com.iread.novel.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOffsetChanged: (Int) -> Unit,
    onPreferencesChanged: (ReaderPreferences) -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onOpenBookmark: (Bookmark) -> Unit = {},
    onPositionChanged: (Int, Int) -> Unit = { _, offset -> onOffsetChanged(offset) },
) {
    val night = state.preferences.theme == ReaderTheme.NIGHT
    val paper = when (state.preferences.theme) {
        ReaderTheme.PAPER -> Color(0xFFFFF8E7)
        ReaderTheme.BLUE -> Color(0xFFE8F0F5)
        ReaderTheme.NIGHT -> Color(0xFF171A1D)
    }
    val ink = if (night) Color(0xFFB8B8AF) else Color(0xFF343D3B)
    val view = LocalView.current
    DisposableEffect(view, night) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !night
        controller?.isAppearanceLightNavigationBars = !night
        onDispose {
            oldStatus?.let { controller?.isAppearanceLightStatusBars = it }
            oldNav?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
    val colors = if (night) darkColorScheme(surface = paper, background = paper, onSurface = ink, primary = Color(0xFFA5C4B3))
        else lightColorScheme(surface = paper, background = paper, onSurface = ink, primary = Color(0xFF466857))
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize().testTag("reader-" + state.preferences.theme.name), color = paper, contentColor = ink) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null || state.chapters.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.errorMessage ?: "这本书没有可阅读的章节")
                    TextButton(onClick = onBack) { Text("返回书架") }
                }
                else -> ReaderBody(state, paper, ink, onBack, onOpenChapter, onOffsetChanged,
                    onPreferencesChanged, onToggleBookmark, onOpenBookmark, onPositionChanged)
            }
        }
    }
}

@Composable
private fun ReaderBody(
    state: ReaderUiState, paper: Color, ink: Color, onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit, onOffsetChanged: (Int) -> Unit,
    onPreferencesChanged: (ReaderPreferences) -> Unit, onToggleBookmark: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit, onPositionChanged: (Int, Int) -> Unit,
) {
    var tools by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    val chapter = state.chapters[state.chapterIndex]
    var pageLabel by remember(chapter.index) { mutableStateOf("") }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tools) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回书架") }
                    Text(state.bookTitle, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Serif)
                    IconButton(onClick = onToggleBookmark) {
                        val marked = state.bookmarks.any { it.chapterIndex == state.chapterIndex && it.characterOffset == state.characterOffset }
                        Icon(if (marked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (marked) "取消书签" else "添加书签")
                    }
                } else {
                    Text(chapter.title, Modifier.padding(horizontal = 16.dp).weight(1f), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (tools) Text(state.notice ?: chapter.title, Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 24.dp), fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Spacer(Modifier.height(32.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
                val density = LocalDensity.current
                val width = with(density) { maxWidth.roundToPx() }
                val height = with(density) { maxHeight.roundToPx() }
                val font = with(density) { state.preferences.fontSize.sp.toPx() }
                val measured by produceState<MeasuredChapter?>(null, chapter.body, width, height, font, state.preferences.lineSpacing) {
                    value = null
                    value = withContext(Dispatchers.Default) { MeasuredChapter(chapter.body, width, height, font, state.preferences.lineSpacing) }
                }
                val bookLayout = measured
                if (bookLayout != null) {
                    val currentPage = bookLayout.pageForOffset(state.characterOffset)
                    val turn: (Int) -> Unit = { direction ->
                        val next = currentPage + direction
                        when {
                            next in bookLayout.pages.indices -> onOffsetChanged(bookLayout.pages[next].start)
                            direction > 0 && state.chapterIndex < state.chapters.lastIndex -> onPositionChanged(state.chapterIndex + 1, 0)
                            direction < 0 && state.chapterIndex > 0 -> onPositionChanged(state.chapterIndex - 1, Int.MAX_VALUE)
                        }
                    }
                    SideEffect { pageLabel = "第 ${currentPage + 1} / ${bookLayout.pages.size} 页" }
                    if (state.preferences.mode == ReaderMode.PAGED) {
                        val latestTurn by rememberUpdatedState(turn)
                        var drag = remember { 0f }
                        val threshold = with(density) { 36.dp.toPx() }
                        PageCanvas(bookLayout, currentPage, ink,
                            Modifier.fillMaxSize().testTag("reader-page")
                                .pointerInput(bookLayout) {
                                    detectTapGestures { position ->
                                        when {
                                            position.x < size.width * .3f -> latestTurn(-1)
                                            position.x > size.width * .7f -> latestTurn(1)
                                            else -> tools = !tools
                                        }
                                    }
                                }.pointerInput(bookLayout) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { drag = 0f },
                                        onDragEnd = { if (drag < -threshold) latestTurn(1) else if (drag > threshold) latestTurn(-1) },
                                        onDragCancel = { drag = 0f },
                                        onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                                    )
                                }.semantics {
                                    customActions = listOf(CustomAccessibilityAction("上一页") { latestTurn(-1); true },
                                        CustomAccessibilityAction("下一页") { latestTurn(1); true },
                                        CustomAccessibilityAction("显示或隐藏工具栏") { tools = !tools; true })
                                })
                    } else {
                        key(chapter.index, bookLayout) {
                            val initialLine = bookLayout.layout.getLineForOffset(state.characterOffset.coerceIn(0, chapter.body.length))
                            val list = remember { LazyListState(currentPage, bookLayout.layout.getLineTop(initialLine) - bookLayout.pages[currentPage].top) }
                            LaunchedEffect(list) {
                                snapshotFlow { bookLayout.offsetAt(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
                                    .distinctUntilChanged().drop(1).collect { onOffsetChanged(it) }
                            }
                            // Jumps from bookmarks/TOC in the same chapter must move the viewport too.
                            LaunchedEffect(state.characterOffset) {
                                val visible = bookLayout.offsetAt(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
                                if (!list.isScrollInProgress && kotlin.math.abs(visible - state.characterOffset) > 1) {
                                    val target = bookLayout.pageForOffset(state.characterOffset)
                                    val line = bookLayout.layout.getLineForOffset(state.characterOffset)
                                    list.scrollToItem(target, bookLayout.layout.getLineTop(line) - bookLayout.pages[target].top)
                                }
                            }
                            LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("reader-scroll")) {
                                itemsIndexed(bookLayout.pages) { i, page ->
                                    PageCanvas(bookLayout, i, ink, Modifier.fillMaxWidth().height(with(density) { (page.bottom - page.top).toDp() })
                                        .pointerInput(Unit) { detectTapGestures { tools = !tools } })
                                }
                                item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        TextButton(enabled = state.chapterIndex > 0, onClick = { onPositionChanged(state.chapterIndex - 1, Int.MAX_VALUE) }) { Text("上一章") }
                                        TextButton(enabled = state.chapterIndex < state.chapters.lastIndex, onClick = { onPositionChanged(state.chapterIndex + 1, 0) }) { Text("继续下一章") }
                                    }
                                }
                            }
                        }
                    }
                } else CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("第 ${state.chapterIndex + 1} / ${state.chapters.size} 章", style = MaterialTheme.typography.labelSmall)
                Text(pageLabel, style = MaterialTheme.typography.labelSmall)
            }
            // Reserve control height permanently so toggling controls never repaginates text.
            Box(Modifier.fillMaxWidth().height(96.dp)) {
                if (tools) Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { panel = "目录" }) { Text("目录") }
                        TextButton(onClick = { panel = "书签" }) { Text("书签") }
                        TextButton(onClick = { panel = "阅读设置" }) { Text("字号与主题") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = state.chapterIndex > 0, onClick = { onOpenChapter(state.chapterIndex - 1) }, modifier = Modifier.semantics { contentDescription = "上一章" }) { Text("上一章") }
                        TextButton(onClick = { onPreferencesChanged(state.preferences.copy(mode = if (state.preferences.mode == ReaderMode.PAGED) ReaderMode.SCROLL else ReaderMode.PAGED)) }) {
                            Text(if (state.preferences.mode == ReaderMode.PAGED) "切换上下滚动" else "切换左右翻页")
                        }
                        TextButton(enabled = state.chapterIndex < state.chapters.lastIndex, onClick = { onOpenChapter(state.chapterIndex + 1) }, modifier = Modifier.semantics { contentDescription = "下一章" }) { Text("下一章") }
                    }
                }
            }
        }
    }
    when (panel) {
        "目录" -> AlertDialog(onDismissRequest = { panel = null }, title = { Text("目录") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp), state = rememberLazyListState(state.chapterIndex)) {
                items(state.chapters) { item ->
                    TextButton(onClick = { onOpenChapter(item.index); panel = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(item.title, Modifier.fillMaxWidth(), color = if (item.index == state.chapterIndex) MaterialTheme.colorScheme.primary else ink)
                    }
                }
            } }, confirmButton = { TextButton(onClick = { panel = null }) { Text("关闭") } })
        "书签" -> AlertDialog(onDismissRequest = { panel = null }, title = { Text("书签") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (state.bookmarks.isEmpty()) item { Text("暂无书签。点击右上角书签图标添加。") }
                items(state.bookmarks) { mark ->
                    TextButton(onClick = { onOpenBookmark(mark); panel = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("${state.chapters.getOrNull(mark.chapterIndex)?.title.orEmpty()}\n${mark.snippet}", maxLines = 3)
                    }
                }
            } }, confirmButton = { TextButton(onClick = { panel = null }) { Text("关闭") } })
        "阅读设置" -> AlertDialog(onDismissRequest = { panel = null }, title = { Text("阅读设置") },
            text = { Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = state.preferences.fontSize > 14, onClick = { onPreferencesChanged(state.preferences.copy(fontSize = state.preferences.fontSize - 1)) }) { Text("字号 −") }
                    Text("${state.preferences.fontSize}", Modifier.align(Alignment.CenterVertically))
                    TextButton(enabled = state.preferences.fontSize < 30, onClick = { onPreferencesChanged(state.preferences.copy(fontSize = state.preferences.fontSize + 1)) }) { Text("字号 ＋") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = state.preferences.lineSpacing > 1.2f, onClick = { onPreferencesChanged(state.preferences.copy(lineSpacing = (state.preferences.lineSpacing - .1f).coerceAtLeast(1.2f))) }) { Text("行距 −") }
                    Text(String.format(java.util.Locale.ROOT, "%.1f", state.preferences.lineSpacing), Modifier.align(Alignment.CenterVertically))
                    TextButton(enabled = state.preferences.lineSpacing < 2.4f, onClick = { onPreferencesChanged(state.preferences.copy(lineSpacing = (state.preferences.lineSpacing + .1f).coerceAtMost(2.4f))) }) { Text("行距 ＋") }
                }
                listOf(ReaderTheme.PAPER to "暖纸黄", ReaderTheme.BLUE to "雾霭蓝", ReaderTheme.NIGHT to "深夜黑").forEach { (theme, label) ->
                    FilterChip(selected = state.preferences.theme == theme, onClick = { onPreferencesChanged(state.preferences.copy(theme = theme)) }, label = { Text(label) })
                }
            } }, confirmButton = { TextButton(onClick = { panel = null }) { Text("完成") } })
    }
}

@Composable
private fun PageCanvas(chapter: MeasuredChapter, index: Int, ink: Color, modifier: Modifier) {
    val page = chapter.pages[index]
    Canvas(modifier.semantics { text = AnnotatedString(chapter.body.substring(page.start, page.end)) }) {
        chapter.paint.color = ink.toArgb()
        drawContext.canvas.nativeCanvas.apply {
            val save = save()
            clipRect(0f, 0f, size.width, size.height)
            translate(0f, -page.top.toFloat())
            chapter.layout.draw(this)
            restoreToCount(save)
        }
    }
}
