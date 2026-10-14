package com.iread.novel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.iread.novel.ui.navigation.IReadNavHost
import com.iread.novel.ui.theme.IReadTheme

@Composable
fun IReadApp() {
    val application = LocalContext.current.applicationContext as IReadApplication
    IReadTheme { IReadNavHost(application.container) }
}
