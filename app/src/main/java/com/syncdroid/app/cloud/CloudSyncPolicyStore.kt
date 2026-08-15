package com.syncdroid.app.cloud

import android.content.Context

enum class CloudSyncScope {
    DISABLED,
    SELECTED_FOLDERS,
    ALL_FOLDERS,
}

data class CloudSyncPolicy(
    val scope: CloudSyncScope = CloudSyncScope.DISABLED,
    val selectedFolderIds: Set<String> = emptySet(),
) {
    fun isEnabledFor(folderId: String): Boolean = when (scope) {
        CloudSyncScope.DISABLED -> false
        CloudSyncScope.SELECTED_FOLDERS -> folderId in selectedFolderIds
        CloudSyncScope.ALL_FOLDERS -> true
    }
}

class CloudSyncPolicyStore(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_sync_policy", Context.MODE_PRIVATE)

    fun load(): CloudSyncPolicy = CloudSyncPolicy(
        scope = runCatching {
            CloudSyncScope.valueOf(preferences.getString(KEY_SCOPE, null) ?: CloudSyncScope.DISABLED.name)
        }.getOrDefault(CloudSyncScope.DISABLED),
        selectedFolderIds = preferences.getStringSet(KEY_SELECTED_FOLDERS, emptySet()).orEmpty(),
    )

    fun setScope(scope: CloudSyncScope) {
        preferences.edit().putString(KEY_SCOPE, scope.name).apply()
    }

    fun setFolderEnabled(folderId: String, enabled: Boolean) {
        val updated = load().selectedFolderIds.toMutableSet()
        if (enabled) updated += folderId else updated -= folderId
        preferences.edit().putStringSet(KEY_SELECTED_FOLDERS, updated).apply()
    }

    private companion object {
        const val KEY_SCOPE = "scope"
        const val KEY_SELECTED_FOLDERS = "selected_folders"
    }
}
