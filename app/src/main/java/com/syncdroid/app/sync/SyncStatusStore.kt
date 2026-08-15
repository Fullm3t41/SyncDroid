package com.syncdroid.app.sync

import android.content.Context

class SyncStatusStore(context: Context) {
    private val preferences = context.getSharedPreferences("sync_status", Context.MODE_PRIVATE)

    fun lastSuccessfulSyncMillis(): Long? = preferences.getLong(KEY_GLOBAL_LAST_SYNC, 0).takeIf { it > 0 }

    fun lastSuccessfulFolderSyncMillis(folderId: String): Long? =
        preferences.getLong(folderKey(folderId), 0).takeIf { it > 0 }

    fun recordSuccessfulSync(folderIds: Collection<String>, syncedAtMillis: Long = System.currentTimeMillis()) {
        val editor = preferences.edit().putLong(KEY_GLOBAL_LAST_SYNC, syncedAtMillis)
        folderIds.forEach { editor.putLong(folderKey(it), syncedAtMillis) }
        editor.apply()
    }

    private fun folderKey(folderId: String) = "folder_last_sync:$folderId"

    private companion object { const val KEY_GLOBAL_LAST_SYNC = "global_last_sync" }
}
