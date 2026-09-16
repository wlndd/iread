package com.iread.novel.ui.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.iread.novel.AppContainer
import com.iread.novel.data.files.AndroidImportSource
import com.iread.novel.ui.reader.ReaderScreen
import com.iread.novel.ui.reader.ReaderViewModel
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
        composable(Routes.Reader, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            val reader: ReaderViewModel = viewModel(
                key = "reader-$bookId",
                factory = ReaderViewModel.Factory(bookId, container.repository),
            )
            val readerState by reader.state.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        reader.flushProgress()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            ReaderScreen(
                state = readerState,
                onBack = { nav.popBackStack() },
                onOpenChapter = reader::openChapter,
                onOffsetChanged = reader::updateCharacterOffset,
            )
        }
    }
}
