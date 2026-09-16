package com.iread.novel

import android.app.Application

class IReadApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
