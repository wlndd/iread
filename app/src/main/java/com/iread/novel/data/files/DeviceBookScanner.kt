package com.iread.novel.data.files

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import java.io.InputStream

fun hasStorageScanAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

class DeviceBookScanner(private val context: Context) {
    suspend fun scan(onProgress: (StorageScanProgress) -> Unit = {}): StorageScanResult {
        check(hasStorageScanAccess(context)) { "Storage access not granted" }
        val roots = sharedStorageRoots()
        check(roots.isNotEmpty()) { "No mounted shared storage" }
        val result = StorageBookTraversal().scan(roots, roots.flatMap {
            listOf(File(it, "Android/data"), File(it, "Android/obb"))
        }, onProgress)
        check(hasStorageScanAccess(context)) { "Storage access revoked during scan" }
        return result.copy(warnings = result.warnings + "已扫描可访问的共享存储；系统和其他应用的私有目录无法扫描。")
    }

    @Suppress("DEPRECATION")
    private fun sharedStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(StorageManager::class.java)
            manager.storageVolumes.mapNotNullTo(roots) { it.directory }
        }
        // App-specific paths identify mounted primary/SD-card volume roots on older Android.
        context.getExternalFilesDirs(null).filterNotNull().forEach { directory ->
            val path = directory.absolutePath.substringBefore("/Android/", "")
            if (path.isNotEmpty()) roots += File(path)
        }
        roots += Environment.getExternalStorageDirectory()
        return roots.map { it.canonicalFile }.distinctBy { it.path }.filter { it.isDirectory }
    }
}

class LocalBookImportSource(private val file: File) : ImportSource {
    override val displayName: String = file.name
    override val sizeBytes: Long = file.length()
    override val sourceUri: String = file.toURI().toString()
    override val sourceDescription: String = file.parent.orEmpty()
    override fun open(): InputStream = file.inputStream()
}
