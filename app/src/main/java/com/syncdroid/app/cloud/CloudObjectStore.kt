package com.syncdroid.app.cloud

import java.io.ByteArrayInputStream

data class CloudObject(
    val id: String,
    val name: String,
    val sizeBytes: Long,
)

interface CloudObjectStore : CloudFolderApi {
    suspend fun findObject(parentId: String, name: String): CloudObject?
    suspend fun putObject(parentId: String, name: String, contentType: String, bytes: ByteArray): CloudObject
    suspend fun getObject(objectId: String): ByteArray
}

class EncryptedCloudRepository(private val store: CloudObjectStore) {
    suspend fun uploadManifest(remoteFolderId: String, key: FolderKeyMaterial, snapshot: com.syncdroid.app.sync.SnapshotManifest) =
        store.putObject(
            remoteFolderId,
            CloudEncryption.MANIFEST_FILE_NAME,
            "application/octet-stream",
            CloudEncryption.encryptManifest(key, snapshot).bytes,
        )

    suspend fun downloadManifest(remoteFolderId: String, key: FolderKeyMaterial): com.syncdroid.app.sync.SnapshotManifest? {
        val remote = store.findObject(remoteFolderId, CloudEncryption.MANIFEST_FILE_NAME) ?: return null
        return CloudEncryption.decryptManifest(key, store.getObject(remote.id))
    }

    suspend fun uploadFile(
        remoteFolderId: String,
        key: FolderKeyMaterial,
        fileId: String,
        contentSha256: String,
        plaintext: ByteArray,
    ): CloudObject {
        require(com.syncdroid.app.sync.FileHasher.sha256(ByteArrayInputStream(plaintext)).equals(contentSha256, true)) {
            "Cloud upload content does not match its manifest"
        }
        val name = CloudEncryption.objectName(key, fileId, contentSha256)
        return store.putObject(
            remoteFolderId,
            name,
            "application/octet-stream",
            CloudEncryption.encryptObject(key, "file:$fileId:$contentSha256", plaintext),
        )
    }

    suspend fun downloadFile(
        objectId: String,
        key: FolderKeyMaterial,
        fileId: String,
        contentSha256: String,
    ): ByteArray {
        val plaintext = CloudEncryption.decryptObject(key, "file:$fileId:$contentSha256", store.getObject(objectId))
        require(com.syncdroid.app.sync.FileHasher.sha256(ByteArrayInputStream(plaintext)).equals(contentSha256, true)) {
            "Cloud object content hash does not match its manifest"
        }
        return plaintext
    }
}
