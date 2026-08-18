package com.syncdroid.shared.cloud

import com.syncdroid.shared.protocol.FolderIndexUpdate
import com.syncdroid.shared.protocol.IndexedFileRecord
import com.syncdroid.shared.protocol.VersionVector
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CloudEncryptionTest {
    private val key = FolderKeyMaterial("folder", "key", ByteArray(32) { it.toByte() })

    @Test
    fun manifestRoundTrips() {
        val update = FolderIndexUpdate(
            "folder", 7, 0, 1, true,
            listOf(IndexedFileRecord("save.sav", "file", 3, 4, "abc", null, "phone", false, VersionVector(mapOf("phone" to 1)), 1)),
        )
        val expected = CloudFolderManifest("folder", "Saves", "phone", 99, update)
        val encrypted = CloudEncryptedObjects.encryptManifest(key, expected)
        assertEquals(expected, CloudEncryptedObjects.decryptManifest(key, "phone", encrypted))
    }

    @Test
    fun fileRoundTrips() {
        val directory = Files.createTempDirectory("cloud-encryption-test")
        try {
            val source = directory.resolve("source").also { Files.write(it, byteArrayOf(1, 2, 3, 4)) }
            val encrypted = directory.resolve("encrypted")
            val restored = directory.resolve("restored")
            CloudEncryptedObjects.encryptFile(key, "file", "hash", source, encrypted)
            CloudEncryptedObjects.decryptFile(key, "file", "hash", encrypted, restored)
            assertContentEquals(Files.readAllBytes(source), Files.readAllBytes(restored))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
