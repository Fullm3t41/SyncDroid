package com.syncdroid.app.mesh

import com.syncdroid.app.data.ChatMessageEntity
import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.shared.protocol.canonicalChatPayload
import com.syncdroid.shared.protocol.eventIdFor
import com.syncdroid.shared.protocol.verifyEcdsaSha256
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.util.Base64

data class MeshChatMessage(
    val messageId: String,
    val groupId: String,
    val authorDeviceId: String,
    val body: String,
    val createdAtMillis: Long,
    val signatureBase64: String,
) {
    fun canonicalPayload(): ByteArray = canonicalChatPayload(
        groupId = groupId,
        authorDeviceId = authorDeviceId,
        body = body,
        createdAtMillis = createdAtMillis,
    )

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
            val payload = canonicalChatPayload(groupId, signer.deviceId, cleanBody, createdAtMillis)
            return MeshChatMessage(
                messageId = eventIdFor(payload),
                groupId = groupId,
                authorDeviceId = signer.deviceId,
                body = cleanBody,
                createdAtMillis = createdAtMillis,
                signatureBase64 = Base64.getEncoder().encodeToString(signer.sign(payload)),
            )
        }
    }
}

class MeshChatRepository(
    database: SyncDroidDatabase,
    private val signer: DeviceSigner,
) {
    private val meshDao = database.meshDao()
    private val chatDao = database.chatDao()

    suspend fun send(groupId: String, body: String): MeshChatMessage {
        val author = requireNotNull(meshDao.getDevice(groupId, signer.deviceId)) {
            "The current device is not a member of this mesh"
        }
        require(author.trustState == TRUSTED) { "The current device is not trusted" }
        return MeshChatMessage.create(groupId, body, signer).also { applyVerified(it) }
    }

    suspend fun receive(message: MeshChatMessage): Boolean = applyVerified(message)

    private suspend fun applyVerified(message: MeshChatMessage): Boolean {
        require(message.body.toByteArray(StandardCharsets.UTF_8).size <= MAX_CHAT_BODY_BYTES) {
            "A chat message is too long"
        }
        require(message.body.isNotBlank() && message.body == message.body.trim()) {
            "A chat message has invalid whitespace"
        }
        require(message.hasValidMessageId()) { "Chat message ID does not match its payload" }
        val author = requireNotNull(meshDao.getDevice(message.groupId, message.authorDeviceId)) {
            "Chat message author is not a member of this mesh"
        }
        require(author.trustState == TRUSTED) { "Chat message author is not trusted" }
        require(message.verifySignature(decodePublicKey(author.publicKeyBase64))) {
            "Chat message signature is invalid"
        }
        return chatDao.insertMessage(message.toEntity()) != -1L
    }
}

internal fun ChatMessageEntity.toDomain() = MeshChatMessage(
    messageId = messageId,
    groupId = groupId,
    authorDeviceId = authorDeviceId,
    body = body,
    createdAtMillis = createdAtMillis,
    signatureBase64 = signatureBase64,
)

private fun MeshChatMessage.toEntity() = ChatMessageEntity(
    messageId = messageId,
    groupId = groupId,
    authorDeviceId = authorDeviceId,
    body = body,
    createdAtMillis = createdAtMillis,
    signatureBase64 = signatureBase64,
)

private const val MAX_CHAT_BODY_BYTES = 4_000
private const val TRUSTED = "TRUSTED"
