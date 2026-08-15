package com.syncdroid.app.sync

import com.syncdroid.app.data.FolderIndexStateEntity
import com.syncdroid.app.data.SyncDao

data class FolderIndexSummary(
    val folderId: String,
    val deviceId: String,
    val indexEpoch: Long,
    val maxSequence: Long,
    val metadataReceivedSequence: Long,
    val contentAppliedSequence: Long,
)

sealed interface IndexAcceptance {
    data class Accepted(val state: FolderIndexSummary) : IndexAcceptance
    data object RequiresFullIndex : IndexAcceptance
}

class IndexStateRepository(private val syncDao: SyncDao) {
    suspend fun summary(folderId: String, deviceId: String): FolderIndexSummary? =
        syncDao.folderIndexState(folderId, deviceId)?.toDomain()

    suspend fun receiveMetadata(
        folderId: String,
        remoteDeviceId: String,
        indexEpoch: Long,
        previousSequence: Long,
        lastSequence: Long,
        fullIndex: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): IndexAcceptance {
        require(indexEpoch != 0L) { "Index epoch cannot be zero" }
        require(previousSequence >= 0 && lastSequence >= previousSequence) { "Invalid index sequence range" }
        val existing = syncDao.folderIndexState(folderId, remoteDeviceId)
        val epochChanged = existing != null && existing.indexEpoch != indexEpoch
        if ((existing == null || epochChanged) && !fullIndex) return IndexAcceptance.RequiresFullIndex
        val expectedPrevious = if (fullIndex || epochChanged) 0 else existing?.maxSequence ?: 0
        if (previousSequence != expectedPrevious) return IndexAcceptance.RequiresFullIndex

        val updated = FolderIndexStateEntity(
            folderId = folderId,
            deviceId = remoteDeviceId,
            indexEpoch = indexEpoch,
            maxSequence = lastSequence,
            metadataReceivedSequence = lastSequence,
            contentAppliedSequence = if (epochChanged || fullIndex) 0 else existing?.contentAppliedSequence ?: 0,
            updatedAtMillis = nowMillis,
        )
        syncDao.upsertFolderIndexState(updated)
        return IndexAcceptance.Accepted(updated.toDomain())
    }

    suspend fun acknowledgeApplied(
        folderId: String,
        deviceId: String,
        indexEpoch: Long,
        sequence: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): FolderIndexSummary {
        val current = requireNotNull(syncDao.folderIndexState(folderId, deviceId)) { "Unknown device index" }
        require(current.indexEpoch == indexEpoch) { "Acknowledgement belongs to a stale index epoch" }
        require(sequence in current.contentAppliedSequence..current.metadataReceivedSequence) {
            "Applied acknowledgement is outside the received metadata range"
        }
        val updated = current.copy(contentAppliedSequence = sequence, updatedAtMillis = nowMillis)
        syncDao.upsertFolderIndexState(updated)
        return updated.toDomain()
    }
}

private fun FolderIndexStateEntity.toDomain() = FolderIndexSummary(
    folderId,
    deviceId,
    indexEpoch,
    maxSequence,
    metadataReceivedSequence,
    contentAppliedSequence,
)
