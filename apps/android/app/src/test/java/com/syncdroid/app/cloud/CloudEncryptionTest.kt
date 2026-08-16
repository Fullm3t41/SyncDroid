package com.syncdroid.app.cloud

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudEncryptionTest {
    @Test fun cloudObjectIsAuthenticatedAndBoundToItsLogicalIdentity() {
        val key = FolderKeyMaterial("folder-1", "key-1", ByteArray(32).also(SecureRandom()::nextBytes))
        val plaintext = "level 20".toByteArray()
        val encrypted = CloudEncryption.encryptObject(key, "file:a:hash", plaintext)

        assertFalse(encrypted.contentEquals(plaintext))
        assertArrayEquals(plaintext, CloudEncryption.decryptObject(key, "file:a:hash", encrypted))
        assertThrows(Exception::class.java) {
            CloudEncryption.decryptObject(key, "file:b:hash", encrypted)
        }
    }

    @Test fun pairingSessionWrapsFolderKeyForANewDevice() {
        val folderKey = FolderKeyMaterial("folder-1", "key-1", ByteArray(32).also(SecureRandom()::nextBytes))
        val sessionKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val wrapped = PairingFolderKeyWrapper.wrap(folderKey, sessionKey)

        val unwrapped = PairingFolderKeyWrapper.unwrap(wrapped, sessionKey)

        assertArrayEquals(folderKey.bytes, unwrapped.bytes)
    }
}
