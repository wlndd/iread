package com.iread.novel.ui.settings

import android.net.Uri
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iread.novel.core.model.ReaderPreferences
import com.iread.novel.core.model.ReaderMode
import com.iread.novel.core.model.ReaderTheme
import com.iread.novel.data.files.hasStorageScanAccess

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportUri: (List<Uri>) -> Unit,
    state: SettingsUiState,
    preferences: ReaderPreferences = ReaderPreferences(),
    onPreferencesChanged: (ReaderPreferences) -> Unit = {},
    onScanDevice: () -> Unit = {},
    onCancelScan: () -> Unit = {},
    onToggleCandidate: (String) -> Unit = {},
    onSelectAll: (Boolean) -> Unit = {},
    onDismissCandidates: () -> Unit = {},
    onImportSelected: () -> Unit = {},
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onImportUri)
    val context = LocalContext.current
    val startScan by rememberUpdatedState(onScanDevice)
    var showScanPermission by rememberSaveable { mutableStateOf(false) }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val storageSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasStorageScanAccess(context)) { permissionMessage = null; startScan() }
        else permissionMessage = "未获得文件访问权限，可重新授权或使用“导入书籍”。"
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { permissionMessage = null; startScan() }
        else permissionMessage = "未获得存储读取权限，可在系统设置中授权或使用“导入书籍”。"
    }
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
                SettingsRow("一键扫描", "扫描手机存储中的 TXT / EPUB，勾选后导入", Icons.Outlined.Refresh, idle) {
                    permissionMessage = null
                    if (hasStorageScanAccess(context)) onScanDevice() else showScanPermission = true
                }
            }
            item {
                    Column {
                        SettingsRow("导入书籍", "可多选文件，自动识别 TXT / EPUB", Icons.AutoMirrored.Outlined.NoteAdd, idle) {
                            picker.launch(arrayOf("*/*"))
                        }
                    }
            }
            item { Text("书籍将保存在应用内，原始文件保持不变。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.scanning) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Column(Modifier.weight(1f)) {
                        Text("正在扫描手机存储…")
                        Text("已检查 ${state.scanProgress.checkedFiles} 个文件，找到 ${state.scanProgress.foundBooks} 个候选", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onCancelScan) { Text("取消扫描") }
                }
            }
            if (state.importingCount > 0) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Column {
                        Text("正在导入 ${state.totalImports - state.importingCount + 1} / ${state.totalImports} 本", style = MaterialTheme.typography.bodyMedium)
                        Text(state.currentBook, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        LinearProgressIndicator(progress = { (state.totalImports - state.importingCount).toFloat() / state.totalImports.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            items(state.messages) { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            permissionMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
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
    if (showScanPermission) {
        AlertDialog(
            onDismissRequest = { showScanPermission = false },
            title = { Text("授权扫描小说文件") },
            text = { Text("授权后，iRead 将遍历手机和已挂载存储卡中可访问的共享文件，找出 TXT / EPUB 候选文件，供你勾选导入。TXT 也可能是普通文档，请自行确认。扫描不会修改或删除原文件，也不会上传文件。系统和其他应用的私有目录无法扫描。") },
            confirmButton = { TextButton(onClick = {
                showScanPermission = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val specific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    try { storageSettings.launch(specific) }
                    catch (_: android.content.ActivityNotFoundException) {
                        try { storageSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        catch (_: android.content.ActivityNotFoundException) { permissionMessage = "系统未提供文件访问授权入口，请使用“导入书籍”。" }
                    }
                } else storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }) { Text("前往授权") } },
            dismissButton = { TextButton(onClick = { showScanPermission = false }) { Text("取消") } },
        )
    }
    if (state.candidates.isNotEmpty()) {
        val selected = state.candidates.count { it.selected }
        AlertDialog(
            onDismissRequest = onDismissCandidates,
            title = { Text("选择导入书籍") },
            text = {
                Column {
                    Text("找到 ${state.candidates.size} 个候选文件 · 已选 $selected 个")
                    TextButton(onClick = { onSelectAll(selected != state.candidates.size) }) { Text(if (selected == state.candidates.size) "取消全选" else "全选") }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(state.candidates, key = { it.id }) { book ->
                            Row(Modifier.fillMaxWidth().clickable { onToggleCandidate(book.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = book.selected, onCheckedChange = { onToggleCandidate(book.id) })
                                Column(Modifier.weight(1f)) {
                                    Text(book.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("${book.name.substringAfterLast('.', "").uppercase()} · ${formatBookSize(book.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    book.location?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                                }
                            }
                        }
                    }
                    Text("TXT 可能是普通文档，请确认后导入；相同书籍会自动跳过。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = selected > 0, onClick = onImportSelected) { Text("导入所选（$selected）") } },
            dismissButton = { TextButton(onClick = onDismissCandidates) { Text("取消") } },
        )
    }
}

private fun formatBookSize(size: Long?): String = when {
    size == null || size < 0 -> "大小未知"
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", size / 1024.0)
    else -> String.format(java.util.Locale.ROOT, "%.1f MB", size / (1024.0 * 1024))
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
