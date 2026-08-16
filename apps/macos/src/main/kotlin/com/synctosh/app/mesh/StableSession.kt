package com.synctosh.app.mesh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.security.Signature
import java.util.Base64

data class StablePeerProof(
    val groupId: String,
    val deviceId: String,
    val publicKeyBase64: String,
    val tlsPublicKeyBase64: String,
    val nonceBase64: String,
    val signatureBase64: String,
) {
    fun payload(): ByteArray = canonicalBytes {
        string("syncdroid-tls-identity-proof-v1")
        string(groupId); string(deviceId); string(publicKeyBase64); string(tlsPublicKeyBase64); string(nonceBase64)
    }

    fun isValid(): Boolean = runCatching {
        val key = decodePublicKey(publicKeyBase64)
        deviceIdFor(key) == deviceId && Signature.getInstance("SHA256withECDSA").run {
            initVerify(key); update(payload()); verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)
}

class StablePeerAuthenticator(
    private val store: MeshStore,
    private val identity: MacDeviceIdentity,
    private val groupId: String,
) {
    suspend fun authenticate(connection: AuthenticatedPeerConnection): String {
        connection.send(StablePeerProofCodec.encode(createProof()))
        val remote = StablePeerProofCodec.decode(connection.receive())
        require(remote.groupId == groupId && remote.isValid()) { "Peer identity proof is invalid" }
        val tlsKey = Base64.getEncoder().encodeToString(connection.peerTlsIdentity.publicKeySpki)
        require(remote.tlsPublicKeyBase64 == tlsKey) { "Peer proof is not bound to this TLS connection" }
        val member = store.devices(groupId).firstOrNull { it.deviceId == remote.deviceId }
            ?: error("Peer is not a member of this mesh")
        require(member.trusted && member.identityPublicKeyBase64 == remote.publicKeyBase64) { "Peer is not trusted" }
        if (member.tlsPublicKeyBase64 == null) {
            store.recordTlsKey(groupId, remote.deviceId, connection.peerTlsIdentity.publicKeySpki)
        } else {
            require(member.tlsPublicKeyBase64 == remote.tlsPublicKeyBase64) { "Pinned peer TLS key changed" }
        }
        return remote.deviceId
    }

    private fun createProof(): StablePeerProof {
        val publicKey = Base64.getEncoder().encodeToString(identity.publicKey.encoded)
        val unsigned = StablePeerProof(
            groupId,
            identity.deviceId,
            publicKey,
            publicKey,
            ByteArray(32).also(SecureRandom()::nextBytes).let { Base64.getEncoder().encodeToString(it) },
            "",
        )
        return unsigned.copy(signatureBase64 = Base64.getEncoder().encodeToString(identity.sign(unsigned.payload())))
    }
}

object StablePeerProofCodec {
    fun encode(value: StablePeerProof): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            listOf(value.groupId, value.deviceId, value.publicKeyBase64, value.tlsPublicKeyBase64, value.nonceBase64, value.signatureBase64)
                .forEach { output.writeString(it) }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StablePeerProof = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC)
        StablePeerProof(input.readString(), input.readString(), input.readString(), input.readString(), input.readString(), input.readString())
            .also { require(input.available() == 0) }
    }

    private fun DataOutputStream.writeString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8); require(encoded.size <= MAX_FIELD); writeInt(encoded.size); write(encoded)
    }
    private fun DataInputStream.readString(): String {
        val size = readInt().also { require(it in 0..MAX_FIELD) }
        return String(ByteArray(size).also(::readFully), Charsets.UTF_8)
    }
    private const val MAGIC = 0x53445049
    private const val MAX_FIELD = 16 * 1024
}

data class FolderClock(
    val folderId: String,
    val localIndexEpoch: Long,
    val localMaxSequence: Long,
    val knownPeerIndexEpoch: Long,
    val knownPeerReceivedSequence: Long,
    val knownPeerAppliedSequence: Long,
)

data class FileBlock(val index: Int, val offsetBytes: Long, val sizeBytes: Int, val sha256: String)

data class IndexedFileRecord(
    val relativePath: String,
    val fileId: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val contentSha256: String,
    val previousContentSha256: String?,
    val originDeviceId: String,
    val deleted: Boolean,
    val version: VersionVector,
    val sequence: Long,
    val blockSizeBytes: Int,
    val blocks: List<FileBlock>,
)

