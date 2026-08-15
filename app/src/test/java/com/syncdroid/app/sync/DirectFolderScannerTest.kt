package com.syncdroid.app.sync

import com.syncdroid.app.storage.SyncFilterRules
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DirectFolderScannerTest {
    @Test fun scannerAppliesFiltersAndUsesRelativePaths() {
        val root = Files.createTempDirectory("syncdroid-scan").toFile()
        try {
            File(root, "slot1.sav").writeText("level 20")
            File(root, "cache.tmp").writeText("ignore")
            File(root, "nested").mkdir()
            File(root, "nested/slot2.sav").writeText("level 10")

            val files = DirectFolderScanner().scan(
                root,
                SyncFilterRules(includes = listOf("*.sav"), excludes = listOf("*.tmp")),
            )

            assertEquals(listOf("nested/slot2.sav", "slot1.sav"), files.map { it.relativePath })
            assertEquals(64, files.first().sha256.length)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun atomicApplyVerifiesHashAndRejectsTraversal() {
        val root = Files.createTempDirectory("syncdroid-apply").toFile()
        try {
            val bytes = "level 20".toByteArray()
            val hash = FileHasher.sha256(ByteArrayInputStream(bytes))
            val applier = AtomicFileApplier(root)
            val sourceTimestamp = 1_700_000_000_000L
            applier.apply("nested/save.sav", ByteArrayInputStream(bytes), hash, sourceTimestamp)
            assertEquals("level 20", File(root, "nested/save.sav").readText())
            assertEquals(sourceTimestamp, File(root, "nested/save.sav").lastModified())

            assertThrows(IllegalArgumentException::class.java) {
                applier.apply("../escape.sav", ByteArrayInputStream(bytes), hash)
            }
            assertFalse(File(root.parentFile, "escape.sav").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
