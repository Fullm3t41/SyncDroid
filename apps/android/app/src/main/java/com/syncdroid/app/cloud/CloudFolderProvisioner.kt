package com.syncdroid.app.cloud

interface CloudFolderApi {
    /** Returns the existing folder ID, or creates the folder and returns its new ID. */
    suspend fun ensureFolder(parentId: String, name: String): String
}

data class ProvisionedCloudFolder(
    val syncDroidRootId: String,
    val syncFolderRootId: String,
    val displayPath: String,
)

class CloudFolderProvisioner(private val api: CloudFolderApi) {
    suspend fun provision(providerRootId: String, syncFolderName: String): ProvisionedCloudFolder {
        val layout = CloudPathLayout.folderRoot(syncFolderName)
        val syncDroidRootId = api.ensureFolder(providerRootId, CloudPathLayout.ROOT_FOLDER)
        val syncFolderRootId = api.ensureFolder(syncDroidRootId, layout.segments.last())
        return ProvisionedCloudFolder(
            syncDroidRootId = syncDroidRootId,
            syncFolderRootId = syncFolderRootId,
            displayPath = layout.displayPath,
        )
    }
}
