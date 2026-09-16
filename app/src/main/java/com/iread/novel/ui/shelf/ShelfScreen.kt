package com.iread.novel.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iread.novel.core.model.BookSummary

@Composable
fun ShelfScreen(
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    state: ShelfUiState,
    onEdit: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var editing by remember { mutableStateOf<BookSummary?>(null) }
    var deleting by remember { mutableStateOf<BookSummary?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onDismissMessage() } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("我的书架", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("留一点时间，给故事。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (state.books.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("书架还是空的", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("从设置导入一本书，让故事开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) { Text("去导入书籍") }
                }
            }
            items(state.books, key = { it.id }) { book ->
                BookRow(book, { onOpenBook(book.id) }, { editing = book }, { deleting = book })
                HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    editing?.let { book ->
        var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
        var author by rememberSaveable(book.id) { mutableStateOf(book.author) }
        AlertDialog(
            onDismissRequest = { editing = null }, title = { Text("编辑书籍信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("书名") }, singleLine = true)
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("作者") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onEdit(book.id, title, author); editing = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
    deleting?.let { book ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("删除本书") },
            text = { Text("确定从书架删除《${book.title}》吗？原始文件不会被删除。") },
            confirmButton = { TextButton(onClick = { onDelete(book.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun BookRow(book: BookSummary, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        GeneratedCover(book)
        Column(Modifier.weight(1f).padding(start = 18.dp, end = 4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Text("${book.author} · ${book.unreadChapters}章未读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多：${book.title}", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("编辑书籍信息") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("删除本书") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun GeneratedCover(book: BookSummary) {
    val palettes = listOf(Color(0xFF789589), Color(0xFF8F9BAB), Color(0xFFB09B84), Color(0xFF939879))
    val ink = palettes[(book.id.hashCode() and Int.MAX_VALUE) % palettes.size]
    Box(Modifier.size(width = 70.dp, height = 96.dp).clip(RoundedCornerShape(5.dp)).background(ink.copy(alpha = .17f))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .65f), size.width * .19f, Offset(size.width * .72f, size.height * .25f))
            for (layer in 0..2) {
                val ridge = Path().apply {
                    moveTo(0f, size.height * (.63f + layer * .12f))
                    cubicTo(size.width * .3f, size.height * (.32f + layer * .15f), size.width * .62f, size.height * (.92f - layer * .05f), size.width, size.height * (.56f + layer * .14f))
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(ridge, ink.copy(alpha = .22f + layer * .18f))
            }
            drawLine(Color.White.copy(alpha = .25f), Offset(4.dp.toPx(), 0f), Offset(4.dp.toPx(), size.height), 1.dp.toPx())
        }
        Text(book.title.take(4), modifier = Modifier.padding(9.dp), color = Color(0xFF354C43), fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
    }
}
