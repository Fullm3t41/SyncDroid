package com.syncdroid.app.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfiguredFolderSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun listsNestedFilesWithMetadataAndDeletesSelectedFile() {
        val root = temporaryFolder.newFolder("sync")
        val nested = root.resolve("slot-1").apply { mkdir() }
        val save = nested.resolve("game.sav").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val source = DirectConfiguredFolderSource(root)

        assertEquals("slot-1", source.list("").single().relativePath)
        val entry = source.list("slot-1").single()
        assertEquals("slot-1/game.sav", entry.relativePath)
        assertEquals(4, entry.sizeBytes)
        assertTrue(entry.modifiedAtMillis > 0)

        source.delete(listOf(entry.relativePath))

        assertFalse(save.exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToDeleteDirectories() {
        val root = temporaryFolder.newFolder("sync")
        root.resolve("slot-1").mkdir()

        DirectConfiguredFolderSource(root).delete(listOf("slot-1"))
    }
}
