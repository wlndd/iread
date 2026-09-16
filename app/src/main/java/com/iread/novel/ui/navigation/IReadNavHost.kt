package com.iread.novel.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.iread.novel.AppContainer
import com.iread.novel.data.files.AndroidImportSource
import com.iread.novel.ui.settings.*
import com.iread.novel.ui.shelf.*

object Routes {
    const val Shelf = "shelf"
    const val Settings = "settings"
    const val Reader = "reader/{bookId}"
    fun reader(bookId: String) = "reader/${Uri.encode(bookId)}"
}

@Composable
fun IReadNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val resolver = LocalContext.current.applicationContext.contentResolver
    // Activity ownership keeps an import running when settings is popped.
    val shelf: ShelfViewModel = viewModel(factory = ShelfViewModel.Factory(container.repository, container.deleteBook))
    val settings: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container.importTxtBook))
    val shelfState by shelf.state.collectAsStateWithLifecycle()
    val settingsState by settings.state.collectAsStateWithLifecycle()
    NavHost(navController = nav, startDestination = Routes.Shelf) {
        composable(Routes.Shelf) {
            ShelfScreen(
                onOpenBook = { nav.navigate(Routes.reader(it)) }, onOpenSettings = { nav.navigate(Routes.Settings) { launchSingleTop = true } },
                state = shelfState, onEdit = shelf::updateMetadata, onDelete = shelf::delete, onDismissMessage = shelf::dismissMessage,
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(onBack = { nav.popBackStack() }, onImportUri = { uris ->
                settings.importSources(uris.map { uri -> { AndroidImportSource(resolver, uri) } })
            }, state = settingsState)
        }
        composable(Routes.Reader, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) {
            // Task 7 owns the reader. Keep the approved route ready for that screen.
            Scaffold { padding ->
                Column(Modifier.padding(padding).padding(24.dp)) {
                    TextButton(onClick = { nav.popBackStack() }) { Text("返回书架") }
                    Text("阅读页即将开放", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
