package com.syncdroid.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateWireTest {
    private val asset = UpdateAssetDescriptor(
        releaseVersion = "0.2.0",
        platformId = "windows-x64",
        fileName = "SyncDows-0.2.0-Windows-x64.exe",
        sha256 = "a".repeat(64),
        sizeBytes = 128_000_000L,
    )

    @Test
    fun updateMessagesRoundTrip() {
        val messages = listOf(
            MeshSessionMessage.UpdateInventory(listOf(asset)),
            MeshSessionMessage.UpdateRequest(asset.sha256, 1_048_576L, 1_048_576),
            MeshSessionMessage.UpdateChunk(asset, 1_048_576L, byteArrayOf(1, 2, 3)),
            MeshSessionMessage.UpdatePhaseDone,
        )

        messages.forEach { message ->
            assertEquals(message, MeshSessionWireCodec.decode(MeshSessionWireCodec.encode(message)))
        }
    }
}
