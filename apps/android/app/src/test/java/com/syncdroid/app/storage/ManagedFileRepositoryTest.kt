package com.syncdroid.app.storage

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedFileRepositoryTest {
    @Test
    fun createsAndListsFolderInsideManagedRoot() {
        val temporaryRoot = Files.createTempDirectory("syncdroid-test").toFile()
        try {
            val repository = ManagedFileRepository(temporaryRoot)
            val created = repository.createFolder(repository.root, "Game saves").getOrThrow()

            assertTrue(created.isDirectory)
            assertEquals(listOf("Game saves"), repository.list(repository.root).map { it.file.name })
        } finally {
            temporaryRoot.deleteRecursively()
        }
    }

    @Test
    fun rejectsPathTraversalFolderName() {
        val temporaryRoot = Files.createTempDirectory("syncdroid-test").toFile()
        try {
            val repository = ManagedFileRepository(temporaryRoot)
            assertTrue(repository.createFolder(repository.root, "../outside").isFailure)
        } finally {
            temporaryRoot.deleteRecursively()
        }
    }
}
