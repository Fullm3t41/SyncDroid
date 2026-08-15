package com.syncdroid.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDecisionTest {
    private fun snapshot(id: String, vector: VersionVector, hash: String = id, modifiedAtMillis: Long = 1) = SnapshotManifest(
        snapshotId = id,
        folderId = "folder",
        originDeviceId = "device",
        createdAtMillis = 1,
        version = vector,
        parentSnapshotIds = emptyList(),
        files = listOf(FileManifestEntry("save.sav", 1, modifiedAtMillis, hash)),
    )

    @Test fun acceptsCausallyNewerRemote() {
        val local = snapshot("local", VersionVector(mapOf("phone" to 1)))
        val remote = snapshot("remote", VersionVector(mapOf("phone" to 2)))
        assertEquals(SyncDecision.Action.AcceptRemote, decideSync(local, remote).action)
    }

    @Test fun concurrentProgressCreatesConflict() {
        val local = snapshot("local", VersionVector(mapOf("phone" to 2)))
        val remote = snapshot("remote", VersionVector(mapOf("tablet" to 2)))
        assertEquals(SyncDecision.Action.Conflict, decideSync(local, remote).action)
    }

    @Test fun equalContentWithDifferentFilesystemTimestampsDoesNothing() {
        val vector = VersionVector(mapOf("phone" to 2))
        val local = snapshot("local", vector, hash = "same", modifiedAtMillis = 100)
        val remote = snapshot("remote", vector, hash = "same", modifiedAtMillis = 999)

        assertEquals(SyncDecision.Action.Nothing, decideSync(local, remote).action)
    }
}
