package com.syncdroid.app.sync

import android.content.Context
import android.net.Uri
import com.syncdroid.app.data.FileVersionEntity
import com.syncdroid.app.data.FolderIndexStateEntity
import com.syncdroid.app.data.SnapshotEntity
import com.syncdroid.app.data.SnapshotFileEntity
import com.syncdroid.app.data.SyncDao
import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.app.mesh.DeviceSigner
import com.syncdroid.app.storage.SyncFilterRules
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID

class SnapshotRepository(
    database: SyncDroidDatabase,
    signer: DeviceSigner,
    private val scanner: DirectFolderScanner = DirectFolderScanner(),
    private val epochSource: () -> Long = ::randomIndexEpoch,
) {
    private val syncDao: SyncDao = database.syncDao()
    private val exceptionRepository = FolderExceptionRepository(database, signer)
    suspend fun scanDirectFolder(
        folderId: String,
        originDeviceId: String,
        rootDirectory: File,
        rules: SyncFilterRules,
        nowMillis: Long = System.currentTimeMillis(),
    ): SnapshotManifest? {
        val activeExceptions = syncDao.activeExceptions(folderId).mapTo(mutableSetOf()) { it.relativePath }
        val scannedFiles = scanner.scan(rootDirectory, rules, activeExceptions)
        return persistScan(folderId, originDeviceId, scannedFiles, nowMillis)
    }

    suspend fun scanDocumentTree(
        context: Context,
        folderId: String,
        originDeviceId: String,
        treeUri: Uri,
        rules: SyncFilterRules,
        nowMillis: Long = System.currentTimeMillis(),
    ): SnapshotManifest? {
        val activeExceptions = syncDao.activeExceptions(folderId).mapTo(mutableSetOf()) { it.relativePath }
        val scannedFiles = DocumentTreeScanner(context).scan(treeUri, rules, activeExceptions)
        return persistScan(folderId, originDeviceId, scannedFiles, nowMillis)
    }

    private suspend fun persistScan(
        folderId: String,
        originDeviceId: String,
        scannedFiles: List<FileManifestEntry>,
        nowMillis: Long,
    ): SnapshotManifest? {
        val folder = requireNotNull(syncDao.getFolder(folderId)) { "Unknown mesh folder" }
        val policy = runCatching { FolderDeletionPolicy.valueOf(folder.deletionPolicy) }
            .getOrDefault(FolderDeletionPolicy.PROPAGATE)
        val activeExceptions = syncDao.activeExceptions(folderId).mapTo(mutableSetOf()) { it.relativePath }
        val previousSnapshot = syncDao.latestSnapshot(folderId)
        val previousFiles = existingFileVersions(folderId, previousSnapshot)
            .associateBy(FileVersionEntity::relativePath)
        val existingLocalIndex = syncDao.folderIndexState(folderId, originDeviceId)
        val localIndex = existingLocalIndex ?: FolderIndexStateEntity(
            folderId = folderId,
            deviceId = originDeviceId,
            indexEpoch = epochSource().nonZeroEpoch(),
            maxSequence = 0,
            metadataReceivedSequence = 0,
            contentAppliedSequence = 0,
            updatedAtMillis = nowMillis,
        )

        var nextSequence = localIndex.maxSequence
        var changed = false
        val updated = linkedMapOf<String, FileVersionEntity>()

        for (file in scannedFiles) {
            val previous = previousFiles[file.relativePath]
            val unchanged = previous != null && previous.localSequence > 0 && !previous.deleted &&
                previous.sizeBytes == file.sizeBytes &&
                previous.contentSha256.equals(file.sha256, ignoreCase = true)
            val entry = if (unchanged) {
                previous
            } else {
                changed = true
                nextSequence++
                FileVersionEntity(
                    folderId = folderId,
                    relativePath = file.relativePath,
                    fileId = previous?.fileId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
                    sizeBytes = file.sizeBytes,
                    modifiedAtMillis = file.modifiedAtMillis,
                    contentSha256 = file.sha256.lowercase(),
                    previousContentSha256 = previous?.contentSha256?.takeIf(String::isNotBlank),
                    deleted = false,
                    versionVectorJson = (previous?.versionVectorJson?.let(VersionVector::fromJson) ?: VersionVector())
                        .increment(originDeviceId)
                        .toJson(),
                    originDeviceId = originDeviceId,
                    localSequence = nextSequence,
                )
            }
            updated[file.relativePath] = entry
        }

        val scannedPaths = scannedFiles.mapTo(mutableSetOf(), FileManifestEntry::relativePath)
        for ((path, previous) in previousFiles) {
            if (path in scannedPaths) continue
            when {
                path in activeExceptions || policy == FolderDeletionPolicy.OVERWRITE_ONLY -> {
                    if (path !in activeExceptions) {
                        exceptionRepository.record(folderId, path, nowMillis)
                    }
                    // Keep the last globally known version. The local exception prevents it being pulled back.
                    updated[path] = previous
                }
                previous.deleted -> updated[path] = previous
                else -> {
                    changed = true
                    nextSequence++
                    updated[path] = previous.copy(
                        sizeBytes = 0,
                        modifiedAtMillis = nowMillis,
                        contentSha256 = "",
                        previousContentSha256 = previous.contentSha256.takeIf(String::isNotBlank),
                        deleted = true,
                        versionVectorJson = VersionVector.fromJson(previous.versionVectorJson)
                            .increment(originDeviceId)
                            .toJson(),
                        originDeviceId = originDeviceId,
                        localSequence = nextSequence,
                    )
                }
            }
        }

        if (!changed) {
            if (existingLocalIndex == null) syncDao.upsertFolderIndexState(localIndex)
            return null
        }

        val snapshotId = UUID.randomUUID().toString()
        val parentIds = listOfNotNull(previousSnapshot?.snapshotId)
        val files = updated.values.sortedBy(FileVersionEntity::relativePath).map(FileVersionEntity::toManifest)
        val snapshotVersion = files.fold(VersionVector()) { merged, file -> merged.merge(file.version) }
        val manifest = SnapshotManifest(
            snapshotId = snapshotId,
            folderId = folderId,
            originDeviceId = originDeviceId,
            createdAtMillis = nowMillis,
            version = snapshotVersion,
            parentSnapshotIds = parentIds,
            files = files,
        )
        val updatedIndex = localIndex.copy(
            maxSequence = nextSequence,
            metadataReceivedSequence = nextSequence,
            contentAppliedSequence = nextSequence,
            updatedAtMillis = nowMillis,
        )
        syncDao.insertVersionedSnapshot(
            SnapshotEntity(
                snapshotId = snapshotId,
                folderId = folderId,
                originDeviceId = originDeviceId,
                createdAtMillis = nowMillis,
                versionVectorJson = snapshotVersion.toJson(),
                parentSnapshotIdsJson = parentIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" },
                state = SnapshotState.Complete.name,
            ),
            files.map { it.toEntity(snapshotId) },
            updated.values.toList(),
            updatedIndex,
        )
        return manifest
    }

    private suspend fun existingFileVersions(
        folderId: String,
        previousSnapshot: SnapshotEntity?,
    ): List<FileVersionEntity> {
        val current = syncDao.fileVersions(folderId)
        if (current.isNotEmpty() || previousSnapshot == null) return current
        val legacyVector = VersionVector.fromJson(previousSnapshot.versionVectorJson)
        return syncDao.filesForSnapshot(previousSnapshot.snapshotId).map { file ->
            FileVersionEntity(
                folderId = folderId,
                relativePath = file.relativePath,
                fileId = file.fileId.ifBlank { stableLegacyFileId(folderId, file.relativePath) },
                sizeBytes = file.sizeBytes,
                modifiedAtMillis = file.modifiedAtMillis,
                contentSha256 = file.sha256,
                previousContentSha256 = file.previousSha256,
                deleted = file.deleted,
                versionVectorJson = file.versionVectorJson.takeUnless { it == "{}" } ?: legacyVector.toJson(),
                originDeviceId = previousSnapshot.originDeviceId,
                localSequence = file.localSequence,
            )
        }
    }
}

private fun FileVersionEntity.toManifest() = FileManifestEntry(
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    sha256 = contentSha256,
    deleted = deleted,
    fileId = fileId,
    previousSha256 = previousContentSha256,
    version = VersionVector.fromJson(versionVectorJson),
    localSequence = localSequence,
    originDeviceId = originDeviceId,
)

private fun FileManifestEntry.toEntity(snapshotId: String) = SnapshotFileEntity(
    snapshotId = snapshotId,
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    sha256 = sha256,
    deleted = deleted,
    fileId = fileId,
    previousSha256 = previousSha256,
    versionVectorJson = version.toJson(),
    localSequence = localSequence,
)

private fun stableLegacyFileId(folderId: String, path: String): String = UUID.nameUUIDFromBytes(
    "$folderId\u0000$path".toByteArray(StandardCharsets.UTF_8),
).toString()

private fun randomIndexEpoch(): Long = SecureRandom().nextLong().nonZeroEpoch()
private fun Long.nonZeroEpoch(): Long = (this and Long.MAX_VALUE).coerceAtLeast(1)
