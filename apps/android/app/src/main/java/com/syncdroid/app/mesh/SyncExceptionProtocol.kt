package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

data class SyncExceptionEvent(
    val eventId: String,
    val groupId: String,
    val folderId: String,
    val relativePath: String,
    val active: Boolean,
    val signerDeviceId: String,
    val version: VersionVector,
    val createdAtMillis: Long,
    val signatureBase64: String,
) {
    fun canonicalPayload(): ByteArray = canonicalExceptionPayload(
        groupId,
        folderId,
        normalizedMeshPath(relativePath),
        active,
        signerDeviceId,
        version,
        createdAtMillis,
    )

    fun hasValidEventId(): Boolean = eventId == exceptionEventId(canonicalPayload())

    fun verifySignature(publicKey: PublicKey): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(canonicalPayload())
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

    companion object {
        fun create(
            groupId: String,
            folderId: String,
            relativePath: String,
            active: Boolean,
            signer: DeviceSigner,
            version: VersionVector,
            createdAtMillis: Long = System.currentTimeMillis(),
        ): SyncExceptionEvent {
            val normalizedPath = normalizedMeshPath(relativePath)
            val payload = canonicalExceptionPayload(
                groupId,
                folderId,
                normalizedPath,
                active,
                signer.deviceId,
                version,
                createdAtMillis,
            )
            return SyncExceptionEvent(
                eventId = exceptionEventId(payload),
                groupId = groupId,
                folderId = folderId,
                relativePath = normalizedPath,
                active = active,
                signerDeviceId = signer.deviceId,
                version = version,
                createdAtMillis = createdAtMillis,
                signatureBase64 = Base64.getEncoder().encodeToString(signer.sign(payload)),
            )
        }
    }
}

private fun canonicalExceptionPayload(
    groupId: String,
    folderId: String,
    relativePath: String,
    active: Boolean,
    signerDeviceId: String,
    version: VersionVector,
    createdAtMillis: Long,
): ByteArray = canonicalBytes {
    string("syncdroid-exception-v1")
    string(groupId)
    string(folderId)
    string(relativePath)
    bool(active)
    string(signerDeviceId)
    int64(createdAtMillis)
    string(version.toJson())
}

internal fun normalizedMeshPath(path: String): String {
    val normalized = path.replace('\\', '/').trim('/')
    require(normalized.isNotEmpty() && normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
        "Invalid mesh relative path"
    }
    return normalized
}

private fun exceptionEventId(payload: ByteArray): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(payload))
