package com.iread.novel.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Colors = lightColorScheme(
    primary = Color(0xFF387764), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F0E8), onPrimaryContainer = Color(0xFF294C40),
    background = Color(0xFFF9FAF7), onBackground = Color(0xFF252E2A),
    surface = Color(0xFFF9FAF7), onSurface = Color(0xFF252E2A),
    surfaceVariant = Color(0xFFEEF1EB), onSurfaceVariant = Color(0xFF7B857D),
    outlineVariant = Color(0xFFE7EBE4),
    secondary = Color(0xFF52675B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EBE1), onSecondaryContainer = Color(0xFF294C40),
    tertiary = Color(0xFF52675B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2EBE1), onTertiaryContainer = Color(0xFF294C40),
    surfaceTint = Color(0xFF387764), outline = Color(0xFF869187),
    surfaceDim = Color(0xFFDDE2D9), surfaceBright = Color(0xFFF9FAF7),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F7F1),
    surfaceContainer = Color(0xFFF0F3EC), surfaceContainerHigh = Color(0xFFEEF1EB),
    surfaceContainerHighest = Color(0xFFE7EBE4),
)

internal fun readerColors(paper: Color, ink: Color, night: Boolean): ColorScheme {
    val accent = if (night) Color(0xFFA5C4B3) else Color(0xFF466857)
    val container = androidx.compose.ui.graphics.lerp(paper, ink, if (night) .09f else .035f)
    val selected = androidx.compose.ui.graphics.lerp(paper, accent, .18f)
    return Colors.copy(
        primary = accent, onPrimary = paper, primaryContainer = selected, onPrimaryContainer = ink,
        secondary = accent, onSecondary = paper, secondaryContainer = selected, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = paper, tertiaryContainer = selected, onTertiaryContainer = ink,
        background = paper, onBackground = ink, surface = paper, onSurface = ink,
        surfaceVariant = container, onSurfaceVariant = ink.copy(alpha = .75f),
        surfaceTint = accent, surfaceDim = container, surfaceBright = paper,
        surfaceContainerLowest = paper, surfaceContainerLow = container,
        surfaceContainer = container, surfaceContainerHigh = container, surfaceContainerHighest = selected,
        outline = ink.copy(alpha = .45f), outlineVariant = ink.copy(alpha = .16f),
        inverseSurface = ink, inverseOnSurface = paper, inversePrimary = paper,
    )
}

@Composable
fun IReadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
