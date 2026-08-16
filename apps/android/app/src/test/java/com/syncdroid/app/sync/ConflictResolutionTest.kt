package com.syncdroid.app.sync

import com.syncdroid.app.data.ConflictEntity
import com.syncdroid.app.data.RemoteFileVersionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionTest {
    @Test
    fun conflictCopyUsesNextAvailableSuffixBeforeExtension() {
        assertEquals(
            "slots/game_3.sav",
            nextConflictCopyPath(
                "slots/game.sav",
                setOf("slots/game.sav", "slots/game_1.sav", "slots/game_2.sav"),
            ),
        )
    }

    @Test
    fun conflictCopySupportsFilesWithoutExtensions() {
        assertEquals("save_1", nextConflictCopyPath("save", setOf("save")))
    }

    @Test
    fun pendingChoiceMatchesTheExactVersionFromAnyTrustedRelay() {
        val conflict = ConflictEntity(
            conflictId = "conflict",
            folderId = "folder",
            relativePath = "game.sav",
            leftSnapshotId = "local:local-file:aaaa",
            rightSnapshotId = "remote:peer-1:remote-file:bbbb",
            state = ConflictState.KeepRight.name,
            createdAtMillis = 1,
            resolvedAtMillis = null,
        )
        val selected = remote(deviceId = "peer-1", hash = "bbbb")

        assertTrue(conflict.matches(selected))
        assertTrue(conflict.matches(selected.copy(deviceId = "peer-2")))
        assertFalse(conflict.matches(selected.copy(fileId = "another-file")))
        assertFalse(conflict.matches(selected.copy(contentSha256 = "cccc")))
    }

    private fun remote(deviceId: String, hash: String) = RemoteFileVersionEntity(
        folderId = "folder",
        deviceId = deviceId,
        relativePath = "game.sav",
        fileId = "remote-file",
        sizeBytes = 10,
        modifiedAtMillis = 1,
        contentSha256 = hash,
        previousContentSha256 = null,
        originDeviceId = deviceId,
        deleted = false,
        versionVectorJson = "{}",
        remoteSequence = 1,
    )
}
