package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

enum class MembershipEventType { AddDevice, UpdateDeviceName }

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
    fun canonicalPayload(): ByteArray = canonicalMembershipPayloadV2(
        groupId = groupId,
        eventType = eventType,
        subjectDeviceId = subjectDeviceId,
        subjectDisplayName = subjectDisplayName,
        subjectPublicKeyBase64 = subjectPublicKeyBase64,
        signerDeviceId = signerDeviceId,
        parentEventIds = parentEventIds,
        version = version,
        createdAtMillis = createdAtMillis,
    )

    fun hasValidEventId(): Boolean = payloadForValidation() != null

    fun hasValidSubjectId(): Boolean = runCatching {
        subjectDeviceId == deviceIdFor(decodePublicKey(subjectPublicKeyBase64))
    }.getOrDefault(false)

    fun verifySignature(signerPublicKey: PublicKey): Boolean = runCatching {
        val payload = requireNotNull(payloadForValidation()) { "Event ID does not match its payload" }
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(signerPublicKey)
            update(payload)
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

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
            val payload = canonicalMembershipPayloadV2(
                groupId,
                eventType,
                subjectDeviceId,
                subjectDisplayName,
                subjectPublicKeyBase64,
                signer.deviceId,
                parentEventIds,
                version,
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
        val legacy = legacyCanonicalMembershipPayload(
            groupId,
            eventType,
            subjectDeviceId,
            subjectDisplayName,
            subjectPublicKeyBase64,
            signerDeviceId,
            parentEventIds,
            version,
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

fun deviceIdFor(publicKey: PublicKey): String = sha256(publicKey.encoded)
    .copyOfRange(0, 18)
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

fun fingerprintFor(publicKey: PublicKey): String = sha256(publicKey.encoded)
    .joinToString("") { "%02X".format(it) }
    .chunked(4)
    .take(8)
    .joinToString(" ")

fun decodePublicKey(encoded: String): PublicKey = KeyFactory.getInstance("EC")
    .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))

private fun canonicalMembershipPayloadV2(
    groupId: String,
    eventType: MembershipEventType,
    subjectDeviceId: String,
    subjectDisplayName: String,
    subjectPublicKeyBase64: String,
    signerDeviceId: String,
    parentEventIds: List<String>,
    version: VersionVector,
    createdAtMillis: Long,
): ByteArray = canonicalBytes {
    string("syncdroid-membership-v2")
    string(groupId)
    string(eventType.name)
    string(subjectDeviceId)
    string(subjectDisplayName)
    string(subjectPublicKeyBase64)
    string(signerDeviceId)
    int64(createdAtMillis)
    string(version.toJson())
    strings(parentEventIds.sorted())
}

private fun legacyCanonicalMembershipPayload(
    groupId: String,
    eventType: MembershipEventType,
    subjectDeviceId: String,
    subjectDisplayName: String,
    subjectPublicKeyBase64: String,
    signerDeviceId: String,
    parentEventIds: List<String>,
    version: VersionVector,
    createdAtMillis: Long,
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.writeUTF("syncdroid-membership-v1")
        output.writeUTF(groupId)
        output.writeUTF(eventType.name)
        output.writeUTF(subjectDeviceId)
        output.writeUTF(subjectDisplayName)
        output.writeUTF(subjectPublicKeyBase64)
        output.writeUTF(signerDeviceId)
        output.writeLong(createdAtMillis)
        output.writeUTF(version.toJson())
        val sortedParents = parentEventIds.sorted()
        output.writeInt(sortedParents.size)
        sortedParents.forEach(output::writeUTF)
    }
    bytes.toByteArray()
}

private fun eventIdFor(payload: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(payload))

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