data class FolderIndexUpdate(
    val folderId: String,
    val indexEpoch: Long,
    val previousSequence: Long,
    val lastSequence: Long,
    val fullIndex: Boolean,
    val files: List<IndexedFileRecord>,
)

sealed interface MeshSessionMessage {
    data class Metadata(val bundle: ByteArray) : MeshSessionMessage
    data class Catalog(val folders: List<FolderClock>) : MeshSessionMessage
    data class IndexBatch(val updates: List<FolderIndexUpdate>) : MeshSessionMessage
    data class TransferPlan(val requestCount: Int) : MeshSessionMessage
    data object PhaseDone : MeshSessionMessage
    data class Error(val reason: String) : MeshSessionMessage
}

object MeshSessionCodec {
    fun encode(message: MeshSessionMessage): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC); output.writeShort(VERSION)
            when (message) {
                is MeshSessionMessage.Metadata -> { output.writeByte(METADATA); output.writeData(message.bundle) }
                is MeshSessionMessage.Catalog -> {
                    output.writeByte(CATALOG); output.writeCount(message.folders.size)
                    message.folders.forEach {
                        output.writeString(it.folderId); output.writeLong(it.localIndexEpoch); output.writeLong(it.localMaxSequence)
                        output.writeLong(it.knownPeerIndexEpoch); output.writeLong(it.knownPeerReceivedSequence); output.writeLong(it.knownPeerAppliedSequence)
                    }
                }
                is MeshSessionMessage.IndexBatch -> {
                    output.writeByte(INDEX_BATCH); output.writeCount(message.updates.size); message.updates.forEach { output.writeUpdate(it) }
                }
                is MeshSessionMessage.TransferPlan -> { require(message.requestCount in 0..MAX_REQUESTS); output.writeByte(TRANSFER_PLAN); output.writeInt(message.requestCount) }
                MeshSessionMessage.PhaseDone -> output.writeByte(PHASE_DONE)
                is MeshSessionMessage.Error -> { output.writeByte(ERROR); output.writeString(message.reason) }
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): MeshSessionMessage = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC && input.readUnsignedShort() == VERSION)
        val value = when (input.readUnsignedByte()) {
            METADATA -> MeshSessionMessage.Metadata(input.readData())
            CATALOG -> MeshSessionMessage.Catalog(List(input.readCount()) {
                FolderClock(input.readString(), input.readLong(), input.readLong(), input.readLong(), input.readLong(), input.readLong())
            })
            INDEX_BATCH -> MeshSessionMessage.IndexBatch(List(input.readCount()) { input.readUpdate() })
            TRANSFER_PLAN -> MeshSessionMessage.TransferPlan(input.readInt().also { require(it in 0..MAX_REQUESTS) })
            PHASE_DONE -> MeshSessionMessage.PhaseDone
            ERROR -> MeshSessionMessage.Error(input.readString())
            else -> error("Unknown mesh session message")
        }
        require(input.available() == 0); value
    }

    private fun DataOutputStream.writeUpdate(update: FolderIndexUpdate) {
        writeString(update.folderId); writeLong(update.indexEpoch); writeLong(update.previousSequence); writeLong(update.lastSequence)
        writeBoolean(update.fullIndex); writeCount(update.files.size)
        update.files.forEach { file ->
            writeString(file.relativePath); writeString(file.fileId); writeLong(file.sizeBytes); writeLong(file.modifiedAtMillis)
            writeString(file.contentSha256); writeNullableString(file.previousContentSha256); writeString(file.originDeviceId)
            writeBoolean(file.deleted); writeString(file.version.toJson()); writeLong(file.sequence); writeInt(file.blockSizeBytes)
            writeCount(file.blocks.size); file.blocks.forEach { block ->
                writeInt(block.index); writeLong(block.offsetBytes); writeInt(block.sizeBytes); writeString(block.sha256)
            }
        }
    }

    private fun DataInputStream.readUpdate(): FolderIndexUpdate {
        val folderId = readString(); val epoch = readLong(); val previous = readLong(); val last = readLong(); val full = readBoolean()
        return FolderIndexUpdate(folderId, epoch, previous, last, full, List(readCount()) {
            IndexedFileRecord(
                readString(), readString(), readLong(), readLong(), readString(), readNullableString(), readString(), readBoolean(),
                VersionVector.fromJson(readString()), readLong(), readInt(),
                List(readCount()) { FileBlock(readInt(), readLong(), readInt(), readString()) },
            )
        })
    }

    private fun DataOutputStream.writeString(value: String) = writeData(value.toByteArray(Charsets.UTF_8))
    private fun DataInputStream.readString() = String(readData(), Charsets.UTF_8)
    private fun DataOutputStream.writeNullableString(value: String?) { writeBoolean(value != null); if (value != null) writeString(value) }
    private fun DataInputStream.readNullableString() = if (readBoolean()) readString() else null
    private fun DataOutputStream.writeData(value: ByteArray) { require(value.size <= MAX_FIELD); writeInt(value.size); write(value) }
    private fun DataInputStream.readData() = ByteArray(readInt().also { require(it in 0..MAX_FIELD) }).also(::readFully)
    private fun DataOutputStream.writeCount(value: Int) { require(value in 0..MAX_ITEMS); writeInt(value) }
    private fun DataInputStream.readCount() = readInt().also { require(it in 0..MAX_ITEMS) }

    private const val MAGIC = 0x53444D53
    private const val VERSION = 2
    private const val METADATA = 1
    private const val CATALOG = 2
    private const val INDEX_BATCH = 3
    private const val TRANSFER_PLAN = 4
    private const val PHASE_DONE = 5
    private const val ERROR = 127
    private const val MAX_ITEMS = 50_000
    private const val MAX_REQUESTS = 1_000_000
    private const val MAX_FIELD = 16 * 1024 * 1024
}

