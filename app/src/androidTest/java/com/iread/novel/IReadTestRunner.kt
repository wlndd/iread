package com.iread.novel

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** The test APK manifest alone cannot replace the target APK's Application. */
class IReadTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, TestIReadApplication::class.java.name, context)
}
