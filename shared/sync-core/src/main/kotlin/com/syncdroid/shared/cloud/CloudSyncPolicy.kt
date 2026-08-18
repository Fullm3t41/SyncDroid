package com.syncdroid.shared.cloud

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

    fun withFolderEnabled(folderId: String, enabled: Boolean): CloudSyncPolicy = copy(
        selectedFolderIds = selectedFolderIds.toMutableSet().apply {
            if (enabled) add(folderId) else remove(folderId)
        },
    )
}
