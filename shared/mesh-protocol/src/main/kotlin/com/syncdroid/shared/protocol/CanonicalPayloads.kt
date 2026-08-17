package com.syncdroid.shared.protocol

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Base64

fun canonicalBytes(block: CanonicalOutput.() -> Unit): ByteArray =
    ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output -> CanonicalOutput(output).block() }
        bytes.toByteArray()
    }

class CanonicalOutput(private val output: DataOutputStream) {
    fun string(value: String) {
        val encoded = Normalizer.normalize(value, Normalizer.Form.NFC).toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= MAX_CANONICAL_STRING_BYTES) { "Canonical string is too large" }
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    fun int64(value: Long) = output.writeLong(value)

    fun bool(value: Boolean) = output.writeByte(if (value) 1 else 0)

    fun strings(values: List<String>) {
        require(values.size <= MAX_CANONICAL_ITEMS) { "Canonical list is too large" }
        output.writeInt(values.size)
        values.forEach(::string)
    }
}

fun canonicalMembershipPayload(
    groupId: String,
    eventType: String,
    subjectDeviceId: String,
    subjectDisplayName: String,
    subjectPublicKeyBase64: String,
    signerDeviceId: String,
    parentEventIds: List<String>,
    versionJson: String,
    createdAtMillis: Long,
): ByteArray = canonicalBytes {
    string("syncdroid-membership-v2")
    string(groupId)
    string(eventType)
    string(subjectDeviceId)
    string(subjectDisplayName)
    string(subjectPublicKeyBase64)
    string(signerDeviceId)
    int64(createdAtMillis)
    string(versionJson)
    strings(parentEventIds.sorted())
}

fun legacyMembershipPayload(
    groupId: String,
    eventType: String,
    subjectDeviceId: String,
    subjectDisplayName: String,
    subjectPublicKeyBase64: String,
    signerDeviceId: String,
    parentEventIds: List<String>,
    versionJson: String,
    createdAtMillis: Long,
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.writeUTF("syncdroid-membership-v1")
        output.writeUTF(groupId)
        output.writeUTF(eventType)
        output.writeUTF(subjectDeviceId)
        output.writeUTF(subjectDisplayName)
        output.writeUTF(subjectPublicKeyBase64)
        output.writeUTF(signerDeviceId)
        output.writeLong(createdAtMillis)
        output.writeUTF(versionJson)
        val parents = parentEventIds.sorted()
        output.writeInt(parents.size)
        parents.forEach(output::writeUTF)
    }
    bytes.toByteArray()
}

fun canonicalFolderAnnouncementPayload(
    groupId: String,
    folderId: String,
    displayName: String,
    includePatterns: List<String>,
    excludePatterns: List<String>,
    signerDeviceId: String,
    versionJson: String,
    createdAtMillis: Long,
): ByteArray = canonicalBytes {
    string("syncdroid-folder-announcement-v2")
    string(groupId)
    string(folderId)
    string(displayName)
    string(signerDeviceId)
    int64(createdAtMillis)
    string(versionJson)
    strings(includePatterns)
    strings(excludePatterns)
}

fun legacyFolderAnnouncementPayload(
    groupId: String,
    folderId: String,
    displayName: String,
    includePatterns: List<String>,
    excludePatterns: List<String>,
    signerDeviceId: String,
    versionJson: String,
    createdAtMillis: Long,
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.writeUTF("syncdroid-folder-announcement-v1")
        output.writeUTF(groupId)
        output.writeUTF(folderId)
        output.writeUTF(displayName)
        output.writeUTF(signerDeviceId)
        output.writeLong(createdAtMillis)
        output.writeUTF(versionJson)
        output.writeInt(includePatterns.size)
        includePatterns.forEach(output::writeUTF)
        output.writeInt(excludePatterns.size)
        excludePatterns.forEach(output::writeUTF)
    }
    bytes.toByteArray()
}

fun canonicalChatPayload(
    groupId: String,
    authorDeviceId: String,
    body: String,
    createdAtMillis: Long,
    attachment: WireChatAttachment? = null,
): ByteArray = if (attachment == null) canonicalBytes {
        string("syncdroid-chat-v1")
        string(groupId)
        string(authorDeviceId)
        string(body)
        int64(createdAtMillis)
    } else canonicalBytes {
        string("syncdroid-chat-v2")
        string(groupId)
        string(authorDeviceId)
        string(body)
        int64(createdAtMillis)
        string(attachment.fileName)
        string(attachment.mediaType)
        int64(attachment.sizeBytes)
        string(attachment.contentSha256)
        int64(attachment.expiresAtMillis)
    }

fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

fun eventIdFor(payload: ByteArray): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(sha256(payload))

private const val MAX_CANONICAL_STRING_BYTES = 1024 * 1024
private const val MAX_CANONICAL_ITEMS = 10_000
