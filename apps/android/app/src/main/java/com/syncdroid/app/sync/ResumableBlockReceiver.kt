package com.syncdroid.app.sync

import com.syncdroid.app.data.PartialTransferEntity
import com.syncdroid.app.data.SyncDao
import com.syncdroid.shared.sync.ResumableTransferProgress
import com.syncdroid.shared.sync.isCompatiblePartialTransfer
import com.syncdroid.shared.sync.resumableTransferId
import com.syncdroid.shared.sync.validateReceivedBlock
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

class ResumableBlockReceiver(
    private val syncDao: SyncDao,
    private val temporaryDirectory: File,
    private val applier: SyncFileApplier,
) {
    init {
        require(temporaryDirectory.isDirectory || temporaryDirectory.mkdirs()) { "Could not create transfer cache" }
    }

    suspend fun missingBlocks(manifest: BlockManifest): List<Int> {
        val state = loadOrCreate(manifest)
        val progress = ResumableTransferProgress(state.receivedBlocksBase64)
        val missing = progress.missingBlocks(manifest.blocks)
        if (missing.isEmpty()) complete(manifest, File(state.temporaryPath))
        return missing
    }

    suspend fun acceptBlock(manifest: BlockManifest, blockIndex: Int, data: ByteArray): Boolean {
        val block = manifest.blocks.firstOrNull { it.index == blockIndex } ?: error("Unknown block index")
        validateReceivedBlock(block, blockIndex, data)
        val state = loadOrCreate(manifest)
        RandomAccessFile(state.temporaryPath, "rw").use { file ->
            file.setLength(manifest.sizeBytes)
            file.seek(block.offsetBytes)
            file.write(data)
            file.fd.sync()
        }
        val progress = ResumableTransferProgress(state.receivedBlocksBase64).record(blockIndex)
        syncDao.upsertPartialTransfer(state.copy(
            receivedBlocksBase64 = progress.receivedBlocksBase64,
            updatedAtMillis = System.currentTimeMillis(),
        ))
        if (!progress.isComplete(manifest.blocks)) return false
        complete(manifest, File(state.temporaryPath))
        return true
    }

    private suspend fun loadOrCreate(manifest: BlockManifest): PartialTransferEntity {
        syncDao.partialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)?.let { existing ->
            if (
                isCompatiblePartialTransfer(
                    manifest.sizeBytes, manifest.blockSizeBytes, existing.totalSizeBytes, existing.blockSizeBytes,
                ) &&
                File(existing.temporaryPath).exists()
            ) return existing
            syncDao.deletePartialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)
            File(existing.temporaryPath).takeIf(File::exists)?.delete()
        }
        val transferId = resumableTransferId(manifest.folderId, manifest.fileId, manifest.contentSha256)
        val temporary = File(temporaryDirectory, "$transferId.part")
        val state = PartialTransferEntity(
            manifest.folderId,
            manifest.fileId,
            manifest.contentSha256,
            temporary.absolutePath,
            manifest.sizeBytes,
            manifest.blockSizeBytes,
            ResumableTransferProgress().receivedBlocksBase64,
            System.currentTimeMillis(),
        )
        syncDao.upsertPartialTransfer(state)
        return state
    }

    private suspend fun complete(manifest: BlockManifest, temporary: File) {
        val actual = FileInputStream(temporary).buffered().use(FileHasher::sha256)
        require(actual.equals(manifest.contentSha256, true)) { "Completed file hash does not match its manifest" }
        FileInputStream(temporary).buffered().use { input ->
            applier.apply(manifest.relativePath, input, manifest.contentSha256)
        }
        syncDao.deletePartialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)
        temporary.delete()
    }
}
