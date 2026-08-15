package com.syncdroid.app.mesh

import com.syncdroid.app.data.DeviceEntity
import com.syncdroid.app.data.MembershipEventEntity
import com.syncdroid.app.data.MeshDao
import com.syncdroid.app.data.MeshGroupEntity
import com.syncdroid.app.sync.VersionVector
import org.json.JSONArray
import java.util.Base64

class MeshMembershipRepository(private val meshDao: MeshDao) {
    suspend fun restoreCreatorProjection(
        groupId: String,
        groupName: String,
        expectedCreatorDeviceId: String? = null,
    ): Boolean {
        val events = meshDao.membershipEvents(groupId)
        return restoreCreatorProjection(groupName, expectedCreatorDeviceId, events)
    }

    suspend fun rebuildProjection(groupId: String, groupName: String): Boolean {
        val events = meshDao.membershipEvents(groupId)
        if (events.isEmpty()) return false
        if (!restoreCreatorProjection(groupName, expectedCreatorDeviceId = null, events)) return false
        return events.all { apply(groupName, it.toDomain(), replayRecorded = true).isSuccess }
    }

    private suspend fun restoreCreatorProjection(
        groupName: String,
        expectedCreatorDeviceId: String?,
        events: List<MembershipEventEntity>,
    ): Boolean {
        val creator = events.firstOrNull()?.toDomain() ?: return false
        if (
            creator.eventType != MembershipEventType.AddDevice ||
            (expectedCreatorDeviceId != null && creator.subjectDeviceId != expectedCreatorDeviceId) ||
            creator.signerDeviceId != creator.subjectDeviceId ||
            !creator.hasValidEventId() ||
            !creator.hasValidSubjectId()
        ) return false
        val creatorKey = decodePublicKey(creator.subjectPublicKeyBase64)
        if (!creator.verifySignature(creatorKey)) return false

        meshDao.upsertGroup(MeshGroupEntity(creator.groupId, groupName, creator.createdAtMillis))
        meshDao.upsertDevice(
            DeviceEntity(
                groupId = creator.groupId,
                deviceId = creator.subjectDeviceId,
                displayName = creator.subjectDisplayName,
                publicKeyBase64 = creator.subjectPublicKeyBase64,
                fingerprint = fingerprintFor(creatorKey),
                trustState = TRUSTED,
                addedByDeviceId = creator.signerDeviceId,
                addedAtMillis = creator.createdAtMillis,
                lastSeenAtMillis = null,
            ),
        )
        return true
    }

    suspend fun apply(
        groupName: String,
        event: MembershipEvent,
        replayRecorded: Boolean = false,
    ): Result<Boolean> = runCatching {
        require(event.hasValidEventId()) { "Membership event ID does not match its payload" }
        require(event.hasValidSubjectId()) { "Subject public key does not match its device ID" }
        if (!replayRecorded && meshDao.hasMembershipEvent(event.eventId)) return@runCatching false

        val trustedCount = meshDao.trustedDeviceCount(event.groupId)
        val signerKey = if (trustedCount == 0) {
            require(event.eventType == MembershipEventType.AddDevice) { "The first membership event must add a device" }
            require(event.signerDeviceId == event.subjectDeviceId) { "The first group member must add itself" }
            decodePublicKey(event.subjectPublicKeyBase64)
        } else {
            val signer = requireNotNull(meshDao.getDevice(event.groupId, event.signerDeviceId)) {
                "Membership event signer is unknown"
            }
            require(signer.trustState == TRUSTED) { "Membership event signer is not trusted" }
            decodePublicKey(signer.publicKeyBase64)
        }
        val existing = meshDao.getDevice(event.groupId, event.subjectDeviceId)
        when (event.eventType) {
            MembershipEventType.UpdateDeviceName -> {
                require(event.signerDeviceId == event.subjectDeviceId) { "A device can only update its own nickname" }
                require(existing?.trustState == TRUSTED) {
                    "Only an existing trusted device can update its nickname"
                }
            }
            MembershipEventType.RemoveDevice -> {
                require(existing?.trustState == TRUSTED) { "Only an existing trusted device can be removed" }
                require(existing.publicKeyBase64 == event.subjectPublicKeyBase64) {
                    "Removal event does not match the trusted device identity"
                }
            }
            MembershipEventType.AddDevice -> Unit
        }
        require(event.verifySignature(signerKey)) { "Membership signature is invalid" }

        meshDao.upsertGroup(MeshGroupEntity(event.groupId, groupName, event.createdAtMillis))
        val inserted = meshDao.insertMembershipEvent(event.toEntity()) != -1L
        val subjectKey = decodePublicKey(event.subjectPublicKeyBase64)
        meshDao.upsertDevice(
            DeviceEntity(
                groupId = event.groupId,
                deviceId = event.subjectDeviceId,
                displayName = event.subjectDisplayName,
                publicKeyBase64 = event.subjectPublicKeyBase64,
                fingerprint = fingerprintFor(subjectKey),
                trustState = if (event.eventType == MembershipEventType.RemoveDevice) REMOVED else TRUSTED,
                addedByDeviceId = existing?.addedByDeviceId ?: event.signerDeviceId,
                addedAtMillis = existing?.addedAtMillis ?: event.createdAtMillis,
                lastSeenAtMillis = existing?.lastSeenAtMillis,
            ),
        )
        inserted
    }

    private fun MembershipEvent.toEntity() = MembershipEventEntity(
        eventId = eventId,
        groupId = groupId,
        eventType = eventType.name,
        subjectDeviceId = subjectDeviceId,
        subjectDisplayName = subjectDisplayName,
        subjectPublicKeyBase64 = subjectPublicKeyBase64,
        signerDeviceId = signerDeviceId,
        signatureBase64 = signatureBase64,
        parentEventIdsJson = JSONArray(parentEventIds).toString(),
        versionVectorJson = version.toJson(),
        createdAtMillis = createdAtMillis,
    )

    private fun MembershipEventEntity.toDomain() = MembershipEvent(
        eventId = eventId,
        groupId = groupId,
        eventType = MembershipEventType.valueOf(eventType),
        subjectDeviceId = subjectDeviceId,
        subjectDisplayName = subjectDisplayName,
        subjectPublicKeyBase64 = subjectPublicKeyBase64,
        signerDeviceId = signerDeviceId,
        signatureBase64 = signatureBase64,
        parentEventIds = JSONArray(parentEventIdsJson).strings(),
        version = VersionVector.fromJson(versionVectorJson),
        createdAtMillis = createdAtMillis,
    )

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private companion object {
        const val REMOVED = "REMOVED"
        const val TRUSTED = "TRUSTED"
    }
}
