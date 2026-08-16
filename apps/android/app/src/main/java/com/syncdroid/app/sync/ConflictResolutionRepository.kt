package com.syncdroid.app.sync

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.syncdroid.app.data.ConflictEntity
import com.syncdroid.app.data.FileVersionEntity
import com.syncdroid.app.data.RemoteFileVersionEntity
import com.syncdroid.app.data.SyncDroidDatabase
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ConflictVersionDetails(
    val conflict: ConflictEntity,
    val local: FileVersionEntity,
    val remote: RemoteFileVersionEntity,
    val suggestedRenamedPath: String,
)

class ConflictResolutionRepository(
    context: Context,
    private val database: SyncDroidDatabase,
    private val currentDeviceId: String,
) {
    private val appContext = context.applicationContext
    private val syncDao = database.syncDao()

    suspend fun details(conflict: ConflictEntity): ConflictVersionDetails? = withContext(Dispatchers.IO) {
        val path = conflict.relativePath ?: return@withContext null
        val local = syncDao.fileVersion(conflict.folderId, path) ?: return@withContext null
        val remoteRef = conflict.remoteReference() ?: return@withContext null
        val remote = syncDao.remoteFileVersion(conflict.folderId, remoteRef.deviceId, path)
            ?.takeIf { it.fileId == remoteRef.fileId && it.contentSha256.equals(remoteRef.sha256, true) }
            ?: return@withContext null
        val occupied = syncDao.fileVersions(conflict.folderId).mapTo(mutableSetOf()) { it.relativePath }
        syncDao.allRemoteFileVersions(conflict.folderId).mapTo(occupied) { it.relativePath }
        val location = syncDao.getBinding(conflict.folderId, currentDeviceId)?.localLocation
        val suggested = if (location == null) nextConflictCopyPath(path, occupied)
        else findAvailablePhysicalPath(location, path, occupied)
        ConflictVersionDetails(conflict, local, remote, suggested)
    }

    suspend fun keepLocal(details: ConflictVersionDetails) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val state = requireNotNull(syncDao.folderIndexState(details.conflict.folderId, currentDeviceId))
            val nextSequence = state.maxSequence + 1
            val winningVector = VersionVector.fromJson(details.local.versionVectorJson)
                .merge(VersionVector.fromJson(details.remote.versionVectorJson))
                .increment(currentDeviceId)
            syncDao.upsertFileVersion(details.local.copy(
                versionVectorJson = winningVector.toJson(),
                localSequence = nextSequence,
            ))
            syncDao.upsertFolderIndexState(state.copy(
                maxSequence = nextSequence,
                metadataReceivedSequence = nextSequence,
                contentAppliedSequence = nextSequence,
                updatedAtMillis = now,
            ))
            syncDao.resolveDuplicateConflicts(details.conflict.folderId, details.local.relativePath, now)
            syncDao.updateConflictResolution(
                details.conflict.conflictId,
                ConflictState.KeepLeft.name,
                now,
                null,
            )
        }
    }

    suspend fun keepRemote(details: ConflictVersionDetails) = withContext(Dispatchers.IO) {
        database.withTransaction {
            syncDao.resolveDuplicateConflicts(
                details.conflict.folderId,
                details.local.relativePath,
                System.currentTimeMillis(),
            )
            syncDao.updateConflictResolution(
                details.conflict.conflictId,
                ConflictState.KeepRight.name,
                null,
                null,
            )
        }
    }

    /**
     * Copies this device's version to the next free suffixed name. The remote version is then
     * downloaded into the original path when its source device is next available.
     */
    suspend fun keepBoth(details: ConflictVersionDetails): String = withContext(Dispatchers.IO) {
        require(!details.local.deleted) { "This device's version is a deletion and cannot be renamed" }
        val binding = requireNotNull(syncDao.getBinding(details.conflict.folderId, currentDeviceId)) {
            "This folder is not configured on this device"
        }
        val location = requireNotNull(binding.localLocation) { "This folder has no local location" }
        val occupied = syncDao.fileVersions(details.conflict.folderId).mapTo(mutableSetOf()) { it.relativePath }
        syncDao.allRemoteFileVersions(details.conflict.folderId).mapTo(occupied) { it.relativePath }
        val targetPath = findAvailablePhysicalPath(location, details.local.relativePath, occupied)
        val applier: SyncFileApplier
        val source = if (location.startsWith("content://", true)) {
            DocumentTreeFileApplier(appContext, Uri.parse(location)).also { applier = it }
                .open(details.local.relativePath)
        } else {
            AtomicFileApplier(File(location)).also { applier = it }
            FileInputStream(File(location, details.local.relativePath))
        } ?: error("This device's file is no longer available")

        try {
            source.use { input ->
                applier.apply(targetPath, input, details.local.contentSha256, details.local.modifiedAtMillis)
            }
            val now = System.currentTimeMillis()
            database.withTransaction {
                val state = requireNotNull(syncDao.folderIndexState(details.conflict.folderId, currentDeviceId))
                val nextSequence = state.maxSequence + 1
                syncDao.upsertFileVersion(details.local.copy(
                    relativePath = targetPath,
                    fileId = UUID.randomUUID().toString(),
                    previousContentSha256 = null,
                    versionVectorJson = VersionVector().increment(currentDeviceId).toJson(),
                    localSequence = nextSequence,
                ))
                syncDao.upsertFolderIndexState(state.copy(
                    maxSequence = nextSequence,
                    metadataReceivedSequence = nextSequence,
                    contentAppliedSequence = nextSequence,
                    updatedAtMillis = now,
                ))
                syncDao.resolveDuplicateConflicts(details.conflict.folderId, details.local.relativePath, now)
                syncDao.updateConflictResolution(
                    details.conflict.conflictId,
                    ConflictState.KeepBoth.name,
                    null,
                    targetPath,
                )
            }
        } catch (error: Throwable) {
            runCatching { applier.delete(targetPath) }
            throw error
        }
        targetPath
    }

    private fun findAvailablePhysicalPath(location: String, originalPath: String, occupied: Set<String>): String {
        var suffix = 1
        while (true) {
            val candidate = conflictCopyPathWithSuffix(originalPath, suffix++)
            if (candidate !in occupied && !physicalPathExists(location, candidate)) return candidate
        }
    }

    private fun physicalPathExists(location: String, path: String): Boolean =
        if (location.startsWith("content://", true)) {
            DocumentTreeFileApplier(appContext, Uri.parse(location)).open(path)?.use { true } ?: false
        } else {
            File(location, path).exists()
        }
}

