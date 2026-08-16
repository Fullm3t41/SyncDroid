package com.syncdroid.app.mesh

import android.content.Context
import android.net.Uri
import com.syncdroid.app.data.FileVersionEntity
import com.syncdroid.app.data.LocalFolderBindingEntity
import com.syncdroid.app.data.RemoteFileVersionEntity
import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.app.storage.SyncFilterRules
import com.syncdroid.app.sync.AtomicFileApplier
import com.syncdroid.app.sync.BlockManifest
import com.syncdroid.app.sync.BlockManifestBuilder
import com.syncdroid.app.sync.BlockManifestRepository
import com.syncdroid.app.sync.FileSyncAction
import com.syncdroid.app.sync.FileSyncPlan
import com.syncdroid.app.sync.FileHistoryRepository
import com.syncdroid.app.sync.FileTransferWireCodec
import com.syncdroid.app.sync.DocumentTreeFileApplier
import com.syncdroid.app.sync.FolderIndexUpdate
import com.syncdroid.app.sync.IndexAcceptance
import com.syncdroid.app.sync.IndexedFileRecord
import com.syncdroid.app.sync.PeerFileServer
import com.syncdroid.app.sync.RemoteIndexRepository
import com.syncdroid.app.sync.ResumableBlockPeerClient
import com.syncdroid.app.sync.ResumableBlockReceiver
import com.syncdroid.app.sync.SnapshotRepository
import com.syncdroid.app.sync.SyncFileApplier
import com.syncdroid.app.sync.VersionVector
import com.syncdroid.app.sync.WholeFilePeerClient
import com.syncdroid.shared.protocol.FileTransferMessage
import com.syncdroid.shared.protocol.MeshSessionMessage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray

