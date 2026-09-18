package com.iread.novel.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iread.novel.core.model.ReaderPreferences
import com.iread.novel.core.model.ReaderMode
import com.iread.novel.core.model.ReaderTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportUri: (List<Uri>) -> Unit,
    state: SettingsUiState,
    onImportFolder: (Uri) -> Unit = {},
    preferences: ReaderPreferences = ReaderPreferences(),
    onPreferencesChanged: (ReaderPreferences) -> Unit = {},
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onImportUri)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onImportFolder) }
    val idle = state.importingCount == 0 && !state.scanning
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回书架") }
                    Text("设置", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            item { GroupLabel("本地书库") }
            item {
                    Column {
                        SettingsRow("导入文件", "从设备选择 TXT / EPUB 书籍", Icons.AutoMirrored.Outlined.NoteAdd, idle) {
                            picker.launch(arrayOf("text/plain", "application/epub+zip", "application/zip", "application/octet-stream"))
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsRow("扫描文件夹", "导入所选文件夹及子文件夹中的书籍", Icons.Outlined.FolderOpen, idle) { folderPicker.launch(null) }
                    }
            }
            item { Text("书籍将保存在应用内，原始文件保持不变。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.scanning) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在扫描文件夹…")
                }
            }
            if (state.importingCount > 0) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在导入 · 剩余 ${state.importingCount} 本", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.importedCount + state.duplicateCount + state.failureCount > 0) item {
                Text("已导入 ${state.importedCount} 本 · 重复 ${state.duplicateCount} 本 · 失败 ${state.failureCount} 本", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(state.messages) { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { GroupLabel("阅读偏好") }
            item { Text("默认主题", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ReaderTheme.PAPER to "暖纸黄", ReaderTheme.BLUE to "雾霭蓝", ReaderTheme.NIGHT to "深夜黑").forEach { (theme, label) ->
                        FilterChip(selected = preferences.theme == theme, onClick = { onPreferencesChanged(preferences.copy(theme = theme)) }, label = { Text(label) })
                    }
                }
            }
            item { Text("默认阅读方式", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = preferences.mode == ReaderMode.PAGED, onClick = { onPreferencesChanged(preferences.copy(mode = ReaderMode.PAGED)) }, label = { Text("左右翻页") })
                    FilterChip(selected = preferences.mode == ReaderMode.SCROLL, onClick = { onPreferencesChanged(preferences.copy(mode = ReaderMode.SCROLL)) }, label = { Text("上下滚动") })
                }
            }
            item {
                PreferenceStepper("字号", "${preferences.fontSize}", preferences.fontSize > 14, preferences.fontSize < 30,
                    { onPreferencesChanged(preferences.copy(fontSize = preferences.fontSize - 1)) },
                    { onPreferencesChanged(preferences.copy(fontSize = preferences.fontSize + 1)) })
            }
            item {
                PreferenceStepper("行距", String.format(java.util.Locale.ROOT, "%.1f 倍", preferences.lineSpacing), preferences.lineSpacing > 1.2f, preferences.lineSpacing < 2.4f,
                    { onPreferencesChanged(preferences.copy(lineSpacing = (preferences.lineSpacing - .1f).coerceAtLeast(1.2f))) },
                    { onPreferencesChanged(preferences.copy(lineSpacing = (preferences.lineSpacing + .1f).coerceAtMost(2.4f))) })
            }
            item { Text("阅读时也可调整。每本书会记住自己的阅读方式与位置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PreferenceStepper(label: String, value: String, canDecrease: Boolean, canIncrease: Boolean, decrease: () -> Unit, increase: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = decrease, enabled = canDecrease) { Text("减小$label") }
        Text(value, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = increase, enabled = canIncrease) { Text("增大$label") }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, modifier = Modifier.padding(top = 22.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsRow(title: String, detail: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
