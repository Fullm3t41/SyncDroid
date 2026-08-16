package com.syncdroid.app.mesh

import com.syncdroid.app.cloud.WrappedFolderKeyTransfer
import com.syncdroid.app.sync.FileBlock
import com.syncdroid.app.sync.FolderIndexUpdate
import com.syncdroid.app.sync.IndexedFileRecord
import com.syncdroid.app.sync.VersionVector
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MeshSessionCodecTest {
    @Test
    fun indexBatchRoundTripsVectorsParentsTombstonesAndBlocks() {
        val record = IndexedFileRecord(
            relativePath = "save/main.sav",
            fileId = "file-1",
            sizeBytes = 3,
            modifiedAtMillis = 42,
            contentSha256 = "ab".repeat(32),
            previousContentSha256 = "cd".repeat(32),
            originDeviceId = "phone-a",
            deleted = false,
            version = VersionVector(mapOf("phone-a" to 7, "phone-b" to 2)),
            sequence = 9,
            blockSizeBytes = 128 * 1024,
            blocks = listOf(FileBlock(0, 0, 3, "ef".repeat(32))),
        )
        val message = MeshSessionMessage.IndexBatch(listOf(
            FolderIndexUpdate("folder-1", 123, 8, 9, false, listOf(record)),
        ))

        assertEquals(message, MeshSessionCodec.decode(MeshSessionCodec.encode(message)))
    }

    @Test
    fun pairingCompletionRoundTripsEncryptedKeys() {
        val key = WrappedFolderKeyTransfer("folder", "key", byteArrayOf(1, 2), byteArrayOf(3, 4, 5))
        val message = PairingCompletionMessage.Complete("group", "My mesh", byteArrayOf(6, 7), listOf(key))

        val decoded = PairingCompletionCodec.decode(PairingCompletionCodec.encode(message)) as PairingCompletionMessage.Complete
        assertEquals(message.groupId, decoded.groupId)
        assertEquals(message.groupName, decoded.groupName)
        assertArrayEquals(message.meshBundle, decoded.meshBundle)
        assertEquals(key.folderId, decoded.folderKeys.single().folderId)
        assertArrayEquals(key.nonce, decoded.folderKeys.single().nonce)
        assertArrayEquals(key.ciphertext, decoded.folderKeys.single().ciphertext)
    }
}
