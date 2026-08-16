package com.syncdroid.shared.sync

import com.syncdroid.shared.protocol.FileBlock
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.BitSet

data class ResumableTransferProgress(val receivedBlocksBase64: String = "") {
    fun missingBlocks(blocks: List<FileBlock>): List<Int> {
        val received = bits()
        return blocks.map(FileBlock::index).filterNot(received::get)
    }

    fun hasReceived(blockIndex: Int): Boolean {
        require(blockIndex >= 0)
        return bits().get(blockIndex)
    }

    fun record(blockIndex: Int): ResumableTransferProgress {
        require(blockIndex >= 0)
        val updated = bits().apply { set(blockIndex) }
        return ResumableTransferProgress(Base64.getEncoder().encodeToString(updated.toByteArray()))
    }

    fun isComplete(blocks: List<FileBlock>): Boolean = missingBlocks(blocks).isEmpty()

    private fun bits(): BitSet = if (receivedBlocksBase64.isBlank()) {
        BitSet()
    } else {
        BitSet.valueOf(Base64.getDecoder().decode(receivedBlocksBase64))
    }
}

fun isCompatiblePartialTransfer(
    expectedSizeBytes: Long,
    expectedBlockSizeBytes: Int,
    storedSizeBytes: Long,
    storedBlockSizeBytes: Int,
): Boolean = expectedSizeBytes == storedSizeBytes && expectedBlockSizeBytes == storedBlockSizeBytes

fun resumableTransferId(folderId: String, fileId: String, contentSha256: String): String =
    MessageDigest.getInstance("SHA-256").digest(
        "$folderId\u0000$fileId\u0000$contentSha256".toByteArray(StandardCharsets.UTF_8),
    ).joinToString("") { "%02x".format(it) }

fun validateReceivedBlock(block: FileBlock, blockIndex: Int, data: ByteArray) {
    require(block.index == blockIndex) { "Peer returned the wrong block" }
    require(data.size == block.sizeBytes) { "Received block has the wrong size" }
    val actual = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    require(actual.equals(block.sha256, true)) { "Received block hash does not match its manifest" }
}
