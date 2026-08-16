package com.syncdroid.app.sync

import androidx.room.withTransaction
import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.app.data.SyncExceptionEntity
import com.syncdroid.app.data.SyncExceptionEventEntity
import com.syncdroid.app.mesh.DeviceSigner
import com.syncdroid.app.mesh.SyncExceptionEvent
import com.syncdroid.app.mesh.create
import com.syncdroid.app.mesh.decodePublicKey
import com.syncdroid.app.mesh.verifySignature

class FolderExceptionRepository(
    private val database: SyncDroidDatabase,
    private val signer: DeviceSigner,
) {
    private val syncDao = database.syncDao()
    private val meshDao = database.meshDao()

    suspend fun setDeletionPolicy(folderId: String, policy: FolderDeletionPolicy) {
        requireNotNull(syncDao.getFolder(folderId)) { "Unknown mesh folder" }
        syncDao.setDeletionPolicy(folderId, policy.name)
    }

    suspend fun record(folderId: String, relativePath: String, nowMillis: Long = System.currentTimeMillis()) =
        createAndApply(folderId, relativePath, active = true, nowMillis)

    suspend fun undo(folderId: String, relativePath: String, nowMillis: Long = System.currentTimeMillis()) =
        createAndApply(folderId, relativePath, active = false, nowMillis)

    suspend fun receive(event: SyncExceptionEvent): Boolean {
        require(event.hasValidEventId()) { "Exception event ID does not match its payload" }
        val folder = requireNotNull(syncDao.getFolder(event.folderId)) { "Unknown mesh folder" }
        require(folder.groupId == event.groupId) { "Exception event belongs to a different mesh" }
        val member = requireNotNull(meshDao.getDevice(event.groupId, event.signerDeviceId)) {
            "Exception signer is not a member of this mesh"
        }
        require(member.trustState == TRUSTED) { "Exception signer is not trusted" }
        require(event.verifySignature(decodePublicKey(member.publicKeyBase64))) { "Invalid exception signature" }

        return database.withTransaction {
            val inserted = syncDao.insertSyncExceptionEvent(event.toEntity()) != -1L
            if (!inserted) return@withTransaction false
            val existing = syncDao.getSyncException(event.folderId, event.relativePath)
            val replaces = existing == null || event.shouldReplace(existing)
            val mergedVersion = VersionVector.fromJson(existing?.versionVectorJson ?: "{}").merge(event.version)
            syncDao.upsertSyncException(
                SyncExceptionEntity(
                    folderId = event.folderId,
                    relativePath = event.relativePath,
                    active = if (replaces) event.active else requireNotNull(existing).active,
                    createdByDeviceId = existing?.createdByDeviceId ?: event.signerDeviceId,
                    createdAtMillis = existing?.createdAtMillis ?: event.createdAtMillis,
                    updatedAtMillis = maxOf(existing?.updatedAtMillis ?: 0, event.createdAtMillis),
                    versionVectorJson = mergedVersion.toJson(),
                    lastEventId = if (replaces) event.eventId else requireNotNull(existing).lastEventId,
                ),
            )
            true
        }
    }

    private suspend fun createAndApply(
        folderId: String,
        relativePath: String,
        active: Boolean,
        nowMillis: Long,
    ): SyncExceptionEvent {
        val folder = requireNotNull(syncDao.getFolder(folderId)) { "Unknown mesh folder" }
        val existing = syncDao.getSyncException(folderId, relativePath.replace('\\', '/').trim('/'))
        val version = VersionVector.fromJson(existing?.versionVectorJson ?: "{}").increment(signer.deviceId)
        val event = SyncExceptionEvent.create(
            groupId = folder.groupId,
            folderId = folderId,
            relativePath = relativePath,
            active = active,
            signer = signer,
            version = version,
            createdAtMillis = nowMillis,
        )
        receive(event)
        return event
    }

    private fun SyncExceptionEvent.shouldReplace(existing: SyncExceptionEntity): Boolean =
        when (VersionVector.fromJson(existing.versionVectorJson).relationTo(version)) {
            CausalRelation.Before -> true
            CausalRelation.After -> false
            CausalRelation.Equal -> eventId > existing.lastEventId
            CausalRelation.Concurrent -> eventId > existing.lastEventId
        }
}

private fun SyncExceptionEvent.toEntity() = SyncExceptionEventEntity(
    eventId,
    groupId,
    folderId,
    relativePath,
    active,
    signerDeviceId,
    version.toJson(),
    createdAtMillis,
    signatureBase64,
)

private const val TRUSTED = "TRUSTED"
