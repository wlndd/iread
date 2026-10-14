package com.iread.novel

import android.app.Application

open class IReadApplication : Application() {
    lateinit var container: AppContainer
        protected set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    protected open fun createContainer(): AppContainer = AppContainer(this)
}
