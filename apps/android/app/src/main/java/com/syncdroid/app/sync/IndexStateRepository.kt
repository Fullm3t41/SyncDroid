package com.syncdroid.app.sync

import com.syncdroid.app.data.FolderIndexStateEntity
import com.syncdroid.app.data.SyncDao
import com.syncdroid.shared.sync.IndexReceiveDecision
import com.syncdroid.shared.sync.IndexStateSnapshot
import com.syncdroid.shared.sync.acknowledgeIndexContent
import com.syncdroid.shared.sync.reconcileReceivedIndex

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
        val existing = syncDao.folderIndexState(folderId, remoteDeviceId)
        val decision = reconcileReceivedIndex(
            existing?.toSnapshot(), indexEpoch, previousSequence, lastSequence, fullIndex,
        )
        if (decision is IndexReceiveDecision.RequiresFullIndex) return IndexAcceptance.RequiresFullIndex
        val next = (decision as IndexReceiveDecision.Accepted).next

        val updated = FolderIndexStateEntity(
            folderId = folderId,
            deviceId = remoteDeviceId,
            indexEpoch = next.indexEpoch,
            maxSequence = next.maxSequence,
            metadataReceivedSequence = next.metadataReceivedSequence,
            contentAppliedSequence = next.contentAppliedSequence,
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
        val next = acknowledgeIndexContent(current.toSnapshot(), indexEpoch, sequence)
        val updated = current.copy(contentAppliedSequence = next.contentAppliedSequence, updatedAtMillis = nowMillis)
        syncDao.upsertFolderIndexState(updated)
        return updated.toDomain()
    }
}

private fun FolderIndexStateEntity.toSnapshot() = IndexStateSnapshot(
    indexEpoch, maxSequence, metadataReceivedSequence, contentAppliedSequence,
)

private fun FolderIndexStateEntity.toDomain() = FolderIndexSummary(
    folderId,
    deviceId,
    indexEpoch,
    maxSequence,
    metadataReceivedSequence,
    contentAppliedSequence,
)
