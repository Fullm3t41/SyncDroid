package com.synctosh.app.mesh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.Normalizer
import java.util.Base64

data class VersionVector(val counters: Map<String, Long> = emptyMap()) {
    fun increment(deviceId: String) = copy(counters = counters + (deviceId to ((counters[deviceId] ?: 0L) + 1L)))

    fun merge(other: VersionVector): VersionVector {
        val keys = counters.keys + other.counters.keys
        return VersionVector(keys.associateWith { maxOf(counters[it] ?: 0L, other.counters[it] ?: 0L) })
    }

    fun relationTo(other: VersionVector): CausalRelation {
        val keys = counters.keys + other.counters.keys
        var less = false
        var greater = false
        keys.forEach { key ->
            val ours = counters[key] ?: 0L
            val theirs = other.counters[key] ?: 0L
            if (ours < theirs) less = true
            if (ours > theirs) greater = true
        }
        return when {
            less && greater -> CausalRelation.Concurrent
            less -> CausalRelation.Before
            greater -> CausalRelation.After
            else -> CausalRelation.Equal
        }
    }

    fun toJson(): String = counters.entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { (id, value) ->
        require(id.matches(DEVICE_ID_PATTERN))
        "\"$id\":$value"
    }

    companion object {
        fun fromJson(encoded: String): VersionVector {
            val body = encoded.trim().removePrefix("{").removeSuffix("}").trim()
            if (body.isEmpty()) return VersionVector()
            return VersionVector(body.split(',').associate { entry ->
                val separator = entry.indexOf(':')
                require(separator > 0) { "Invalid version vector" }
                val id = entry.substring(0, separator).trim().removeSurrounding("\"")
                require(id.matches(DEVICE_ID_PATTERN)) { "Invalid device ID" }
                id to entry.substring(separator + 1).trim().toLong().also { require(it >= 0) }
            })
        }

        private val DEVICE_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}

enum class CausalRelation { Before, After, Equal, Concurrent }

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
    fun canonicalPayload(): ByteArray = canonicalBytes {
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

    fun isStructurallyValid(): Boolean = runCatching {
        payloadForValidation() != null &&
            subjectDeviceId == deviceIdFor(decodePublicKey(subjectPublicKeyBase64))
    }.getOrDefault(false)

    fun verifySignature(signerPublicKey: PublicKey): Boolean = runCatching {
        val payload = requireNotNull(payloadForValidation()) { "Membership event ID does not match its payload" }
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(signerPublicKey)
            update(payload)
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

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

    private fun legacyCanonicalPayload(): ByteArray = ByteArrayOutputStream().use { bytes ->
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
            val parents = parentEventIds.sorted()
            output.writeInt(parents.size)
            parents.forEach(output::writeUTF)
        }
        bytes.toByteArray()
    }

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
    fun canonicalPayload(): ByteArray = canonicalBytes {
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

    fun hasValidEventId(): Boolean = payloadForValidation() != null

    fun verifySignature(signerPublicKey: PublicKey): Boolean = runCatching {
        val payload = requireNotNull(payloadForValidation()) { "Folder event ID does not match its payload" }
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(signerPublicKey)
            update(payload)
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

    private fun payloadForValidation(): ByteArray? {
        val current = canonicalPayload()
        if (eventId == eventIdFor(current)) return current
        val legacy = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF("syncdroid-folder-announcement-v1")
                output.writeUTF(groupId)
                output.writeUTF(folderId)
                output.writeUTF(displayName)
                output.writeUTF(signerDeviceId)
                output.writeLong(createdAtMillis)
                output.writeUTF(version.toJson())
                output.writeInt(includePatterns.size)
                includePatterns.forEach(output::writeUTF)
                output.writeInt(excludePatterns.size)
                excludePatterns.forEach(output::writeUTF)
            }
            bytes.toByteArray()
        }
        return legacy.takeIf { eventId == eventIdFor(it) }
    }
}

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
)

