package com.synctosh.app.mesh

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MacDeviceIdentityTest {
    @Test
    fun fileIdentityPersistsWithOwnerOnlyPermissions() {
        val path = Files.createTempDirectory("synctosh-file-identity-test").resolve("identity.p12")
        val first = MacDeviceIdentity("file-test", path, legacyKeyStoreFactory = null)
        val deviceId = first.deviceId

        val second = MacDeviceIdentity("file-test", path) {
            error("Keychain must not be read after the identity file exists")
        }

        assertEquals(deviceId, second.deviceId)
        assertTrue(Files.isRegularFile(path))
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(path),
        )
    }

    @Test
    fun keychainMigrationPreservesTheExistingDeviceIdentity() {
        val directory = Files.createTempDirectory("synctosh-keychain-migration-test")
        val alias = "migration-test"
        val source = MacDeviceIdentity(alias, directory.resolve("source.p12"), legacyKeyStoreFactory = null)
        val legacyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(
                alias,
                source.privateKey(),
                MacDeviceIdentity.KEYCHAIN_PASSWORD,
                arrayOf(source.certificate),
            )
        }
        val migratedPath = directory.resolve("migrated.p12")

        val migrated = MacDeviceIdentity(alias, migratedPath) { legacyStore }
        assertEquals(source.deviceId, migrated.deviceId)
        assertTrue(Files.isRegularFile(migratedPath))

        val reloaded = MacDeviceIdentity(alias, migratedPath) {
            error("The migrated identity must no longer require Keychain")
        }
        assertEquals(source.deviceId, reloaded.deviceId)
    }

    @Test
    fun corruptIdentityFileIsNeverSilentlyReplaced() {
        val path = Files.createTempDirectory("synctosh-corrupt-identity-test").resolve("identity.p12")
        val corruptBytes = byteArrayOf(1, 2, 3, 4, 5)
        Files.write(path, corruptBytes)

        assertFails {
            MacDeviceIdentity("corrupt-test", path) {
                error("A corrupt file must not fall back to Keychain")
            }.deviceId
        }
        assertTrue(Files.readAllBytes(path).contentEquals(corruptBytes))
    }
}