internal data class RemoteConflictReference(val deviceId: String, val fileId: String, val sha256: String)

internal fun ConflictEntity.remoteReference(): RemoteConflictReference? {
    val parts = rightSnapshotId.split(':', limit = 4)
    if (parts.size != 4 || parts[0] != "remote") return null
    return RemoteConflictReference(parts[1], parts[2], parts[3])
}

internal fun ConflictEntity.matches(remote: RemoteFileVersionEntity): Boolean {
    val reference = remoteReference() ?: return false
    // Any trusted peer may relay the exact selected content; it need not be the device first seen.
    return reference.fileId == remote.fileId &&
        reference.sha256.equals(remote.contentSha256, true)
}

internal fun nextConflictCopyPath(relativePath: String, occupiedPaths: Set<String>): String {
    var suffix = 1
    while (true) {
        val candidate = conflictCopyPathWithSuffix(relativePath, suffix++)
        if (candidate !in occupiedPaths) return candidate
    }
}

private fun conflictCopyPathWithSuffix(relativePath: String, suffix: Int): String {
    val normalized = normalizedRelativePath(relativePath)
    val directory = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    val name = normalized.substringAfterLast('/')
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val renamed = "${name.substring(0, dot)}_$suffix${name.substring(dot)}"
    return if (directory.isEmpty()) renamed else "$directory/$renamed"
}
