package com.iread.novel.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF387764), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F0E8), onPrimaryContainer = Color(0xFF294C40),
    background = Color(0xFFF9FAF7), onBackground = Color(0xFF252E2A),
    surface = Color(0xFFF9FAF7), onSurface = Color(0xFF252E2A),
    surfaceVariant = Color(0xFFEEF1EB), onSurfaceVariant = Color(0xFF7B857D),
    outlineVariant = Color(0xFFE7EBE4),
)

@Composable
fun IReadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
