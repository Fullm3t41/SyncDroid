package com.syncdroid.app.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedStorageRootsTest {
    @Test fun derivesRemovableRootFromAppExternalDirectory() {
        val directory = File("/storage/1234-ABCD/Android/data/com.syncdroid.app/files")
        assertEquals(
            File("/storage/1234-ABCD"),
            deriveStorageRoot(directory, "com.syncdroid.app"),
        )
    }

    @Test fun rejectsDirectoryForAnotherPackage() {
        val directory = File("/storage/1234-ABCD/Android/data/another.app/files")
        assertNull(deriveStorageRoot(directory, "com.syncdroid.app"))
    }
}
