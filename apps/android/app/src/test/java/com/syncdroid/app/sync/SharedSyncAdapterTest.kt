package com.syncdroid.app.sync

import com.syncdroid.app.data.FileVersionEntity
import com.syncdroid.app.data.RemoteFileVersionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedSyncAdapterTest {
    @Test
    fun androidEntitiesUseSharedFileDecision() {
        val local = FileVersionEntity(
            "folder", "save.sav", "file", 3, 1, "old", null, false,
            VersionVector(mapOf("phone" to 1)).toJson(), "phone", 1,
        )
        val remote = RemoteFileVersionEntity(
            "folder", "tablet", "save.sav", "file", 4, 2, "new", "old", "tablet", false,
            VersionVector(mapOf("phone" to 2)).toJson(), 2,
        )
        assertEquals(FileSyncAction.DownloadRemote, decideFileSync(local, remote).first)
    }
}
