package com.syncdroid.app.sync

import com.syncdroid.app.data.SyncExceptionEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderExceptionStateTest {
    @Test
    fun eachDeviceKeepsItsOwnLatestAbsenceReport() {
        val events = listOf(
            event("a-1", "phone-a", "save/main.sav", active = true, counter = 1),
            event("b-1", "phone-b", "save/main.sav", active = true, counter = 1),
            event("a-2", "phone-a", "save/main.sav", active = false, counter = 2),
            event("a-3", "phone-a", "save/slot2.sav", active = true, counter = 3),
        )

        assertEquals(setOf("save/slot2.sav"), events.activePathsForDevice("phone-a"))
        assertEquals(setOf("save/main.sav"), events.activePathsForDevice("phone-b"))
    }

    private fun event(
        id: String,
        signer: String,
        path: String,
        active: Boolean,
        counter: Long,
    ) = SyncExceptionEventEntity(
        eventId = id,
        groupId = "group-1",
        folderId = "folder-1",
        relativePath = path,
        active = active,
        signerDeviceId = signer,
        versionVectorJson = "{\"$signer\":$counter}",
        createdAtMillis = counter,
        signatureBase64 = "signature",
    )
}
