package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.UUID

data class FolderAnnouncement(
    val eventId: String,
    val groupId: String,
    val folderId: String,
    val displayName: String,
    val includePatterns: List<String>,
    val excludePatterns: List<String>,
    val signerDeviceId: String,
    val version: VersionVector,
    val createdAtMillis: Long,
    val signatureBase64: String,
) {
    fun canonicalPayload(): ByteArray = canonicalFolderPayloadV2(
        groupId = groupId,
        folderId = folderId,
        displayName = displayName,
        includePatterns = includePatterns,
        excludePatterns = excludePatterns,
        signerDeviceId = signerDeviceId,
        version = version,
        createdAtMillis = createdAtMillis,
    )

    fun hasValidEventId(): Boolean = payloadForValidation() != null

    fun verifySignature(signerPublicKey: PublicKey): Boolean = runCatching {
        val payload = requireNotNull(payloadForValidation()) { "Event ID does not match its payload" }
        Signature.getInstance(FOLDER_SIGNATURE_ALGORITHM).run {
            initVerify(signerPublicKey)
            update(payload)
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

    companion object {
        fun create(
            groupId: String,
            displayName: String,
            includePatterns: List<String>,
            excludePatterns: List<String>,
            signer: DeviceSigner,
            version: VersionVector,
            folderId: String = UUID.randomUUID().toString(),
            createdAtMillis: Long = System.currentTimeMillis(),
        ): FolderAnnouncement {
            require(displayName.isNotBlank()) { "Folder name cannot be blank" }
            val normalizedIncludes = normalizePatterns(includePatterns)
            val normalizedExcludes = normalizePatterns(excludePatterns)
            val payload = canonicalFolderPayloadV2(
                groupId,
                folderId,
                displayName.trim(),
                normalizedIncludes,
                normalizedExcludes,
                signer.deviceId,
                version,
                createdAtMillis,
            )
            return FolderAnnouncement(
                eventId = folderEventIdFor(payload),
                groupId = groupId,
                folderId = folderId,
                displayName = displayName.trim(),
                includePatterns = normalizedIncludes,
                excludePatterns = normalizedExcludes,
                signerDeviceId = signer.deviceId,
                version = version,
                createdAtMillis = createdAtMillis,
                signatureBase64 = Base64.getEncoder().encodeToString(signer.sign(payload)),
            )
        }
    }

    private fun payloadForValidation(): ByteArray? {
        val current = canonicalPayload()
        if (eventId == folderEventIdFor(current)) return current
        val legacy = legacyCanonicalFolderPayload(
            groupId,
            folderId,
            displayName,
            includePatterns,
            excludePatterns,
            signerDeviceId,
            version,
            createdAtMillis,
        )
        return legacy.takeIf { eventId == folderEventIdFor(it) }
    }
}

private fun normalizePatterns(patterns: List<String>): List<String> = patterns
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .sorted()

private fun canonicalFolderPayloadV2(
    groupId: String,
    folderId: String,
    displayName: String,
    includePatterns: List<String>,
    excludePatterns: List<String>,
    signerDeviceId: String,
    version: VersionVector,
    createdAtMillis: Long,
): ByteArray = canonicalBytes {
    string("syncdroid-folder-announcement-v2")
    string(groupId)
    string(folderId)
    string(displayName)
    string(signerDeviceId)
    int64(createdAtMillis)
    string(version.toJson())
    strings(includePatterns)
    strings(excludePatterns)
}

private fun legacyCanonicalFolderPayload(
    groupId: String,
    folderId: String,
    displayName: String,
    includePatterns: List<String>,
    excludePatterns: List<String>,
    signerDeviceId: String,
    version: VersionVector,
    createdAtMillis: Long,
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.writeUTF("syncdroid-folder-announcement-v1")
        output.writeUTF(groupId)
        output.writeUTF(folderId)
        output.writeUTF(displayName)
        output.writeUTF(signerDeviceId)
        output.writeLong(createdAtMillis)
        output.writeUTF(version.toJson())
        output.writeStringList(includePatterns)
        output.writeStringList(excludePatterns)
    }
    bytes.toByteArray()
}

private fun DataOutputStream.writeStringList(values: List<String>) {
    writeInt(values.size)
    values.forEach(::writeUTF)
}

private fun folderEventIdFor(payload: ByteArray): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(payload))

private const val FOLDER_SIGNATURE_ALGORITHM = "SHA256withECDSA"