class MeshFileSyncSession(
    private val store: MeshStore,
    private val identity: MacDeviceIdentity,
    private val profile: MeshProfile,
    private val onBytesTransferred: (Long) -> Unit = {},
) {
    private val history = FileHistoryRepository(store, identity.deviceId)

    suspend fun run(connection: AuthenticatedPeerConnection, remoteDeviceId: String) {
        history.cleanupExpired()
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.Metadata(MeshWireCodec.encode(store.exportBundle()))))
        val remoteMetadata = connection.receiveSession<MeshSessionMessage.Metadata>()
        store.importBundle(MeshWireCodec.decode(remoteMetadata.bundle))

        val engine = FileSyncEngine(store, identity, profile)
        engine.scanConfiguredFolders()
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.Catalog(engine.buildCatalog(remoteDeviceId))))
        val remoteCatalog = connection.receiveSession<MeshSessionMessage.Catalog>().folders
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.IndexBatch(engine.buildUpdatesForPeer(remoteCatalog))))
        val remoteUpdates = connection.receiveSession<MeshSessionMessage.IndexBatch>().updates
        val plans = engine.receiveIndexes(remoteDeviceId, remoteUpdates)
        val prepared = plans.map { plan ->
            val root = engine.configuredRoot(plan.remote.folderId)
            val manifest = plan.remoteManifest.takeIf {
                plan.action == FileSyncAction.DownloadRemote && !plan.remote.deleted && root != null
            }
            val requestCount = when {
                plan.action != FileSyncAction.DownloadRemote || plan.remote.deleted || root == null -> 0
                manifest != null -> ResumableBlockReceiver(store, transferCache(), AtomicFileApplier(root))
                    .missingBlocks(manifest).size
                else -> 1
            }
            PreparedDownload(plan, manifest, requestCount)
        }
        val localRequests = prepared.sumOf(PreparedDownload::requestCount)
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.TransferPlan(localRequests)))
        val remoteRequests = connection.receiveSession<MeshSessionMessage.TransferPlan>().requestCount

        if (identity.deviceId < remoteDeviceId) {
            downloadPhase(connection, remoteDeviceId, prepared, engine)
            connection.send(MeshSessionCodec.encode(MeshSessionMessage.PhaseDone))
            serveRequests(connection, remoteRequests, engine)
            connection.receiveSession<MeshSessionMessage.PhaseDone>()
        } else {
            serveRequests(connection, remoteRequests, engine)
            connection.receiveSession<MeshSessionMessage.PhaseDone>()
            downloadPhase(connection, remoteDeviceId, prepared, engine)
            connection.send(MeshSessionCodec.encode(MeshSessionMessage.PhaseDone))
        }
        store.markSeen(profile.groupId, remoteDeviceId)
    }

    private suspend fun downloadPhase(
        connection: AuthenticatedPeerConnection,
        remoteDeviceId: String,
        downloads: List<PreparedDownload>,
        engine: FileSyncEngine,
    ) {
        val acknowledgementBlocked = mutableSetOf<String>()
        downloads.forEach { prepared ->
            val plan = prepared.plan
            val folderId = plan.remote.folderId
            when (plan.action) {
                FileSyncAction.Conflict, FileSyncAction.SendLocal -> acknowledgementBlocked += folderId
                FileSyncAction.Nothing -> if (folderId !in acknowledgementBlocked) {
                    store.acknowledgeRemoteApplied(folderId, remoteDeviceId, plan.remote.remoteSequence)
                }
                FileSyncAction.DownloadRemote -> {
                    val root = engine.configuredRoot(folderId)
                    if (root == null) {
                        acknowledgementBlocked += folderId
                        return@forEach
                    }
                    val applier = AtomicFileApplier(root)
                    val localBefore = store.fileVersion(folderId, plan.relativePath)
                    if (plan.remote.deleted) {
                        if (localBefore != null && !localBefore.deleted) {
                            history.deleteWithRecovery(
                                root,
                                localBefore,
                                plan.remote.originDeviceId.ifBlank { remoteDeviceId },
                            )
                        } else {
                            applier.delete(plan.relativePath)
                        }
                    } else if (prepared.manifest != null) {
                        if (prepared.requestCount > 0) {
                            val completed = ResumableBlockPeerClient(
                                ResumableBlockReceiver(store, transferCache(), applier),
                                onBytesTransferred,
                            ).fetchMissing(connection, prepared.manifest)
                            require(completed) { "Resumable transfer did not receive every block" }
                        }
                    } else {
                        WholeFilePeerClient(transferCache(), onBytesTransferred).fetch(
                            connection,
                            FileTransferMessage.WholeFileRequest(
                                plan.remote.folderId,
                                plan.remote.fileId,
                                plan.remote.relativePath,
                                plan.remote.contentSha256,
                            ),
                            applier,
                        )
                    }
                    engine.markRemoteApplied(remoteDeviceId, plan.remote, folderId !in acknowledgementBlocked)
                    if (!plan.remote.deleted) history.recordSynced(plan.remote)
                }
            }
        }
    }

    private suspend fun serveRequests(
        connection: AuthenticatedPeerConnection,
        requestCount: Int,
        engine: FileSyncEngine,
    ) {
        repeat(requestCount) {
            val request = FileTransferWireCodec.decode(connection.receive())
            val folderId = when (request) {
                is FileTransferMessage.WholeFileRequest -> request.folderId
                is FileTransferMessage.BlockRequest -> request.folderId
                else -> null
            }
            val root = folderId?.let(engine::configuredRoot)
            if (root == null) {
                connection.send(FileTransferWireCodec.encode(FileTransferMessage.Error("Folder is not available on this Mac")))
            } else {
                PeerFileServer(store, root, onBytesTransferred).serve(connection, request)
            }
        }
    }

    private fun transferCache(): java.nio.file.Path = java.nio.file.Path.of(
        System.getProperty("user.home"), "Library", "Application Support", "SyncTosh", "transfers",
    )

    private data class PreparedDownload(
        val plan: FileSyncPlan,
        val manifest: BlockManifest?,
        val requestCount: Int,
    )
}

class MetadataOnlyMeshSession(
    private val store: MeshStore,
    private val identity: MacDeviceIdentity,
    private val profile: MeshProfile,
) {
    suspend fun run(connection: AuthenticatedPeerConnection, remoteDeviceId: String) =
        MeshFileSyncSession(store, identity, profile).run(connection, remoteDeviceId)
}

private suspend inline fun <reified T : MeshSessionMessage> AuthenticatedPeerConnection.receiveSession(): T {
    return when (val value = MeshSessionCodec.decode(receive())) {
        is MeshSessionMessage.Error -> error(value.reason)
        is T -> value
        else -> error("Unexpected mesh session message")
    }
}
