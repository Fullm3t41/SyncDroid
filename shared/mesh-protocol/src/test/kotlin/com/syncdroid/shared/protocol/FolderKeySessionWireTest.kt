package com.syncdroid.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class FolderKeySessionWireTest {
    @Test
    fun folderKeysRoundTrip() {
        val expected = MeshSessionMessage.FolderKeys(
            listOf(SessionFolderKey("folder-1", "key-1", ByteArray(32) { it.toByte() })),
        )
        assertEquals(expected, MeshSessionWireCodec.decode(MeshSessionWireCodec.encode(expected)))
    }
}
