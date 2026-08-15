package com.syncdroid.app.mesh

import com.syncdroid.app.data.LocalFolderBindingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeshFolderBindingTest {
    @Test
    fun `replaying an announcement preserves a configured local binding`() {
        val configured = LocalFolderBindingEntity(
            folderId = "folder-1",
            deviceId = "device-1",
            localLocation = "/storage/emulated/0/SyncDroid",
            state = LocalFolderBindingState.CONFIGURED.name,
            updatedAtMillis = 100,
        )

        val update = bindingUpdateForAnnouncement(
            folderId = "folder-1",
            deviceId = "device-1",
            localLocation = null,
            existingBinding = configured,
            updatedAtMillis = 200,
        )

        assertNull(update)
    }

    @Test
    fun `a newly received folder starts pending local configuration`() {
        val update = bindingUpdateForAnnouncement(
            folderId = "folder-1",
            deviceId = "device-1",
            localLocation = null,
            existingBinding = null,
            updatedAtMillis = 200,
        )

        assertEquals(LocalFolderBindingState.PENDING_CONFIGURATION.name, update?.state)
        assertNull(update?.localLocation)
    }

    @Test
    fun `choosing a local location creates a configured binding`() {
        val update = bindingUpdateForAnnouncement(
            folderId = "folder-1",
            deviceId = "device-1",
            localLocation = "/storage/emulated/0/SyncDroid",
            existingBinding = null,
            updatedAtMillis = 200,
        )

        assertEquals(LocalFolderBindingState.CONFIGURED.name, update?.state)
        assertEquals("/storage/emulated/0/SyncDroid", update?.localLocation)
    }
}
