package com.synctosh.app.mesh

import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.BitSet

data class BlockManifest(
    val folderId: String,
    val fileId: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val contentSha256: String,
    val blockSizeBytes: Int,
    val blocks: List<FileBlock>,
)

data class PartialTransfer(
    val folderId: String,
    val fileId: String,
    val contentSha256: String,
    val temporaryPath: String,
    val totalSizeBytes: Long,
    val blockSizeBytes: Int,
    val receivedBlocksBase64: String,
    val updatedAtMillis: Long,
)

object BlockManifestBuilder {
    fun build(version: FileVersion, source: Path): BlockManifest {
        require(!version.deleted && Files.isRegularFile(source)) { "Cannot build blocks for an unavailable file" }
        val blockSize = adaptiveBlockSize(version.sizeBytes)
        val wholeDigest = MessageDigest.getInstance("SHA-256")
        val blocks = mutableListOf<FileBlock>()
        Files.newInputStream(source).buffered().use { input ->
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
                val blockHash = MessageDigest.getInstance("SHA-256").digest(buffer.copyOf(count)).toHex()
                blocks += FileBlock(index++, offset, count, blockHash)
                offset += count
            }
            require(offset == version.sizeBytes) { "File changed size while its block manifest was created" }
        }
        require(wholeDigest.digest().toHex().equals(version.contentSha256, true)) {
            "File changed while its block manifest was created"
        }
        return BlockManifest(
            version.folderId,
            version.fileId,
            version.relativePath,
            version.sizeBytes,
            version.modifiedAtMillis,
            version.contentSha256.lowercase(),
            blockSize,
            blocks,
        )
    }

    fun adaptiveBlockSize(fileSize: Long): Int {
        var size = MIN_BLOCK_SIZE
        while (size < MAX_BLOCK_SIZE && fileSize / size > TARGET_BLOCK_COUNT) size *= 2
        return size
    }

    const val RESUMABLE_THRESHOLD_BYTES = 1L * 1024 * 1024
    private const val MIN_BLOCK_SIZE = 128 * 1024
    private const val MAX_BLOCK_SIZE = 16 * 1024 * 1024
    private const val TARGET_BLOCK_COUNT = 1_000
}

class ResumableBlockReceiver(
    private val store: MeshStore,
    private val temporaryDirectory: Path,
    private val applier: AtomicFileApplier,
) {
    init { Files.createDirectories(temporaryDirectory) }

    fun missingBlocks(manifest: BlockManifest): List<Int> {
        val state = loadOrCreate(manifest)
        val received = decodeBits(state.receivedBlocksBase64)
        val missing = manifest.blocks.map(FileBlock::index).filterNot(received::get)
        if (missing.isEmpty()) complete(manifest, Path.of(state.temporaryPath))
        return missing
    }

    fun acceptBlock(manifest: BlockManifest, blockIndex: Int, data: ByteArray): Boolean {
        val block = manifest.blocks.firstOrNull { it.index == blockIndex } ?: error("Unknown block index")
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
        store.upsertPartialTransfer(
            state.copy(receivedBlocksBase64 = encodeBits(received), updatedAtMillis = System.currentTimeMillis()),
        )
        if (manifest.blocks.any { !received.get(it.index) }) return false
        complete(manifest, Path.of(state.temporaryPath))
        return true
    }

    private fun loadOrCreate(manifest: BlockManifest): PartialTransfer {
        store.partialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)?.let { existing ->
            val path = runCatching { Path.of(existing.temporaryPath).toAbsolutePath().normalize() }.getOrNull()
            val safe = path != null && path.startsWith(temporaryDirectory.toAbsolutePath().normalize())
            if (safe && existing.totalSizeBytes == manifest.sizeBytes &&
                existing.blockSizeBytes == manifest.blockSizeBytes && Files.isRegularFile(path)
            ) return existing
            store.deletePartialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)
            if (safe) Files.deleteIfExists(path)
        }
        val transferId = MessageDigest.getInstance("SHA-256").digest(
            "${manifest.folderId}\u0000${manifest.fileId}\u0000${manifest.contentSha256}"
                .toByteArray(StandardCharsets.UTF_8),
        ).toHex()
        val temporary = temporaryDirectory.resolve("$transferId.part").toAbsolutePath().normalize()
        require(temporary.startsWith(temporaryDirectory.toAbsolutePath().normalize()))
        RandomAccessFile(temporary.toFile(), "rw").use { it.setLength(manifest.sizeBytes) }
        return PartialTransfer(
            manifest.folderId,
            manifest.fileId,
            manifest.contentSha256,
            temporary.toString(),
            manifest.sizeBytes,
            manifest.blockSizeBytes,
            encodeBits(BitSet()),
            System.currentTimeMillis(),
        ).also(store::upsertPartialTransfer)
    }

    private fun complete(manifest: BlockManifest, temporary: Path) {
        val actual = FileInputStream(temporary.toFile()).buffered().use(::sha256Hex)
        if (!actual.equals(manifest.contentSha256, true)) {
            store.deletePartialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)
            Files.deleteIfExists(temporary)
            error("Completed file hash does not match its manifest")
        }
        FileInputStream(temporary.toFile()).buffered().use { input ->
            applier.apply(manifest.relativePath, input, manifest.contentSha256, manifest.modifiedAtMillis)
        }
        store.deletePartialTransfer(manifest.folderId, manifest.fileId, manifest.contentSha256)
        Files.deleteIfExists(temporary)
    }

    private fun encodeBits(bits: BitSet): String = Base64.getEncoder().encodeToString(bits.toByteArray())
    private fun decodeBits(value: String): BitSet = BitSet.valueOf(Base64.getDecoder().decode(value))
}

class ResumableBlockPeerClient(
    private val receiver: ResumableBlockReceiver,
    private val onBytesTransferred: (Long) -> Unit = {},
) {
    suspend fun fetchMissing(connection: AuthenticatedPeerConnection, manifest: BlockManifest): Boolean {
        val missing = receiver.missingBlocks(manifest)
        if (missing.isEmpty()) return true
        for (index in missing) {
            connection.send(
                FileTransferWireCodec.encode(
                    FileTransferMessage.BlockRequest(
                        manifest.folderId,
                        manifest.fileId,
                        manifest.relativePath,
                        manifest.contentSha256,
                        index,
                    ),
                ),
            )
            when (val response = FileTransferWireCodec.decode(connection.receive())) {
                is FileTransferMessage.BlockResponse -> {
                    require(response.blockIndex == index) { "Peer returned the wrong block" }
                    if (receiver.acceptBlock(manifest, index, response.data)) {
                        onBytesTransferred(response.data.size.toLong())
                        return true
                    }
                    onBytesTransferred(response.data.size.toLong())
                }
                is FileTransferMessage.Error -> error(response.reason)
                else -> error("Unexpected block-transfer response")
            }
        }
        return false
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
