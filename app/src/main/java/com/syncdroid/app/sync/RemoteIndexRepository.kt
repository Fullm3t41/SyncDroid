package com.syncdroid.app.sync

import androidx.room.withTransaction
import com.syncdroid.app.data.ConflictEntity
import com.syncdroid.app.data.FileVersionEntity
import com.syncdroid.app.data.FolderIndexStateEntity
import com.syncdroid.app.data.RemoteFileVersionEntity
import com.syncdroid.app.data.SyncDroidDatabase
import java.security.SecureRandom
import java.nio.charset.StandardCharsets
import java.util.UUID

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
    val blockSizeBytes: Int = 0,
    val blocks: List<FileBlock> = emptyList(),
)

data class FolderIndexUpdate(
    val folderId: String,
    val indexEpoch: Long,
    val previousSequence: Long,
    val lastSequence: Long,
    val fullIndex: Boolean,
    val files: List<IndexedFileRecord>,
)

enum class FileSyncAction { Nothing, DownloadRemote, SendLocal, Conflict }

data class FileSyncPlan(
    val action: FileSyncAction,
    val relativePath: String,
    val local: FileVersionEntity?,
    val remote: RemoteFileVersionEntity,
    val reason: String,
    val remoteManifest: BlockManifest? = null,
)

fun decideFileSync(local: FileVersionEntity?, remote: RemoteFileVersionEntity): Pair<FileSyncAction, String> {
    if (local == null) return if (remote.deleted) {
        FileSyncAction.Nothing to "Both sides have no live file"
    } else {
        FileSyncAction.DownloadRemote to "File is new on the remote device"
    }
    val localVersion = VersionVector.fromJson(local.versionVectorJson)
    val remoteVersion = VersionVector.fromJson(remote.versionVectorJson)
    return when (localVersion.relationTo(remoteVersion)) {
        CausalRelation.Before -> FileSyncAction.DownloadRemote to "Remote file causally follows local"
        CausalRelation.After -> FileSyncAction.SendLocal to "Local file causally follows remote"
        CausalRelation.Equal -> if (
            local.deleted == remote.deleted && local.contentSha256.equals(remote.contentSha256, true)
        ) {
            FileSyncAction.Nothing to "File versions and content are identical"
        } else {
            FileSyncAction.Conflict to "Equal vectors describe different content"
        }
        CausalRelation.Concurrent -> when {
            remote.previousContentSha256 != null &&
                remote.previousContentSha256.equals(local.contentSha256, true) ->
                FileSyncAction.DownloadRemote to "Remote content proves it descends from local"
            local.previousContentSha256 != null &&
                local.previousContentSha256.equals(remote.contentSha256, true) ->
                FileSyncAction.SendLocal to "Local content proves it descends from remote"
            else -> FileSyncAction.Conflict to "Both devices changed this file independently"
        }
    }
}

