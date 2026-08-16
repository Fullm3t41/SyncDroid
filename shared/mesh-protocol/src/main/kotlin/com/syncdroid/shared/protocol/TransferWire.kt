package com.syncdroid.shared.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

sealed interface FileTransferMessage {
    data class WholeFileRequest(
        val folderId: String,
        val fileId: String,
        val relativePath: String,
        val contentSha256: String,
    ) : FileTransferMessage

    data class FileStart(val sizeBytes: Long, val modifiedAtMillis: Long) : FileTransferMessage
    data class FileChunk(val sequence: Int, val data: ByteArray) : FileTransferMessage
    data class FileEnd(val contentSha256: String) : FileTransferMessage
    data class BlockRequest(
        val folderId: String,
        val fileId: String,
        val relativePath: String,
        val contentSha256: String,
        val blockIndex: Int,
    ) : FileTransferMessage
    data class BlockResponse(val blockIndex: Int, val data: ByteArray) : FileTransferMessage
    data class Error(val reason: String) : FileTransferMessage
}

/** Wire-compatible with the SDFT whole-file and resumable block messages. */
object FileTransferWireCodec {
    fun encode(message: FileTransferMessage): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            when (message) {
                is FileTransferMessage.WholeFileRequest -> {
                    output.writeByte(WHOLE_REQUEST)
                    output.writeString(message.folderId)
                    output.writeString(message.fileId)
                    output.writeString(message.relativePath)
                    output.writeString(message.contentSha256)
                }
                is FileTransferMessage.FileStart -> {
                    output.writeByte(FILE_START)
                    output.writeLong(message.sizeBytes)
                    output.writeLong(message.modifiedAtMillis)
                }
                is FileTransferMessage.FileChunk -> {
                    output.writeByte(FILE_CHUNK)
                    output.writeInt(message.sequence)
                    output.writeData(message.data)
                }
                is FileTransferMessage.FileEnd -> {
                    output.writeByte(FILE_END)
                    output.writeString(message.contentSha256)
                }
                is FileTransferMessage.BlockRequest -> {
                    output.writeByte(BLOCK_REQUEST)
                    output.writeString(message.folderId)
                    output.writeString(message.fileId)
                    output.writeString(message.relativePath)
                    output.writeString(message.contentSha256)
                    output.writeInt(message.blockIndex)
                }
                is FileTransferMessage.BlockResponse -> {
                    output.writeByte(BLOCK_RESPONSE)
                    output.writeInt(message.blockIndex)
                    output.writeData(message.data)
                }
                is FileTransferMessage.Error -> {
                    output.writeByte(ERROR)
                    output.writeString(message.reason)
                }
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): FileTransferMessage = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC) { "Unsupported file-transfer protocol" }
        val message = when (val type = input.readUnsignedByte()) {
            WHOLE_REQUEST -> FileTransferMessage.WholeFileRequest(
                input.readString(), input.readString(), input.readString(), input.readString(),
            )
            FILE_START -> FileTransferMessage.FileStart(input.readLong(), input.readLong())
            FILE_CHUNK -> FileTransferMessage.FileChunk(input.readInt(), input.readData())
            FILE_END -> FileTransferMessage.FileEnd(input.readString())
            BLOCK_REQUEST -> FileTransferMessage.BlockRequest(
                input.readString(), input.readString(), input.readString(), input.readString(), input.readInt(),
            )
            BLOCK_RESPONSE -> FileTransferMessage.BlockResponse(input.readInt(), input.readData())
            ERROR -> FileTransferMessage.Error(input.readString())
            else -> error("Unknown file-transfer message $type")
        }
        require(input.available() == 0) { "Trailing file-transfer data" }
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

    private const val MAGIC = 0x53444654
    private const val WHOLE_REQUEST = 1
    private const val FILE_START = 2
    private const val FILE_CHUNK = 3
    private const val FILE_END = 4
    private const val BLOCK_REQUEST = 5
    private const val BLOCK_RESPONSE = 6
    private const val ERROR = 127
    private const val MAX_DATA_BYTES = 16 * 1024 * 1024
}