data class MeshChatMessage(
    val messageId: String,
    val groupId: String,
    val authorDeviceId: String,
    val body: String,
    val createdAtMillis: Long,
    val signatureBase64: String,
) {
    fun canonicalPayload(): ByteArray = canonicalBytes {
        string("syncdroid-chat-v1")
        string(groupId)
        string(authorDeviceId)
        string(body)
        int64(createdAtMillis)
    }

    fun hasValidMessageId(): Boolean = messageId == eventIdFor(canonicalPayload())

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

data class MeshStateBundle(
    val groupName: String,
    val membershipEvents: List<MembershipEvent>,
    val folderAnnouncements: List<FolderAnnouncement> = emptyList(),
    val syncExceptionEvents: List<SyncExceptionEvent> = emptyList(),
    val chatMessages: List<MeshChatMessage> = emptyList(),
)

object MeshWireCodec {
    fun encode(bundle: MeshStateBundle): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write(MAGIC)
            output.writeShort(PROTOCOL_MAJOR)
            output.writeShort(PROTOCOL_MINOR)
            output.writeUtf8(bundle.groupName)
            output.writeInt(bundle.membershipEvents.safeSize())
            bundle.membershipEvents.forEach { output.writeMembership(it) }
            output.writeInt(bundle.folderAnnouncements.safeSize())
            bundle.folderAnnouncements.forEach { output.writeFolder(it) }
            output.writeInt(bundle.syncExceptionEvents.safeSize())
            bundle.syncExceptionEvents.forEach { output.writeException(it) }
            output.writeInt(bundle.chatMessages.safeSize())
            bundle.chatMessages.forEach { output.writeChat(it) }
        }
        require(bytes.size() <= MAX_BUNDLE_BYTES)
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): MeshStateBundle {
        require(bytes.size <= MAX_BUNDLE_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(ByteArray(MAGIC.size).also(input::readFully).contentEquals(MAGIC))
            require(input.readUnsignedShort() == PROTOCOL_MAJOR)
            val minor = input.readUnsignedShort()
            require(minor <= PROTOCOL_MINOR)
            val name = input.readUtf8()
            val memberships = List(input.readCount()) { input.readMembership() }
            val folders = List(input.readCount()) { input.readFolder() }
            val exceptions = if (minor >= 1) List(input.readCount()) { input.readException() } else emptyList()
            val chat = if (minor >= 2) List(input.readCount()) { input.readChat() } else emptyList()
            require(input.available() == 0)
            MeshStateBundle(name, memberships, folders, exceptions, chat)
        }
    }

    private fun DataOutputStream.writeMembership(value: MembershipEvent) {
        writeUtf8(value.eventId); writeUtf8(value.groupId); writeUtf8(value.eventType.name)
        writeUtf8(value.subjectDeviceId); writeUtf8(value.subjectDisplayName); writeUtf8(value.subjectPublicKeyBase64)
        writeUtf8(value.signerDeviceId); writeList(value.parentEventIds); writeUtf8(value.version.toJson())
        writeLong(value.createdAtMillis); writeUtf8(value.signatureBase64)
    }

    private fun DataInputStream.readMembership() = MembershipEvent(
        readUtf8(), readUtf8(), MembershipEventType.valueOf(readUtf8()), readUtf8(), readUtf8(), readUtf8(),
        readUtf8(), readList(), VersionVector.fromJson(readUtf8()), readLong(), readUtf8(),
    )

    private fun DataOutputStream.writeFolder(value: FolderAnnouncement) {
        writeUtf8(value.eventId); writeUtf8(value.groupId); writeUtf8(value.folderId); writeUtf8(value.displayName)
        writeList(value.includePatterns); writeList(value.excludePatterns); writeUtf8(value.signerDeviceId)
        writeUtf8(value.version.toJson()); writeLong(value.createdAtMillis); writeUtf8(value.signatureBase64)
    }

    private fun DataInputStream.readFolder() = FolderAnnouncement(
        readUtf8(), readUtf8(), readUtf8(), readUtf8(), readList(), readList(), readUtf8(),
        VersionVector.fromJson(readUtf8()), readLong(), readUtf8(),
    )

    private fun DataOutputStream.writeException(value: SyncExceptionEvent) {
        writeUtf8(value.eventId); writeUtf8(value.groupId); writeUtf8(value.folderId); writeUtf8(value.relativePath)
        writeBoolean(value.active); writeUtf8(value.signerDeviceId); writeUtf8(value.version.toJson())
        writeLong(value.createdAtMillis); writeUtf8(value.signatureBase64)
    }

    private fun DataInputStream.readException() = SyncExceptionEvent(
        readUtf8(), readUtf8(), readUtf8(), readUtf8(), readBoolean(), readUtf8(),
        VersionVector.fromJson(readUtf8()), readLong(), readUtf8(),
    )

    private fun DataOutputStream.writeChat(value: MeshChatMessage) {
        writeUtf8(value.messageId); writeUtf8(value.groupId); writeUtf8(value.authorDeviceId); writeUtf8(value.body)
        writeLong(value.createdAtMillis); writeUtf8(value.signatureBase64)
    }

    private fun DataInputStream.readChat() = MeshChatMessage(
        readUtf8(), readUtf8(), readUtf8(), readUtf8(), readLong(), readUtf8(),
    )

    private fun DataOutputStream.writeUtf8(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size); write(bytes)
    }

    private fun DataInputStream.readUtf8(): String {
        val size = readInt().also { require(it in 0..MAX_STRING_BYTES) }
        return String(ByteArray(size).also(::readFully), StandardCharsets.UTF_8)
    }

    private fun DataOutputStream.writeList(values: List<String>) {
        writeInt(values.safeSize()); values.forEach { writeUtf8(it) }
    }

    private fun DataInputStream.readList() = List(readCount()) { readUtf8() }
    private fun DataInputStream.readCount() = readInt().also { require(it in 0..MAX_ITEMS) }
    private fun Collection<*>.safeSize() = size.also { require(it <= MAX_ITEMS) }

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'D'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte())
    private const val PROTOCOL_MAJOR = 2
    private const val PROTOCOL_MINOR = 3
    private const val MAX_ITEMS = 10_000
    private const val MAX_STRING_BYTES = 1024 * 1024
    private const val MAX_BUNDLE_BYTES = 16 * 1024 * 1024
}

internal fun canonicalBytes(block: CanonicalOutput.() -> Unit): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { CanonicalOutput(it).block() }
    bytes.toByteArray()
}

internal class CanonicalOutput(private val output: DataOutputStream) {
    fun string(value: String) {
        val bytes = Normalizer.normalize(value, Normalizer.Form.NFC).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 1024 * 1024)
        output.writeInt(bytes.size); output.write(bytes)
    }
    fun int64(value: Long) = output.writeLong(value)
    fun strings(values: List<String>) { require(values.size <= 10_000); output.writeInt(values.size); values.forEach(::string) }
}

internal fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
internal fun eventIdFor(payload: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(payload))

internal const val MAX_CHAT_BODY_BYTES = 4_000
