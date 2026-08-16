package com.syncdroid.app.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

data class ManagedStorageRoot(
    val id: String,
    val label: String,
    val directory: File,
    val removable: Boolean,
)

fun managedStorageRoots(context: Context): List<ManagedStorageRoot> {
    @Suppress("DEPRECATION")
    val internal = Environment.getExternalStorageDirectory().canonicalFile
    val roots = mutableListOf(
        ManagedStorageRoot("internal", "Internal storage", internal, removable = false),
    )

    val removableDirectories = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
            if (!volume.isRemovable || volume.state !in MOUNTED_STATES) return@mapNotNull null
            volume.directory?.let { directory -> volume.getDescription(context) to directory }
        }
    } else {
        context.getExternalFilesDirs(null).drop(1).mapNotNull { appDirectory ->
            deriveStorageRoot(appDirectory, context.packageName)?.let { "SD card" to it }
        }
    }

    removableDirectories.forEachIndexed { index, (_, directory) ->
        val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return@forEachIndexed
        if (!canonical.exists() || !canonical.isDirectory || canonical == internal) return@forEachIndexed
        if (roots.any { it.directory == canonical }) return@forEachIndexed
        val label = if (removableDirectories.size > 1) "SD card ${index + 1}" else "SD card"
        roots += ManagedStorageRoot(
            id = "removable:${canonical.path}",
            label = label,
            directory = canonical,
            removable = true,
        )
    }
    return roots
}

internal fun deriveStorageRoot(appExternalFilesDirectory: File?, packageName: String): File? {
    val appDirectory = appExternalFilesDirectory?.canonicalFile ?: return null
    var candidate: File = appDirectory
    repeat(4) { candidate = candidate.parentFile ?: return null }
    val expected = File(candidate, "Android/data/$packageName/files")
    return candidate.takeIf {
        runCatching { expected.canonicalFile == appDirectory }.getOrDefault(false)
    }
}

private val MOUNTED_STATES = setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
