package com.syncdroid.app.sync

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockManifestTest {
    @Test fun manifestCoversEveryByteAndHashesEachBlock() {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val manifest = BlockManifestBuilder.build(
            "folder", "file", "nested/save.sav", data.size.toLong(), ByteArrayInputStream(data),
        )

        assertEquals(data.size.toLong(), manifest.blocks.sumOf { it.sizeBytes.toLong() })
        assertEquals(FileHasher.sha256(ByteArrayInputStream(data)), manifest.contentSha256)
        assertEquals(listOf(0, 1, 2), manifest.blocks.map { it.index })
        manifest.blocks.forEach { block ->
            val bytes = data.copyOfRange(block.offsetBytes.toInt(), block.offsetBytes.toInt() + block.sizeBytes)
            assertEquals(FileHasher.sha256(ByteArrayInputStream(bytes)), block.sha256)
        }
    }

    @Test fun emptyFileStillHasOneVerifiableBlock() {
        val manifest = BlockManifestBuilder.build("folder", "file", "empty.sav", 0, ByteArrayInputStream(byteArrayOf()))
        assertEquals(1, manifest.blocks.size)
        assertEquals(0, manifest.blocks.single().sizeBytes)
        assertTrue(manifest.contentSha256.isNotBlank())
        assertEquals(manifest.contentSha256, manifest.blocks.single().sha256)
    }
}
