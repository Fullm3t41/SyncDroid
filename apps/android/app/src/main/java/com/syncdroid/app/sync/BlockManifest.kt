package com.syncdroid.app.sync

import com.syncdroid.app.data.FileBlockEntity
import com.syncdroid.app.data.SyncDao
import com.syncdroid.shared.sync.ContentBlockManifestBuilder
import com.syncdroid.shared.sync.normalizeRelativePath
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

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
        val content = ContentBlockManifestBuilder.build(sizeBytes, input)
        return BlockManifest(
            folderId,
            fileId,
            normalizedRelativePath(relativePath),
            sizeBytes,
            content.contentSha256,
            content.blockSizeBytes,
            content.blocks.map { FileBlock(it.index, it.offsetBytes, it.sizeBytes, it.sha256) },
        )
    }

    fun adaptiveBlockSize(fileSize: Long): Int = ContentBlockManifestBuilder.adaptiveBlockSize(fileSize)
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
    return normalizeRelativePath(path)
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
