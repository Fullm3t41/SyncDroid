package com.syncdroid.app.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudFolderProvisionerTest {
    @Test fun createsNamedFolderInsideSyncDroidRoot() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val api = object : CloudFolderApi {
            override suspend fun ensureFolder(parentId: String, name: String): String {
                calls += parentId to name
                return if (name == "SyncDroid") "syncdroid-id" else "game-id"
            }
        }

        val result = CloudFolderProvisioner(api).provision("provider-root", "Game Saves")

        assertEquals(
            listOf("provider-root" to "SyncDroid", "syncdroid-id" to "Game Saves"),
            calls,
        )
        assertEquals("SyncDroid/Game Saves", result.displayPath)
        assertEquals("game-id", result.syncFolderRootId)
    }
}
