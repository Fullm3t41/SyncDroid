package com.syncdroid.app.cloud

import com.syncdroid.app.sync.FileManifestEntry
import com.syncdroid.app.sync.SnapshotManifest
import com.syncdroid.app.sync.VersionVector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

data class CloudManifestFile(
    val file: FileManifestEntry,
    val objectName: String?,
)

data class EncryptedCloudManifest(
    val folderId: String,
    val keyId: String,
    val snapshotId: String,
    val bytes: ByteArray,
)

object CloudEncryption {
    fun encryptObject(
        key: FolderKeyMaterial,
        logicalObjectId: String,
        plaintext: ByteArray,
    ): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad(key.folderId, key.keyId, logicalObjectId))
        }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeByte(VERSION)
                output.writeByte(nonce.size)
                output.write(nonce)
                output.write(cipher.doFinal(plaintext))
            }
            bytes.toByteArray()
        }
    }

    fun decryptObject(
        key: FolderKeyMaterial,
        logicalObjectId: String,
        encrypted: ByteArray,
    ): ByteArray = DataInputStream(ByteArrayInputStream(encrypted)).use { input ->
        require(input.readInt() == MAGIC && input.readUnsignedByte() == VERSION) { "Unsupported cloud ciphertext" }
        val nonceSize = input.readUnsignedByte()
        require(nonceSize == NONCE_BYTES) { "Invalid cloud nonce" }
        val nonce = ByteArray(nonceSize).also(input::readFully)
        val ciphertext = input.readBytes()
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad(key.folderId, key.keyId, logicalObjectId))
            doFinal(ciphertext)
        }
    }

    fun objectName(key: FolderKeyMaterial, fileId: String, contentSha256: String): String = Mac
        .getInstance("HmacSHA256")
        .run {
            init(SecretKeySpec(key.bytes, "HmacSHA256"))
            doFinal("file:$fileId:$contentSha256".toByteArray(StandardCharsets.UTF_8))
        }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) + ".sdenc" }

    fun encryptManifest(key: FolderKeyMaterial, snapshot: SnapshotManifest): EncryptedCloudManifest {
        val plaintext = manifestJson(key, snapshot).toString().toByteArray(StandardCharsets.UTF_8)
        return EncryptedCloudManifest(
            key.folderId,
            key.keyId,
            snapshot.snapshotId,
            encryptObject(key, MANIFEST_OBJECT_ID, plaintext),
        )
    }

    fun decryptManifest(key: FolderKeyMaterial, encrypted: ByteArray): SnapshotManifest {
        val json = JSONObject(String(decryptObject(key, MANIFEST_OBJECT_ID, encrypted), StandardCharsets.UTF_8))
        require(
            json.getInt("format") == 1 &&
                json.getString("folderId") == key.folderId &&
                json.getString("keyId") == key.keyId
        ) {
            "Cloud manifest belongs to a different folder or format"
        }
        val filesJson = json.getJSONArray("files")
        val files = List(filesJson.length()) { index ->
            val file = filesJson.getJSONObject(index)
            FileManifestEntry(
                relativePath = file.getString("path"),
                sizeBytes = file.getLong("size"),
                modifiedAtMillis = file.getLong("modified"),
                sha256 = file.getString("sha256"),
                deleted = file.getBoolean("deleted"),
                fileId = file.getString("fileId"),
                previousSha256 = file.optString("previousSha256").takeIf(String::isNotEmpty),
                version = VersionVector.fromJson(file.getString("version")),
                localSequence = file.getLong("sequence"),
                originDeviceId = file.optString("originDeviceId").ifBlank { json.getString("originDeviceId") },
            )
        }
        return SnapshotManifest(
            snapshotId = json.getString("snapshotId"),
            folderId = json.getString("folderId"),
            originDeviceId = json.getString("originDeviceId"),
            createdAtMillis = json.getLong("createdAtMillis"),
            version = VersionVector.fromJson(json.getString("version")),
            parentSnapshotIds = json.getJSONArray("parents").strings(),
            files = files,
        )
    }

    private fun manifestJson(key: FolderKeyMaterial, snapshot: SnapshotManifest) = JSONObject().apply {
        put("format", 1)
        put("keyId", key.keyId)
        put("folderId", snapshot.folderId)
        put("snapshotId", snapshot.snapshotId)
        put("originDeviceId", snapshot.originDeviceId)
        put("createdAtMillis", snapshot.createdAtMillis)
        put("version", snapshot.version.toJson())
        put("parents", JSONArray(snapshot.parentSnapshotIds))
        put("files", JSONArray(snapshot.files.sortedBy(FileManifestEntry::relativePath).map { file ->
            JSONObject().apply {
                put("path", file.relativePath)
                put("fileId", file.fileId)
                put("size", file.sizeBytes)
                put("modified", file.modifiedAtMillis)
                put("sha256", file.sha256)
                put("previousSha256", file.previousSha256 ?: "")
                put("deleted", file.deleted)
                put("version", file.version.toJson())
                put("sequence", file.localSequence)
                put("originDeviceId", file.originDeviceId.ifBlank { snapshot.originDeviceId })
                if (!file.deleted) put("object", objectName(key, file.fileId, file.sha256))
            }
        }))
    }

    private fun aad(folderId: String, keyId: String, logicalObjectId: String): ByteArray =
        "syncdroid-cloud-v1\u0000$folderId\u0000$keyId\u0000$logicalObjectId".toByteArray(StandardCharsets.UTF_8)

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    const val MANIFEST_FILE_NAME = "manifest.sdenc"
    private const val MANIFEST_OBJECT_ID = "manifest"
    private const val MAGIC = 0x53444345
    private const val VERSION = 1
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}

data class WrappedFolderKeyTransfer(
    val folderId: String,
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

object PairingFolderKeyWrapper {
    fun wrap(key: FolderKeyMaterial, pairingSessionKey: ByteArray): WrappedFolderKeyTransfer {
        require(pairingSessionKey.size == 32)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(pairingSessionKey, "AES"), GCMParameterSpec(128, nonce))
            updateAAD("${key.folderId}\u0000${key.keyId}".toByteArray(StandardCharsets.UTF_8))
        }
        return WrappedFolderKeyTransfer(key.folderId, key.keyId, nonce, cipher.doFinal(key.bytes))
    }

    fun unwrap(value: WrappedFolderKeyTransfer, pairingSessionKey: ByteArray): FolderKeyMaterial {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(pairingSessionKey, "AES"), GCMParameterSpec(128, value.nonce))
            updateAAD("${value.folderId}\u0000${value.keyId}".toByteArray(StandardCharsets.UTF_8))
        }
        return FolderKeyMaterial(value.folderId, value.keyId, cipher.doFinal(value.ciphertext))
    }
}