class RemoteIndexRepository(
    private val database: SyncDroidDatabase,
    private val currentDeviceId: String,
) {
    private val syncDao = database.syncDao()
    private val indexStates = IndexStateRepository(syncDao)

    suspend fun receive(remoteDeviceId: String, update: FolderIndexUpdate): Pair<IndexAcceptance, List<FileSyncPlan>> {
        validate(update)
        lateinit var acceptance: IndexAcceptance
        database.withTransaction {
            acceptance = indexStates.receiveMetadata(
                update.folderId,
                remoteDeviceId,
                update.indexEpoch,
                update.previousSequence,
                update.lastSequence,
                update.fullIndex,
            )
            if (acceptance is IndexAcceptance.Accepted) {
                if (update.fullIndex) syncDao.deleteRemoteFileVersions(update.folderId, remoteDeviceId)
                val remoteFiles = update.files.map { it.toEntity(update.folderId, remoteDeviceId) }
                if (remoteFiles.isNotEmpty()) syncDao.upsertRemoteFileVersions(remoteFiles)
            }
        }
        if (acceptance is IndexAcceptance.RequiresFullIndex) return acceptance to emptyList()

        val remoteFiles = update.files.map { it.toEntity(update.folderId, remoteDeviceId) }
        val localByPath = syncDao.fileVersions(update.folderId).associateBy(FileVersionEntity::relativePath)
        val exceptions = syncDao.activeExceptions(update.folderId).mapTo(mutableSetOf()) { it.relativePath }
        val recordsByPath = update.files.associateBy { normalizedRelativePath(it.relativePath) }
        val plans = remoteFiles.map { remote ->
            val local = localByPath[remote.relativePath]
            val pendingResolution = syncDao.pendingRemoteResolution(update.folderId, remote.relativePath)
            val (action, reason) = if (pendingResolution?.matches(remote) == true) {
                FileSyncAction.DownloadRemote to if (pendingResolution.state == ConflictState.KeepBoth.name) {
                    "User kept both versions; restore the selected remote version at the original path"
                } else {
                    "User selected the remote version"
                }
            } else if (pendingResolution != null) {
                FileSyncAction.Nothing to "Waiting for the version selected by the user"
            } else if (remote.relativePath in exceptions) {
                FileSyncAction.Nothing to "This device has an active overwrite-only exception"
            } else {
                decideFileSync(local, remote)
            }
            if (action == FileSyncAction.Conflict) recordConflict(update.folderId, local, remote)
            val record = recordsByPath[remote.relativePath]
            val manifest = record?.takeIf { !it.deleted && it.blocks.isNotEmpty() }?.let {
                BlockManifest(
                    update.folderId,
                    it.fileId,
                    it.relativePath,
                    it.sizeBytes,
                    it.contentSha256,
                    it.blockSizeBytes,
                    it.blocks,
                )
            }
            FileSyncPlan(action, remote.relativePath, local, remote, reason, manifest)
        }
        return acceptance to plans
    }

    /**
     * Rebuilds work for metadata that was received but whose content was not applied.
     * This is what makes a transfer survive a disconnected or killed mesh session.
     * Block manifests are carried by the live update; older pending records safely
     * fall back to the hash-verified whole-file protocol.
     */
    suspend fun pendingPlans(remoteDeviceId: String, folderIds: Collection<String>): List<FileSyncPlan> = buildList {
        for (folderId in folderIds) {
            val state = syncDao.folderIndexState(folderId, remoteDeviceId) ?: continue
            if (state.contentAppliedSequence >= state.metadataReceivedSequence) continue
            val localByPath = syncDao.fileVersions(folderId).associateBy(FileVersionEntity::relativePath)
            val exceptions = syncDao.activeExceptions(folderId).mapTo(mutableSetOf()) { it.relativePath }
            val pending = syncDao.remoteFileVersions(folderId, remoteDeviceId)
                .filter { it.remoteSequence > state.contentAppliedSequence }
                .sortedBy(RemoteFileVersionEntity::remoteSequence)
            for (remote in pending) {
                val local = localByPath[remote.relativePath]
                val pendingResolution = syncDao.pendingRemoteResolution(folderId, remote.relativePath)
                val (action, reason) = if (pendingResolution?.matches(remote) == true) {
                    FileSyncAction.DownloadRemote to if (pendingResolution.state == ConflictState.KeepBoth.name) {
                        "User kept both versions; restore the selected remote version at the original path"
                    } else {
                        "User selected the remote version"
                    }
                } else if (pendingResolution != null) {
                    FileSyncAction.Nothing to "Waiting for the version selected by the user"
                } else if (remote.relativePath in exceptions) {
                    FileSyncAction.Nothing to "This device has an active overwrite-only exception"
                } else {
                    decideFileSync(local, remote)
                }
                if (action == FileSyncAction.Conflict) recordConflict(folderId, local, remote)
                add(FileSyncPlan(action, remote.relativePath, local, remote, reason))
            }
        }
    }

    suspend fun markRemoteApplied(
        remoteDeviceId: String,
        remote: RemoteFileVersionEntity,
        acknowledgeRemoteSequence: Boolean = true,
    ) {
        database.withTransaction {
            val previousLocal = syncDao.fileVersion(remote.folderId, remote.relativePath)
            val pendingResolution = syncDao.pendingRemoteResolution(remote.folderId, remote.relativePath)
                ?.takeIf { it.matches(remote) }
            val localState = syncDao.folderIndexState(remote.folderId, currentDeviceId) ?: FolderIndexStateEntity(
                remote.folderId,
                currentDeviceId,
                randomEpoch(),
                0,
                0,
                0,
                System.currentTimeMillis(),
            )
            val nextSequence = localState.maxSequence + 1
            val appliedVector = if (pendingResolution != null && previousLocal != null) {
                VersionVector.fromJson(previousLocal.versionVectorJson)
                    .merge(VersionVector.fromJson(remote.versionVectorJson))
                    .increment(currentDeviceId)
                    .toJson()
            } else {
                remote.versionVectorJson
            }
            syncDao.upsertFileVersion(
                FileVersionEntity(
                    remote.folderId,
                    remote.relativePath,
                    remote.fileId,
                    remote.sizeBytes,
                    remote.modifiedAtMillis,
                    remote.contentSha256,
                    remote.previousContentSha256,
                    remote.deleted,
                    appliedVector,
                    remote.originDeviceId.ifBlank { remoteDeviceId },
                    nextSequence,
                ),
            )
            syncDao.upsertFolderIndexState(localState.copy(
                maxSequence = nextSequence,
                metadataReceivedSequence = nextSequence,
                contentAppliedSequence = nextSequence,
                updatedAtMillis = System.currentTimeMillis(),
            ))
            if (acknowledgeRemoteSequence) acknowledgeRemoteApplied(remoteDeviceId, remote)
            syncDao.completeRemoteResolution(remote.folderId, remote.relativePath, System.currentTimeMillis())
        }
    }

    suspend fun acknowledgeRemoteApplied(remoteDeviceId: String, remote: RemoteFileVersionEntity) {
        val remoteState = requireNotNull(syncDao.folderIndexState(remote.folderId, remoteDeviceId))
        indexStates.acknowledgeApplied(remote.folderId, remoteDeviceId, remoteState.indexEpoch, remote.remoteSequence)
    }

    private suspend fun recordConflict(folderId: String, local: FileVersionEntity?, remote: RemoteFileVersionEntity) {
        val conflictKey = "$folderId\u0000${remote.relativePath}\u0000${local?.contentSha256.orEmpty()}\u0000${remote.deviceId}\u0000${remote.contentSha256}"
        syncDao.upsertConflict(
            ConflictEntity(
                conflictId = UUID.nameUUIDFromBytes(conflictKey.toByteArray(StandardCharsets.UTF_8)).toString(),
                folderId = folderId,
                relativePath = remote.relativePath,
                leftSnapshotId = local?.let { "local:${it.fileId}:${it.contentSha256}" } ?: "local:missing",
                rightSnapshotId = "remote:${remote.deviceId}:${remote.fileId}:${remote.contentSha256}",
                state = ConflictState.Unresolved.name,
                createdAtMillis = System.currentTimeMillis(),
                resolvedAtMillis = null,
                renamedRelativePath = null,
            ),
        )
    }

    private fun validate(update: FolderIndexUpdate) {
        require(update.indexEpoch != 0L && update.previousSequence >= 0 && update.lastSequence >= update.previousSequence)
        var sequence = update.previousSequence
        val paths = mutableSetOf<String>()
        update.files.forEach { file ->
            require(file.sequence > sequence && file.sequence <= update.lastSequence) { "Index sequences are not increasing" }
            sequence = file.sequence
            require(paths.add(normalizedRelativePath(file.relativePath))) { "Index contains a duplicate path" }
            require(file.fileId.isNotBlank())
            require(file.originDeviceId.isNotBlank())
            require(file.sizeBytes >= 0 && file.modifiedAtMillis >= 0)
            require(file.deleted || file.contentSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid content hash" }
            require(file.previousContentSha256 == null || file.previousContentSha256.matches(Regex("[a-fA-F0-9]{64}")))
            var expectedOffset = 0L
            file.blocks.forEachIndexed { index, block ->
                require(block.index == index && block.offsetBytes == expectedOffset && block.sizeBytes >= 0)
                require(block.sha256.matches(Regex("[a-fA-F0-9]{64}")))
                expectedOffset += block.sizeBytes
            }
            if (file.blocks.isNotEmpty()) require(file.blockSizeBytes > 0)
            if (!file.deleted && file.blocks.isNotEmpty()) require(expectedOffset == file.sizeBytes)
        }
        if (update.files.isNotEmpty()) require(sequence == update.lastSequence) { "Index last sequence does not match its records" }
    }
}

private fun IndexedFileRecord.toEntity(folderId: String, deviceId: String) = RemoteFileVersionEntity(
    folderId,
    deviceId,
    normalizedRelativePath(relativePath),
    fileId,
    sizeBytes,
    modifiedAtMillis,
    contentSha256.lowercase(),
    previousContentSha256?.lowercase(),
    originDeviceId,
    deleted,
    version.toJson(),
    sequence,
)

private fun randomEpoch(): Long = (SecureRandom().nextLong() and Long.MAX_VALUE).coerceAtLeast(1)
