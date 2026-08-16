package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import com.syncdroid.shared.protocol.canonicalMembershipPayload
import com.syncdroid.shared.protocol.eventIdFor
import com.syncdroid.shared.protocol.decodeEcPublicKeyBase64
import com.syncdroid.shared.protocol.deviceIdForPublicKey
import com.syncdroid.shared.protocol.fingerprintForPublicKey
import com.syncdroid.shared.protocol.legacyMembershipPayload
import com.syncdroid.shared.protocol.verifyEcdsaSha256
import java.security.PublicKey
import java.util.Base64

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
        groupId = groupId,
        eventType = eventType.name,
        subjectDeviceId = subjectDeviceId,
        subjectDisplayName = subjectDisplayName,
        subjectPublicKeyBase64 = subjectPublicKeyBase64,
        signerDeviceId = signerDeviceId,
        parentEventIds = parentEventIds,
        versionJson = version.toJson(),
        createdAtMillis = createdAtMillis,
    )

    fun hasValidEventId(): Boolean = payloadForValidation() != null

    fun hasValidSubjectId(): Boolean = runCatching {
        subjectDeviceId == deviceIdFor(decodePublicKey(subjectPublicKeyBase64))
    }.getOrDefault(false)

    fun verifySignature(signerPublicKey: PublicKey): Boolean = payloadForValidation()?.let { payload ->
        verifyEcdsaSha256(signerPublicKey, payload, signatureBase64)
    } ?: false

    companion object {
        fun createAddDevice(
            groupId: String,
            subjectDisplayName: String,
            subjectPublicKey: PublicKey,
            signer: DeviceSigner,
            parentEventIds: List<String>,
            version: VersionVector,
            createdAtMillis: Long = System.currentTimeMillis(),
        ): MembershipEvent {
            return createDeviceEvent(
                groupId,
                MembershipEventType.AddDevice,
                subjectDisplayName,
                subjectPublicKey,
                signer,
                parentEventIds,
                version,
                createdAtMillis,
            )
        }

        fun createDeviceNameUpdate(
            groupId: String,
            subjectDisplayName: String,
            signer: DeviceSigner,
            parentEventIds: List<String>,
            version: VersionVector,
            createdAtMillis: Long = System.currentTimeMillis(),
        ): MembershipEvent = createDeviceEvent(
            groupId,
            MembershipEventType.UpdateDeviceName,
            subjectDisplayName,
            signer.publicKey,
            signer,
            parentEventIds,
            version,
            createdAtMillis,
        )

        fun createRemoveDevice(
            groupId: String,
            subjectDisplayName: String,
            subjectPublicKey: PublicKey,
            signer: DeviceSigner,
            parentEventIds: List<String>,
            version: VersionVector,
            createdAtMillis: Long = System.currentTimeMillis(),
        ): MembershipEvent = createDeviceEvent(
            groupId,
            MembershipEventType.RemoveDevice,
            subjectDisplayName,
            subjectPublicKey,
            signer,
            parentEventIds,
            version,
            createdAtMillis,
        )

        private fun createDeviceEvent(
            groupId: String,
            eventType: MembershipEventType,
            subjectDisplayName: String,
            subjectPublicKey: PublicKey,
            signer: DeviceSigner,
            parentEventIds: List<String>,
            version: VersionVector,
            createdAtMillis: Long,
        ): MembershipEvent {
            val subjectPublicKeyBase64 = Base64.getEncoder().encodeToString(subjectPublicKey.encoded)
            val subjectDeviceId = deviceIdFor(subjectPublicKey)
            val payload = canonicalMembershipPayload(
                groupId,
                eventType.name,
                subjectDeviceId,
                subjectDisplayName,
                subjectPublicKeyBase64,
                signer.deviceId,
                parentEventIds,
                version.toJson(),
                createdAtMillis,
            )
            return MembershipEvent(
                eventId = eventIdFor(payload),
                groupId = groupId,
                eventType = eventType,
                subjectDeviceId = subjectDeviceId,
                subjectDisplayName = subjectDisplayName,
                subjectPublicKeyBase64 = subjectPublicKeyBase64,
                signerDeviceId = signer.deviceId,
                parentEventIds = parentEventIds.sorted(),
                version = version,
                createdAtMillis = createdAtMillis,
                signatureBase64 = Base64.getEncoder().encodeToString(signer.sign(payload)),
            )
        }
    }

    private fun payloadForValidation(): ByteArray? {
        val current = canonicalPayload()
        if (eventId == eventIdFor(current)) return current
        val legacy = legacyMembershipPayload(
            groupId,
            eventType.name,
            subjectDeviceId,
            subjectDisplayName,
            subjectPublicKeyBase64,
            signerDeviceId,
            parentEventIds,
            version.toJson(),
            createdAtMillis,
        )
        return legacy.takeIf { eventId == eventIdFor(it) }
    }
}

interface DeviceSigner {
    val deviceId: String
    val publicKey: PublicKey
    fun sign(payload: ByteArray): ByteArray
}

fun deviceIdFor(publicKey: PublicKey): String = deviceIdForPublicKey(publicKey)

fun fingerprintFor(publicKey: PublicKey): String = fingerprintForPublicKey(publicKey)

fun decodePublicKey(encoded: String): PublicKey = decodeEcPublicKeyBase64(encoded)
