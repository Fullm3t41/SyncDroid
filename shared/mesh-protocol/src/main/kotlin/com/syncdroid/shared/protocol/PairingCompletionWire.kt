package com.syncdroid.shared.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

data class WrappedFolderKeyTransfer(
    val folderId: String,
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

sealed interface PairingCompletionMessage {
    data class Complete(
        val groupId: String,
        val groupName: String,
        val meshBundle: ByteArray,
        val folderKeys: List<WrappedFolderKeyTransfer> = emptyList(),
    ) : PairingCompletionMessage
    data object Ack : PairingCompletionMessage
}

object PairingCompletionWireCodec {
    fun encode(message: PairingCompletionMessage): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            when (message) {
                PairingCompletionMessage.Ack -> output.writeByte(ACK)
                is PairingCompletionMessage.Complete -> {
                    output.writeByte(COMPLETE)
                    output.writeString(message.groupId)
                    output.writeString(message.groupName)
                    output.writeData(message.meshBundle)
                    require(message.folderKeys.size <= MAX_KEYS)
                    output.writeInt(message.folderKeys.size)
                    message.folderKeys.forEach { key ->
                        output.writeString(key.folderId)
                        output.writeString(key.keyId)
                        output.writeData(key.nonce)
                        output.writeData(key.ciphertext)
                    }
                }
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): PairingCompletionMessage = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC) { "Invalid pairing completion message" }
        val message = when (val type = input.readUnsignedByte()) {
            ACK -> PairingCompletionMessage.Ack
            COMPLETE -> PairingCompletionMessage.Complete(
                input.readString(),
                input.readString(),
                input.readData(),
                List(input.readInt().also { require(it in 0..MAX_KEYS) }) {
                    WrappedFolderKeyTransfer(input.readString(), input.readString(), input.readData(), input.readData())
                },
            )
            else -> error("Unknown pairing completion message $type")
        }
        require(input.available() == 0) { "Trailing pairing completion data" }
        message
    }

    private fun DataOutputStream.writeString(value: String) = writeData(value.toByteArray(StandardCharsets.UTF_8))
    private fun DataInputStream.readString() = String(readData(), StandardCharsets.UTF_8)
    private fun DataOutputStream.writeData(value: ByteArray) {
        require(value.size <= MAX_DATA_BYTES)
        writeInt(value.size)
        write(value)
    }
    private fun DataInputStream.readData() = ByteArray(readInt().also { require(it in 0..MAX_DATA_BYTES) }).also(::readFully)

    private const val MAGIC = 0x53445043
    private const val COMPLETE = 1
    private const val ACK = 2
    private const val MAX_KEYS = 10_000
    private const val MAX_DATA_BYTES = 16 * 1024 * 1024
}
