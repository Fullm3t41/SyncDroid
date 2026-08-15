package com.syncdroid.app.sync

import com.syncdroid.app.data.FileBlockEntity
import com.syncdroid.app.data.SyncDao
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

data class FileBlock(
    val index: Int,
    val offsetBytes: Long,
    val sizeBytes: Int,
    val sha256: String,
)

data class BlockManifest(
    val folderId: String,
    val fileId: String,
    val relativePath: String,
    val sizeBytes: Long,
    val contentSha256: String,
    val blockSizeBytes: Int,
    val blocks: List<FileBlock>,
)

object BlockManifestBuilder {
    fun build(
        folderId: String,
        fileId: String,
        relativePath: String,
        file: File,
    ): BlockManifest = FileInputStream(file).buffered().use { input ->
        build(folderId, fileId, relativePath, file.length(), input)
    }

    fun build(
        folderId: String,
        fileId: String,
        relativePath: String,
        sizeBytes: Long,
        input: InputStream,
    ): BlockManifest {
        require(sizeBytes >= 0) { "File size cannot be negative" }
        val blockSize = adaptiveBlockSize(sizeBytes)
        val wholeDigest = MessageDigest.getInstance("SHA-256")
        val blocks = mutableListOf<FileBlock>()
        val buffer = ByteArray(blockSize)
        var offset = 0L
        var index = 0
        while (true) {
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                if (read > 0) count += read
            }
            if (count == 0) break
            wholeDigest.update(buffer, 0, count)
            val digest = MessageDigest.getInstance("SHA-256").digest(buffer.copyOf(count)).toHex()
            blocks += FileBlock(index++, offset, count, digest)
            offset += count
        }
        if (blocks.isEmpty()) {
            blocks += FileBlock(0, 0, 0, MessageDigest.getInstance("SHA-256").digest().toHex())
        }
        require(offset == sizeBytes) { "File changed size while its block manifest was created" }
        return BlockManifest(
            folderId,
            fileId,
            normalizedRelativePath(relativePath),
            sizeBytes,
            wholeDigest.digest().toHex(),
            blockSize,
            blocks,
        )
    }

    fun adaptiveBlockSize(fileSize: Long): Int {
        var size = MIN_BLOCK_SIZE
        while (size < MAX_BLOCK_SIZE && fileSize / size > TARGET_BLOCK_COUNT) size *= 2
        return size
    }

    private const val MIN_BLOCK_SIZE = 128 * 1024
    private const val MAX_BLOCK_SIZE = 16 * 1024 * 1024
    private const val TARGET_BLOCK_COUNT = 1_000
}

class BlockManifestRepository(private val syncDao: SyncDao) {
    suspend fun store(manifest: BlockManifest) {
        syncDao.deleteOldFileBlocks(manifest.folderId, manifest.fileId, manifest.contentSha256)
        if (manifest.blocks.isNotEmpty()) {
            syncDao.upsertFileBlocks(manifest.blocks.map { block ->
                FileBlockEntity(
                    manifest.folderId,
                    manifest.fileId,
                    manifest.contentSha256,
                    block.index,
                    block.offsetBytes,
                    block.sizeBytes,
                    block.sha256,
                )
            })
        }
    }

    suspend fun load(
        folderId: String,
        fileId: String,
        relativePath: String,
        sizeBytes: Long,
        contentSha256: String,
    ): BlockManifest? {
        val blocks = syncDao.fileBlocks(folderId, fileId, contentSha256)
        if (sizeBytes > 0 && blocks.isEmpty()) return null
        val blockSize = blocks.firstOrNull()?.sizeBytes ?: BlockManifestBuilder.adaptiveBlockSize(sizeBytes)
        return BlockManifest(
            folderId,
            fileId,
            normalizedRelativePath(relativePath),
            sizeBytes,
            contentSha256,
            blockSize,
            blocks.map { FileBlock(it.blockIndex, it.offsetBytes, it.sizeBytes, it.blockSha256) },
        )
    }
}

internal fun normalizedRelativePath(path: String): String {
    val normalized = path.replace('\\', '/').trim('/')
    require(normalized.isNotEmpty() && normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
        "Invalid relative path"
    }
    return normalized
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
