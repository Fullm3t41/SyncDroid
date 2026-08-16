package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

data class MeshStateBundle(
    val groupName: String,
    val membershipEvents: List<MembershipEvent>,
    val folderAnnouncements: List<FolderAnnouncement>,
    val syncExceptionEvents: List<SyncExceptionEvent> = emptyList(),
    val chatMessages: List<MeshChatMessage> = emptyList(),
)

object MeshWireCodec {
    fun encode(bundle: MeshStateBundle): ByteArray = ByteArrayOutputStream().use { bytes ->
        require(bundle.membershipEvents.size <= MAX_ITEMS) { "Too many membership events" }
        require(bundle.folderAnnouncements.size <= MAX_ITEMS) { "Too many folder announcements" }
        require(bundle.syncExceptionEvents.size <= MAX_ITEMS) { "Too many exception events" }
        require(bundle.chatMessages.size <= MAX_ITEMS) { "Too many chat messages" }
        DataOutputStream(bytes).use { output ->
            output.write(MAGIC_V2)
            output.writeShort(PROTOCOL_MAJOR)
            output.writeShort(PROTOCOL_MINOR)
            output.writeUtf8(bundle.groupName)
            output.writeInt(bundle.membershipEvents.size)
            bundle.membershipEvents.forEach { output.writeMembershipV2(it) }
            output.writeInt(bundle.folderAnnouncements.size)
            bundle.folderAnnouncements.forEach { output.writeFolderV2(it) }
            output.writeInt(bundle.syncExceptionEvents.size)
            bundle.syncExceptionEvents.forEach { output.writeExceptionV2(it) }
            output.writeInt(bundle.chatMessages.size)
            bundle.chatMessages.forEach { output.writeChatV2(it) }
        }
        require(bytes.size() <= MAX_BUNDLE_BYTES) { "Mesh metadata bundle is too large" }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): MeshStateBundle = runCatching {
        require(bytes.size <= MAX_BUNDLE_BYTES) { "Mesh metadata bundle is too large" }
        if (bytes.startsWith(MAGIC_V2)) decodeV2(bytes) else decodeLegacyV1(bytes)
    }.getOrElse { error ->
        if (error is IllegalArgumentException) throw error
        throw IllegalArgumentException("Invalid mesh metadata bundle", error)
    }

