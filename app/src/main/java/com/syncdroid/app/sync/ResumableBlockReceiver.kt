package com.syncdroid.app.sync

import com.syncdroid.app.data.PartialTransferEntity
import com.syncdroid.app.data.SyncDao
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.BitSet

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
        val received = decodeBits(state.receivedBlocksBase64)
        return manifest.blocks.map(FileBlock::index).filterNot(received::get)
    }

    suspend fun acceptBlock(manifest: BlockManifest, blockIndex: Int, data: ByteArray): Boolean {
        val block = requireNotNull(manifest.blocks.getOrNull(blockIndex)) { "Unknown block index" }
        require(data.size == block.sizeBytes) { "Received block has the wrong size" }
        require(MessageDigest.getInstance("SHA-256").digest(data).toHex().equals(block.sha256, true)) {
            "Received block hash does not match its manifest"
        }
        val state = loadOrCreate(manifest)
        RandomAccessFile(state.temporaryPath, "rw").use { file ->
            file.setLength(manifest.sizeBytes)
            file.seek(block.offsetBytes)
            file.write(data)
            file.fd.sync()
        }
        val received = decodeBits(state.receivedBlocksBase64).apply { set(blockIndex) }
        syncDao.upsertPartialTransfer(state.copy(
            receivedBlocksBase64 = encodeBits(received),
            updatedAtMillis = System.currentTimeMillis(),
        ))
        if (manifest.blocks.any { !received.get(it.index) }) return false
        complete(manifest, File(state.temporaryPath))
        return true
    }

    private suspend fun loadOrCreate(manifest: BlockManifest): PartialTransferEntity {
        syncDao.partialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)?.let { existing ->
            if (
                existing.totalSizeBytes == manifest.sizeBytes &&
                existing.blockSizeBytes == manifest.blockSizeBytes &&
                File(existing.temporaryPath).exists()
            ) return existing
            syncDao.deletePartialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)
            File(existing.temporaryPath).takeIf(File::exists)?.delete()
        }
        val transferId = MessageDigest.getInstance("SHA-256").digest(
            "${manifest.folderId}\u0000${manifest.fileId}\u0000${manifest.contentSha256}"
                .toByteArray(StandardCharsets.UTF_8),
        ).toHex()
        val temporary = File(temporaryDirectory, "$transferId.part")
        val state = PartialTransferEntity(
            manifest.folderId,
            manifest.fileId,
            manifest.contentSha256,
            temporary.absolutePath,
            manifest.sizeBytes,
            manifest.blockSizeBytes,
            encodeBits(BitSet()),
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

    private fun encodeBits(bits: BitSet): String = Base64.getEncoder().encodeToString(bits.toByteArray())
    private fun decodeBits(value: String): BitSet = BitSet.valueOf(Base64.getDecoder().decode(value))
}
