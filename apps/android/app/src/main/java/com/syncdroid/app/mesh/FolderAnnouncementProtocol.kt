package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import com.syncdroid.shared.protocol.canonicalFolderAnnouncementPayload
import com.syncdroid.shared.protocol.eventIdFor
import com.syncdroid.shared.protocol.legacyFolderAnnouncementPayload
import com.syncdroid.shared.protocol.verifyEcdsaSha256
import java.security.PublicKey
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
    fun canonicalPayload(): ByteArray = canonicalFolderAnnouncementPayload(
        groupId = groupId,
        folderId = folderId,
        displayName = displayName,
        includePatterns = includePatterns,
        excludePatterns = excludePatterns,
        signerDeviceId = signerDeviceId,
        versionJson = version.toJson(),
        createdAtMillis = createdAtMillis,
    )

    fun hasValidEventId(): Boolean = payloadForValidation() != null

    fun verifySignature(signerPublicKey: PublicKey): Boolean = payloadForValidation()?.let { payload ->
        verifyEcdsaSha256(signerPublicKey, payload, signatureBase64)
    } ?: false

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
            val payload = canonicalFolderAnnouncementPayload(
                groupId,
                folderId,
                displayName.trim(),
                normalizedIncludes,
                normalizedExcludes,
                signer.deviceId,
                version.toJson(),
                createdAtMillis,
            )
            return FolderAnnouncement(
                eventId = eventIdFor(payload),
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
        if (eventId == eventIdFor(current)) return current
        val legacy = legacyFolderAnnouncementPayload(
            groupId,
            folderId,
            displayName,
            includePatterns,
            excludePatterns,
            signerDeviceId,
            version.toJson(),
            createdAtMillis,
        )
        return legacy.takeIf { eventId == eventIdFor(it) }
    }
}

private fun normalizePatterns(patterns: List<String>): List<String> = patterns
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .sorted()
