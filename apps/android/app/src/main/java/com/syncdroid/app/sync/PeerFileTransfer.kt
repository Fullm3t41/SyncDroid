package com.syncdroid.app.sync

import com.syncdroid.app.data.SyncDao
import com.syncdroid.app.mesh.AuthenticatedPeerConnection
import com.syncdroid.shared.protocol.FileTransferMessage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

object FileTransferWireCodec {
    fun encode(message: FileTransferMessage): ByteArray =
        com.syncdroid.shared.protocol.FileTransferWireCodec.encode(message)

    fun decode(bytes: ByteArray): FileTransferMessage =
        com.syncdroid.shared.protocol.FileTransferWireCodec.decode(bytes)
}

class PeerFileServer(
    private val syncDao: SyncDao,
    private val rootDirectory: File,
    private val onBytesTransferred: (Long) -> Unit = {},
) {
    private val root = rootDirectory.canonicalFile

    suspend fun serveOne(connection: AuthenticatedPeerConnection) {
        serve(connection, FileTransferWireCodec.decode(connection.receive()))
    }

    suspend fun serve(connection: AuthenticatedPeerConnection, request: FileTransferMessage) {
        when (request) {
            is FileTransferMessage.WholeFileRequest -> serveWhole(connection, request)
            is FileTransferMessage.BlockRequest -> serveBlock(connection, request)
            else -> connection.send(FileTransferWireCodec.encode(FileTransferMessage.Error("Expected a file request")))
        }
    }

    private suspend fun serveWhole(
        connection: AuthenticatedPeerConnection,
        request: FileTransferMessage.WholeFileRequest,
    ) {
        val source = validateSource(request.folderId, request.fileId, request.relativePath, request.contentSha256)
            ?: return connection.send(FileTransferWireCodec.encode(FileTransferMessage.Error("Requested version is unavailable")))
        val modifiedAt = syncDao.fileVersion(request.folderId, normalizedRelativePath(request.relativePath))?.modifiedAtMillis
            ?: source.lastModified()
        connection.send(FileTransferWireCodec.encode(FileTransferMessage.FileStart(source.length(), modifiedAt)))
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(source).buffered().use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            var sequence = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                connection.send(FileTransferWireCodec.encode(FileTransferMessage.FileChunk(sequence++, buffer.copyOf(count))))
                onBytesTransferred(count.toLong())
            }
        }
        connection.send(FileTransferWireCodec.encode(FileTransferMessage.FileEnd(digest.digest().toHex())))
    }

    private suspend fun serveBlock(connection: AuthenticatedPeerConnection, request: FileTransferMessage.BlockRequest) {
        val source = validateSource(request.folderId, request.fileId, request.relativePath, request.contentSha256)
            ?: return connection.send(FileTransferWireCodec.encode(FileTransferMessage.Error("Requested version is unavailable")))
        val block = syncDao.fileBlocks(request.folderId, request.fileId, request.contentSha256)
            .firstOrNull { it.blockIndex == request.blockIndex }
            ?: return connection.send(FileTransferWireCodec.encode(FileTransferMessage.Error("Requested block is unavailable")))
        val data = ByteArray(block.sizeBytes)
        RandomAccessFile(source, "r").use { file -> file.seek(block.offsetBytes); file.readFully(data) }
        val actual = MessageDigest.getInstance("SHA-256").digest(data).toHex()
        if (!actual.equals(block.blockSha256, true)) {
            return connection.send(FileTransferWireCodec.encode(FileTransferMessage.Error("Source block changed")))
        }
        connection.send(FileTransferWireCodec.encode(FileTransferMessage.BlockResponse(block.blockIndex, data)))
        onBytesTransferred(data.size.toLong())
    }

    private suspend fun validateSource(folderId: String, fileId: String, relativePath: String, hash: String): File? {
        val normalized = normalizedRelativePath(relativePath)
        val version = syncDao.fileVersion(folderId, normalized) ?: return null
        if (version.fileId != fileId || version.deleted || !version.contentSha256.equals(hash, true)) return null
        val source = File(root, normalized).canonicalFile
        if (!source.toPath().startsWith(root.toPath()) || !source.isFile) return null
        return source
    }

    private companion object { const val CHUNK_SIZE = 64 * 1024 }
}

class WholeFilePeerClient(
    private val receiveDirectory: File,
    private val onBytesTransferred: (Long) -> Unit = {},
) {
    init { require(receiveDirectory.isDirectory || receiveDirectory.mkdirs()) }

    suspend fun fetch(
        connection: AuthenticatedPeerConnection,
        request: FileTransferMessage.WholeFileRequest,
        applier: SyncFileApplier,
    ) {
        connection.send(FileTransferWireCodec.encode(request))
        val start = FileTransferWireCodec.decode(connection.receive())
        if (start is FileTransferMessage.Error) error(start.reason)
        require(start is FileTransferMessage.FileStart) { "Peer did not start the requested file" }
        val temporary = File.createTempFile("syncdroid-whole-", ".part", receiveDirectory)
        try {
            var expectedSequence = 0
            FileOutputStream(temporary).buffered().use { output ->
                while (true) {
                    when (val message = FileTransferWireCodec.decode(connection.receive())) {
                        is FileTransferMessage.FileChunk -> {
                            require(message.sequence == expectedSequence++) { "File chunks arrived out of order" }
                            output.write(message.data)
                            onBytesTransferred(message.data.size.toLong())
                        }
                        is FileTransferMessage.FileEnd -> {
                            output.flush()
                            require(message.contentSha256.equals(request.contentSha256, true)) { "Peer sent a different file version" }
                            break
                        }
                        is FileTransferMessage.Error -> error(message.reason)
                        else -> error("Unexpected file-transfer response")
                    }
                }
            }
            require(temporary.length() == start.sizeBytes) { "Received file size does not match its manifest" }
            FileInputStream(temporary).buffered().use { input ->
                applier.apply(request.relativePath, input, request.contentSha256, start.modifiedAtMillis)
            }
        } finally {
            temporary.delete()
        }
    }
}

class ResumableBlockPeerClient(
    private val receiver: ResumableBlockReceiver,
    private val onBytesTransferred: (Long) -> Unit = {},
) {
    suspend fun fetchMissing(connection: AuthenticatedPeerConnection, manifest: BlockManifest): Boolean {
        val missing = receiver.missingBlocks(manifest)
        if (missing.isEmpty()) return true
        for (index in missing) {
            connection.send(FileTransferWireCodec.encode(FileTransferMessage.BlockRequest(
                manifest.folderId, manifest.fileId, manifest.relativePath, manifest.contentSha256, index,
            )))
            when (val response = FileTransferWireCodec.decode(connection.receive())) {
                is FileTransferMessage.BlockResponse -> {
                    require(response.blockIndex == index) { "Peer returned the wrong block" }
                    onBytesTransferred(response.data.size.toLong())
                    if (receiver.acceptBlock(manifest, index, response.data)) return true
                }
                is FileTransferMessage.Error -> error(response.reason)
                else -> error("Unexpected block-transfer response")
            }
        }
        return false
    }
}
