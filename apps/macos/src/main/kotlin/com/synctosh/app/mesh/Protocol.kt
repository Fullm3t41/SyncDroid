package com.synctosh.app.mesh

import com.syncdroid.shared.protocol.canonicalChatPayload
import com.syncdroid.shared.protocol.canonicalFolderAnnouncementPayload
import com.syncdroid.shared.protocol.canonicalMembershipPayload
import com.syncdroid.shared.protocol.decodeEcPublicKeyBase64
import com.syncdroid.shared.protocol.deviceIdForPublicKey
import com.syncdroid.shared.protocol.fingerprintForPublicKey
import com.syncdroid.shared.protocol.legacyFolderAnnouncementPayload
import com.syncdroid.shared.protocol.legacyMembershipPayload
import com.syncdroid.shared.protocol.verifyEcdsaSha256
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.util.Base64

interface DeviceSigner {
    val deviceId: String
    val publicKey: PublicKey
    fun sign(payload: ByteArray): ByteArray
}

fun deviceIdFor(publicKey: PublicKey): String = deviceIdForPublicKey(publicKey)

fun fingerprintFor(publicKey: PublicKey): String = fingerprintForPublicKey(publicKey)

fun decodePublicKey(encoded: String): PublicKey = decodeEcPublicKeyBase64(encoded)

enum class MembershipEventType { AddDevice, UpdateDeviceName, RemoveDevice }

data class MembershipEvent(
    val eventId: String,
    val groupId: String,
    val eventType: MembershipEventType,
    val subjectDeviceId: String,
    val subjectDisplayName: String,
    val subjectPublicKeyBase64: String,
    val signerDeviceId: String,
    val parentEventIds: List<String>,
    val version: VersionVector,
    val createdAtMillis: Long,
    val signatureBase64: String,
) {
    fun canonicalPayload(): ByteArray = canonicalMembershipPayload(
        groupId, eventType.name, subjectDeviceId, subjectDisplayName, subjectPublicKeyBase64,
        signerDeviceId, parentEventIds, version.toJson(), createdAtMillis,
    )

    fun isStructurallyValid(): Boolean = runCatching {
        payloadForValidation() != null &&
            subjectDeviceId == deviceIdFor(decodePublicKey(subjectPublicKeyBase64))
    }.getOrDefault(false)

    fun verifySignature(signerPublicKey: PublicKey): Boolean = payloadForValidation()?.let { payload ->
        verifyEcdsaSha256(signerPublicKey, payload, signatureBase64)
    } ?: false

    /**
     * Early SyncDroid meshes used DataOutputStream.writeUTF for membership-v1 events.
     * Keep those signed creator records verifiable so an upgraded Android mesh can
     * authorize a new SyncTosh member without rewriting its immutable history.
     */
    private fun payloadForValidation(): ByteArray? {
        val current = canonicalPayload()
        if (eventId == eventIdFor(current)) return current
        val legacy = legacyCanonicalPayload()
        return legacy.takeIf { eventId == eventIdFor(it) }
    }

    private fun legacyCanonicalPayload(): ByteArray = legacyMembershipPayload(
        groupId, eventType.name, subjectDeviceId, subjectDisplayName, subjectPublicKeyBase64,
        signerDeviceId, parentEventIds, version.toJson(), createdAtMillis,
    )

    companion object {
        fun createAddDevice(
            groupId: String,
            subjectDisplayName: String,
            subjectPublicKey: PublicKey,
            signer: DeviceSigner,
            parentEventIds: List<String>,
            version: VersionVector,
            createdAtMillis: Long = System.currentTimeMillis(),
        ) = create(
            groupId, MembershipEventType.AddDevice, subjectDisplayName, subjectPublicKey,
            signer, parentEventIds, version, createdAtMillis,
        )

        private fun create(
            groupId: String,
            type: MembershipEventType,
            subjectDisplayName: String,
            subjectPublicKey: PublicKey,
            signer: DeviceSigner,
            parents: List<String>,
            version: VersionVector,
            createdAtMillis: Long,
        ): MembershipEvent {
            val publicKey = Base64.getEncoder().encodeToString(subjectPublicKey.encoded)
            val subjectId = deviceIdFor(subjectPublicKey)
            val unsigned = MembershipEvent(
                "", groupId, type, subjectId, subjectDisplayName.trim(), publicKey,
                signer.deviceId, parents.sorted(), version, createdAtMillis, "",
            )
            val payload = unsigned.canonicalPayload()
            return unsigned.copy(
                eventId = eventIdFor(payload),
                signatureBase64 = Base64.getEncoder().encodeToString(signer.sign(payload)),
            )
        }
    }
}

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
        groupId, folderId, displayName, includePatterns, excludePatterns,
        signerDeviceId, version.toJson(), createdAtMillis,
    )

    fun hasValidEventId(): Boolean = payloadForValidation() != null

    fun verifySignature(signerPublicKey: PublicKey): Boolean = payloadForValidation()?.let { payload ->
        verifyEcdsaSha256(signerPublicKey, payload, signatureBase64)
    } ?: false

    private fun payloadForValidation(): ByteArray? {
        val current = canonicalPayload()
        if (eventId == eventIdFor(current)) return current
        val legacy = legacyFolderAnnouncementPayload(
            groupId, folderId, displayName, includePatterns, excludePatterns,
            signerDeviceId, version.toJson(), createdAtMillis,
        )
        return legacy.takeIf { eventId == eventIdFor(it) }
    }
}

data class MeshChatMessage(
    val messageId: String,
    val groupId: String,
    val authorDeviceId: String,
    val body: String,
    val createdAtMillis: Long,
    val signatureBase64: String,
) {
    fun canonicalPayload(): ByteArray = canonicalChatPayload(groupId, authorDeviceId, body, createdAtMillis)

    fun hasValidMessageId(): Boolean = messageId == eventIdFor(canonicalPayload())

    fun verifySignature(publicKey: PublicKey): Boolean =
        verifyEcdsaSha256(publicKey, canonicalPayload(), signatureBase64)

    companion object {
        fun create(
            groupId: String,
            body: String,
            signer: DeviceSigner,
            createdAtMillis: Long = System.currentTimeMillis(),
        ): MeshChatMessage {
            val cleanBody = body.trim()
            require(cleanBody.isNotEmpty()) { "A chat message cannot be empty" }
            require(cleanBody.toByteArray(StandardCharsets.UTF_8).size <= MAX_CHAT_BODY_BYTES) {
                "A chat message is too long"
            }
            val unsigned = MeshChatMessage("", groupId, signer.deviceId, cleanBody, createdAtMillis, "")
            val payload = unsigned.canonicalPayload()
            return unsigned.copy(
                messageId = eventIdFor(payload),
                signatureBase64 = Base64.getEncoder().encodeToString(signer.sign(payload)),
            )
        }
    }
}

internal const val MAX_CHAT_BODY_BYTES = 4_000
