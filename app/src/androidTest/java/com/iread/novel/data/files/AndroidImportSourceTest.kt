package com.iread.novel.data.files

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidImportSourceTest {
    @Test
    fun usesChineseNameWhenProviderHasNoDisplayNameOrPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = AndroidImportSource(
            context.contentResolver,
            Uri.parse("content://missing-provider"),
        )

        assertEquals("未命名.txt", source.displayName)
    }
}