class MeshSyncSession(
    context: Context,
    private val database: SyncDroidDatabase,
    private val identity: AndroidDeviceIdentity,
    private val groupId: String,
    private val groupName: String,
    private val onBytesTransferred: (Long) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val syncDao = database.syncDao()
    private val replication = MeshReplicationRepository(database, identity)
    private val snapshots = SnapshotRepository(database, identity)
    private val remoteIndexes = RemoteIndexRepository(database, identity.deviceId)
    private val blockManifests = BlockManifestRepository(syncDao)
    private val fileHistory = FileHistoryRepository(appContext, database, identity.deviceId)

    suspend fun run(connection: AuthenticatedPeerConnection): MeshSyncResult {
        val remoteDeviceId = connection.peer.deviceId
        require(database.meshDao().getDevice(groupId, remoteDeviceId)?.trustState == "TRUSTED") {
            "The connected device is not a trusted member of this mesh"
        }

        val receiveResult = exchangeMetadata(connection)
        scanConfiguredFolders()

        val localCatalog = buildCatalog(remoteDeviceId)
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.Catalog(localCatalog)))
        val remoteCatalog = connection.receiveSession<MeshSessionMessage.Catalog>().folders

        val updates = buildUpdatesForPeer(remoteCatalog)
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.IndexBatch(updates)))
        val receivedUpdates = connection.receiveSession<MeshSessionMessage.IndexBatch>().updates
        val receivedPlans = receiveIndexes(remoteDeviceId, receivedUpdates)
        val receivedKeys = receivedPlans.mapTo(mutableSetOf()) { it.planKey() }
        val pendingPlans = remoteIndexes.pendingPlans(
            remoteDeviceId,
            syncDao.enabledFolders(groupId).map { it.folderId },
        ).filterNot { it.planKey() in receivedKeys }
        val plans = (receivedPlans + pendingPlans)
            .sortedWith(compareBy({ it.remote.folderId }, { it.remote.remoteSequence }))

        val prepared = prepareDownloads(plans)
        val localRequestCount = prepared.sumOf(PreparedDownload::requestCount)
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.TransferPlan(localRequestCount)))
        val remoteRequestCount = connection.receiveSession<MeshSessionMessage.TransferPlan>().requestCount

        if (identity.deviceId < remoteDeviceId) {
            downloadPhase(connection, remoteDeviceId, prepared)
            connection.send(MeshSessionCodec.encode(MeshSessionMessage.PhaseDone))
            serveRequests(connection, remoteRequestCount)
            connection.receiveSession<MeshSessionMessage.PhaseDone>()
        } else {
            serveRequests(connection, remoteRequestCount)
            connection.receiveSession<MeshSessionMessage.PhaseDone>()
            downloadPhase(connection, remoteDeviceId, prepared)
            connection.send(MeshSessionCodec.encode(MeshSessionMessage.PhaseDone))
        }
        database.meshDao().updateLastSeen(groupId, remoteDeviceId, System.currentTimeMillis())
        return MeshSyncResult(receiveResult.newChatMessages)
    }

    private suspend fun exchangeMetadata(connection: AuthenticatedPeerConnection): MeshReceiveResult {
        val local = MeshWireCodec.encode(replication.export(groupId, groupName))
        connection.send(MeshSessionCodec.encode(MeshSessionMessage.Metadata(local)))
        val remote = connection.receiveSession<MeshSessionMessage.Metadata>()
        return replication.receive(MeshWireCodec.decode(remote.bundle))
    }

    private suspend fun scanConfiguredFolders() {
        val folders = syncDao.enabledFolders(groupId).associateBy { it.folderId }
        syncDao.configuredBindings(identity.deviceId, groupId).forEach { binding ->
            val folder = folders[binding.folderId] ?: return@forEach
            val rules = SyncFilterRules(
                includes = JSONArray(folder.includePatternsJson).strings(),
                excludes = JSONArray(folder.excludePatternsJson).strings(),
            )
            val location = binding.configuredLocationOrNull() ?: return@forEach
            if (location.startsWith("content://", true)) {
                snapshots.scanDocumentTree(appContext, folder.folderId, identity.deviceId, Uri.parse(location), rules)
            } else {
                val root = File(location)
                if (root.isDirectory) snapshots.scanDirectFolder(folder.folderId, identity.deviceId, root, rules)
            }
        }
    }

    private suspend fun buildCatalog(remoteDeviceId: String): List<FolderClock> =
        syncDao.enabledFolders(groupId).mapNotNull { folder ->
            val local = syncDao.folderIndexState(folder.folderId, identity.deviceId) ?: return@mapNotNull null
            val knownPeer = syncDao.folderIndexState(folder.folderId, remoteDeviceId)
            FolderClock(
                folder.folderId,
                local.indexEpoch,
                local.maxSequence,
                knownPeer?.indexEpoch ?: 0,
                knownPeer?.metadataReceivedSequence ?: 0,
                knownPeer?.contentAppliedSequence ?: 0,
            )
        }

    private suspend fun buildUpdatesForPeer(remoteCatalog: List<FolderClock>): List<FolderIndexUpdate> {
        val peerByFolder = remoteCatalog.associateBy(FolderClock::folderId)
        return syncDao.enabledFolders(groupId).mapNotNull { folder ->
            val local = syncDao.folderIndexState(folder.folderId, identity.deviceId) ?: return@mapNotNull null
            val peer = peerByFolder[folder.folderId]
            val full = peer == null || peer.knownPeerIndexEpoch != local.indexEpoch ||
                peer.knownPeerReceivedSequence > local.maxSequence
            val previous = if (full) 0 else peer.knownPeerReceivedSequence
            if (!full && previous == local.maxSequence) return@mapNotNull null
            val versions = (if (full) syncDao.fileVersions(folder.folderId) else {
                syncDao.fileVersionsAfter(folder.folderId, previous, MAX_INDEX_FILES)
            }).sortedBy(FileVersionEntity::localSequence)
            require(versions.size < MAX_INDEX_FILES || versions.last().localSequence == local.maxSequence) {
                "Folder index is too large for one session"
            }
            val binding = syncDao.getBinding(folder.folderId, identity.deviceId)
            FolderIndexUpdate(
                folder.folderId,
                local.indexEpoch,
                previous,
                local.maxSequence,
                full,
                versions.map { it.toIndexedRecord(binding) },
            )
        }
    }

    private suspend fun FileVersionEntity.toIndexedRecord(binding: LocalFolderBindingEntity?): IndexedFileRecord {
        var manifest: BlockManifest? = null
        if (!deleted && sizeBytes >= RESUMABLE_THRESHOLD_BYTES) {
            val root = binding?.directDirectoryOrNull()
            val source = root?.let { File(it, relativePath) }
            if (source?.isFile == true) {
                manifest = blockManifests.load(folderId, fileId, relativePath, sizeBytes, contentSha256)
                if (manifest == null || !manifest.contentSha256.equals(contentSha256, true)) {
                    val built = BlockManifestBuilder.build(folderId, fileId, relativePath, source)
                    if (built.contentSha256.equals(contentSha256, true)) {
                        blockManifests.store(built)
                        manifest = built
                    }
                }
            }
        }
        return IndexedFileRecord(
            relativePath,
            fileId,
            sizeBytes,
            modifiedAtMillis,
            contentSha256,
            previousContentSha256,
            originDeviceId,
            deleted,
            VersionVector.fromJson(versionVectorJson),
            localSequence,
            manifest?.blockSizeBytes ?: 0,
            manifest?.blocks ?: emptyList(),
        )
    }

    private suspend fun receiveIndexes(
        remoteDeviceId: String,
        updates: List<FolderIndexUpdate>,
    ): List<FileSyncPlan> = buildList {
        for (update in updates) {
            require(syncDao.getFolder(update.folderId)?.groupId == groupId) { "Peer sent an index for another mesh" }
            val (acceptance, plans) = remoteIndexes.receive(remoteDeviceId, update)
            require(acceptance !is IndexAcceptance.RequiresFullIndex) { "A full index is required" }
            addAll(plans)
        }
    }.sortedWith(compareBy({ it.remote.folderId }, { it.remote.remoteSequence }))

    private suspend fun prepareDownloads(plans: List<FileSyncPlan>): List<PreparedDownload> {
        return plans.map { plan ->
            if (plan.action != FileSyncAction.DownloadRemote || plan.remote.deleted) {
                PreparedDownload(plan, null, 0)
            } else {
                val binding = syncDao.getBinding(plan.remote.folderId, identity.deviceId)
                val applier = binding?.fileApplierOrNull()
                if (applier == null) {
                    PreparedDownload(plan, null, 0)
                } else if (plan.remoteManifest != null) {
                    val receiver = blockReceiver(applier)
                    PreparedDownload(plan, receiver, receiver.missingBlocks(plan.remoteManifest).size)
                } else {
                    PreparedDownload(plan, null, 1)
                }
            }
        }
    }

    private suspend fun downloadPhase(
        connection: AuthenticatedPeerConnection,
        remoteDeviceId: String,
        downloads: List<PreparedDownload>,
    ) {
        val acknowledgementBlocked = mutableSetOf<String>()
        for (prepared in downloads) {
            val plan = prepared.plan
            val folderId = plan.remote.folderId
            when (plan.action) {
                FileSyncAction.Conflict, FileSyncAction.SendLocal -> acknowledgementBlocked += folderId
                FileSyncAction.Nothing -> if (folderId !in acknowledgementBlocked) {
                    remoteIndexes.acknowledgeRemoteApplied(remoteDeviceId, plan.remote)
                }
                FileSyncAction.DownloadRemote -> {
                    val binding = syncDao.getBinding(folderId, identity.deviceId)
                    val applier = binding?.fileApplierOrNull()
                    if (applier == null) {
                        acknowledgementBlocked += folderId
                        continue
                    }
                    val localBefore = syncDao.fileVersion(folderId, plan.relativePath)
                    if (plan.remote.deleted) {
                        if (localBefore != null && !localBefore.deleted) {
                            fileHistory.deleteWithRecovery(
                                binding = binding,
                                versions = listOf(localBefore),
                                sourceDeviceId = plan.remote.originDeviceId.ifBlank { remoteDeviceId },
                            )
                        } else {
                            applier.delete(plan.relativePath)
                        }
                    } else if (plan.remoteManifest != null && prepared.blockReceiver != null) {
                        if (prepared.requestCount > 0) {
                            check(ResumableBlockPeerClient(
                                prepared.blockReceiver,
                                onBytesTransferred,
                            ).fetchMissing(connection, plan.remoteManifest))
                        }
                        binding.directDirectoryOrNull()?.let {
                            File(it, plan.relativePath).setLastModified(plan.remote.modifiedAtMillis)
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
                    remoteIndexes.markRemoteApplied(remoteDeviceId, plan.remote, folderId !in acknowledgementBlocked)
                    if (!plan.remote.deleted) {
                        fileHistory.recordRemoteApplied(
                            remote = plan.remote,
                            wasNew = localBefore == null || localBefore.deleted,
                        )
                    }
                }
            }
        }
    }

    private suspend fun serveRequests(connection: AuthenticatedPeerConnection, requestCount: Int) {
        repeat(requestCount) {
            val request = FileTransferWireCodec.decode(connection.receive())
            val folderId = when (request) {
                is FileTransferMessage.WholeFileRequest -> request.folderId
                is FileTransferMessage.BlockRequest -> request.folderId
                else -> null
            }
            val binding = folderId?.let { syncDao.getBinding(it, identity.deviceId) }
            val root = binding?.directDirectoryOrNull() ?: binding?.let { materializeSafRequest(it, request) }
            if (root == null) {
                connection.send(FileTransferWireCodec.encode(FileTransferMessage.Error("Folder is not available on this device")))
            } else {
                try {
                    PeerFileServer(syncDao, root, onBytesTransferred).serve(connection, request)
                } finally {
                    if (binding?.directDirectoryOrNull() == null) root.deleteRecursively()
                }
            }
        }
    }

    private fun blockReceiver(applier: SyncFileApplier): ResumableBlockReceiver = ResumableBlockReceiver(
        syncDao,
        transferCache(),
        applier,
    )

    private fun LocalFolderBindingEntity.fileApplierOrNull(): SyncFileApplier? {
        val location = configuredLocationOrNull() ?: return null
        return if (location.startsWith("content://", true)) {
            runCatching { DocumentTreeFileApplier(appContext, Uri.parse(location)) }.getOrNull()
        } else {
            File(location).takeIf(File::isDirectory)?.let(::AtomicFileApplier)
        }
    }

    private fun materializeSafRequest(binding: LocalFolderBindingEntity, request: FileTransferMessage): File? {
        val location = binding.configuredLocationOrNull()?.takeIf { it.startsWith("content://", true) } ?: return null
        val path = when (request) {
            is FileTransferMessage.WholeFileRequest -> request.relativePath
            is FileTransferMessage.BlockRequest -> request.relativePath
            else -> return null
        }
        val source = DocumentTreeFileApplier(appContext, Uri.parse(location)).open(path) ?: return null
        val root = File(transferCache(), "serve-${UUID.randomUUID()}")
        val target = File(root, path).canonicalFile
        require(target.toPath().startsWith(root.canonicalFile.toPath())) { "Requested path escapes the folder" }
        require(target.parentFile?.mkdirs() != false)
        source.use { input -> FileOutputStream(target).use(input::copyTo) }
        return root
    }

    private fun transferCache(): File = File(appContext.cacheDir, "mesh-transfers").apply { mkdirs() }

    private data class PreparedDownload(
        val plan: FileSyncPlan,
        val blockReceiver: ResumableBlockReceiver?,
        val requestCount: Int,
    )

    private companion object {
        const val MAX_INDEX_FILES = 50_000
        const val RESUMABLE_THRESHOLD_BYTES = 1024 * 1024L
    }
}

private fun FileSyncPlan.planKey(): String =
    "${remote.folderId}\u0000${remote.relativePath}\u0000${remote.remoteSequence}"

data class MeshSyncResult(val newChatMessages: List<MeshChatMessage> = emptyList())

private suspend inline fun <reified T : MeshSessionMessage> AuthenticatedPeerConnection.receiveSession(): T {
    return when (val message = MeshSessionCodec.decode(receive())) {
        is MeshSessionMessage.Error -> error(message.reason)
        is T -> message
        else -> error("Unexpected mesh session message")
    }
}

private fun LocalFolderBindingEntity.directDirectoryOrNull(): File? {
    val location = configuredLocationOrNull() ?: return null
    if (location.startsWith("content://", true)) return null
    return File(location).takeIf { it.isDirectory }
}

private fun LocalFolderBindingEntity.configuredLocationOrNull(): String? {
    if (state != LocalFolderBindingState.CONFIGURED.name) return null
    return localLocation?.takeIf(String::isNotBlank)
}

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
