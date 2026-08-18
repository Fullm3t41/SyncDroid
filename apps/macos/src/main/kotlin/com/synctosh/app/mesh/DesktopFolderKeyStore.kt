package com.synctosh.app.mesh

import com.syncdroid.shared.cloud.FolderKeyMaterial
import com.syncdroid.shared.cloud.LocalSecretCipher
import java.security.SecureRandom
import java.util.UUID

class DesktopFolderKeyStore(
    private val store: MeshStore,
    identity: MacDeviceIdentity,
) {
    private val cipher = LocalSecretCipher(identity.privateKey().encoded, "synctosh-folder-keys")

    fun getOrCreate(folderId: String): FolderKeyMaterial {
        store.storedFolderKey(folderId)?.let(::decode)?.let { return it }
        return FolderKeyMaterial(folderId, UUID.randomUUID().toString(), ByteArray(32).also(SecureRandom()::nextBytes))
            .also(::save)
    }

    fun existing(folderId: String): FolderKeyMaterial? = store.storedFolderKey(folderId)?.let(::decode)

    fun import(value: FolderKeyMaterial): FolderKeyMaterial {
        store.storedFolderKey(value.folderId)?.let(::decode)?.let { existing ->
            require(existing.keyId == value.keyId && existing.bytes.contentEquals(value.bytes)) {
                "A different cloud key already protects this folder"
            }
            return existing
        }
        save(value)
        return value
    }

    private fun save(value: FolderKeyMaterial) = store.saveFolderKey(
        StoredFolderKey(value.folderId, value.keyId, cipher.encrypt(value.bytes)),
    )

    private fun decode(value: StoredFolderKey) = FolderKeyMaterial(
        value.folderId, value.keyId, cipher.decrypt(value.encryptedKey),
    )
}