    private fun decodeV2(bytes: ByteArray): MeshStateBundle =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = ByteArray(MAGIC_V2.size).also(input::readFully)
            require(magic.contentEquals(MAGIC_V2)) { "Unsupported mesh protocol" }
            val major = input.readUnsignedShort()
            val minor = input.readUnsignedShort()
            require(major == PROTOCOL_MAJOR) { "Unsupported mesh protocol major version: $major" }
            require(minor <= PROTOCOL_MINOR) { "Unsupported mesh protocol minor version: $minor" }
            val groupName = input.readUtf8()
            val memberships = List(input.readSafeCount()) { input.readMembershipV2() }
            val folders = List(input.readSafeCount()) { input.readFolderV2() }
            val exceptions = if (minor >= 1) {
                List(input.readSafeCount()) { input.readExceptionV2() }
            } else {
                emptyList()
            }
            val chatMessages = if (minor >= 2) {
                List(input.readSafeCount()) { input.readChatV2() }
            } else {
                emptyList()
            }
            require(input.available() == 0) { "Unexpected data after mesh bundle" }
            MeshStateBundle(groupName, memberships, folders, exceptions, chatMessages)
        }

    private fun decodeLegacyV1(bytes: ByteArray): MeshStateBundle =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUTF() == LEGACY_MAGIC_V1) { "Unsupported mesh protocol" }
            val groupName = input.readUTF()
            val memberships = List(input.readSafeCount()) { input.readMembershipV1() }
            val folders = List(input.readSafeCount()) { input.readFolderV1() }
            require(input.available() == 0) { "Unexpected data after mesh bundle" }
            MeshStateBundle(groupName, memberships, folders)
        }

    private fun DataOutputStream.writeMembershipV2(event: MembershipEvent) {
        writeUtf8(event.eventId)
        writeUtf8(event.groupId)
        writeUtf8(event.eventType.name)
        writeUtf8(event.subjectDeviceId)
        writeUtf8(event.subjectDisplayName)
        writeUtf8(event.subjectPublicKeyBase64)
        writeUtf8(event.signerDeviceId)
        writeUtf8List(event.parentEventIds)
        writeUtf8(event.version.toJson())
        writeLong(event.createdAtMillis)
        writeUtf8(event.signatureBase64)
    }

    private fun DataInputStream.readMembershipV2() = MembershipEvent(
        eventId = readUtf8(),
        groupId = readUtf8(),
        eventType = MembershipEventType.valueOf(readUtf8()),
        subjectDeviceId = readUtf8(),
        subjectDisplayName = readUtf8(),
        subjectPublicKeyBase64 = readUtf8(),
        signerDeviceId = readUtf8(),
        parentEventIds = readUtf8List(),
        version = VersionVector.fromJson(readUtf8()),
        createdAtMillis = readLong(),
        signatureBase64 = readUtf8(),
    )

    private fun DataOutputStream.writeFolderV2(event: FolderAnnouncement) {
        writeUtf8(event.eventId)
        writeUtf8(event.groupId)
        writeUtf8(event.folderId)
        writeUtf8(event.displayName)
        writeUtf8List(event.includePatterns)
        writeUtf8List(event.excludePatterns)
        writeUtf8(event.signerDeviceId)
        writeUtf8(event.version.toJson())
        writeLong(event.createdAtMillis)
        writeUtf8(event.signatureBase64)
    }

    private fun DataInputStream.readFolderV2() = FolderAnnouncement(
        eventId = readUtf8(),
        groupId = readUtf8(),
        folderId = readUtf8(),
        displayName = readUtf8(),
        includePatterns = readUtf8List(),
        excludePatterns = readUtf8List(),
        signerDeviceId = readUtf8(),
        version = VersionVector.fromJson(readUtf8()),
        createdAtMillis = readLong(),
        signatureBase64 = readUtf8(),
    )

    private fun DataOutputStream.writeExceptionV2(event: SyncExceptionEvent) {
        writeUtf8(event.eventId)
        writeUtf8(event.groupId)
        writeUtf8(event.folderId)
        writeUtf8(event.relativePath)
        writeBoolean(event.active)
        writeUtf8(event.signerDeviceId)
        writeUtf8(event.version.toJson())
        writeLong(event.createdAtMillis)
        writeUtf8(event.signatureBase64)
    }

    private fun DataInputStream.readExceptionV2() = SyncExceptionEvent(
        eventId = readUtf8(),
        groupId = readUtf8(),
        folderId = readUtf8(),
        relativePath = readUtf8(),
        active = readBoolean(),
        signerDeviceId = readUtf8(),
        version = VersionVector.fromJson(readUtf8()),
        createdAtMillis = readLong(),
        signatureBase64 = readUtf8(),
    )

    private fun DataOutputStream.writeChatV2(message: MeshChatMessage) {
        writeUtf8(message.messageId)
        writeUtf8(message.groupId)
        writeUtf8(message.authorDeviceId)
        writeUtf8(message.body)
        writeLong(message.createdAtMillis)
        writeUtf8(message.signatureBase64)
    }

    private fun DataInputStream.readChatV2() = MeshChatMessage(
        messageId = readUtf8(),
        groupId = readUtf8(),
        authorDeviceId = readUtf8(),
        body = readUtf8(),
        createdAtMillis = readLong(),
        signatureBase64 = readUtf8(),
    )

    private fun DataOutputStream.writeUtf8(value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= MAX_STRING_BYTES) { "Mesh string is too large" }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readUtf8(): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES) { "Invalid mesh string length" }
        return String(ByteArray(size).also(::readFully), StandardCharsets.UTF_8)
    }

    private fun DataOutputStream.writeUtf8List(values: List<String>) {
        require(values.size <= MAX_ITEMS) { "Mesh list is too large" }
        writeInt(values.size)
        values.forEach { writeUtf8(it) }
    }

    private fun DataInputStream.readUtf8List(): List<String> = List(readSafeCount()) { readUtf8() }

    private fun DataInputStream.readMembershipV1() = MembershipEvent(
        eventId = readUTF(),
        groupId = readUTF(),
        eventType = MembershipEventType.valueOf(readUTF()),
        subjectDeviceId = readUTF(),
        subjectDisplayName = readUTF(),
        subjectPublicKeyBase64 = readUTF(),
        signerDeviceId = readUTF(),
        parentEventIds = readLegacyStringList(),
        version = VersionVector.fromJson(readUTF()),
        createdAtMillis = readLong(),
        signatureBase64 = readUTF(),
    )

    private fun DataInputStream.readFolderV1() = FolderAnnouncement(
        eventId = readUTF(),
        groupId = readUTF(),
        folderId = readUTF(),
        displayName = readUTF(),
        includePatterns = readLegacyStringList(),
        excludePatterns = readLegacyStringList(),
        signerDeviceId = readUTF(),
        version = VersionVector.fromJson(readUTF()),
        createdAtMillis = readLong(),
        signatureBase64 = readUTF(),
    )

    private fun DataInputStream.readLegacyStringList(): List<String> = List(readSafeCount()) { readUTF() }

    private fun DataInputStream.readSafeCount(): Int = readInt().also {
        require(it in 0..MAX_ITEMS) { "Invalid mesh item count" }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val MAGIC_V2 = byteArrayOf('S'.code.toByte(), 'D'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte())
    private const val LEGACY_MAGIC_V1 = "syncdroid-mesh-state-v1"
    private const val PROTOCOL_MAJOR = 2
    private const val PROTOCOL_MINOR = 3
    private const val MAX_ITEMS = 10_000
    private const val MAX_STRING_BYTES = 1024 * 1024
    private const val MAX_BUNDLE_BYTES = 16 * 1024 * 1024
}
