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

@Composable
fun SettingsScreen(onBack: () -> Unit, onImportUri: (List<Uri>) -> Unit, state: SettingsUiState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onImportUri)
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
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                    Column {
                        SettingsRow("导入文件", "从设备选择 TXT 书籍", Icons.AutoMirrored.Outlined.NoteAdd, state.importingCount == 0) {
                            picker.launch(arrayOf("text/plain", "application/octet-stream"))
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsRow("扫描文件夹", "下一阶段开放", Icons.Outlined.FolderOpen, false) { }
                    }
                }
            }
            item { Text("书籍将保存在应用内，原始文件保持不变。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            item { Text("当前为暖色竖向阅读。字号、行距与背景调整将在下一阶段开放。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
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
