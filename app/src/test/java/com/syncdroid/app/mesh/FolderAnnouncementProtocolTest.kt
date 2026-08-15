package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderAnnouncementProtocolTest {
    @Test
    fun signedAnnouncementVerifies() {
        val signer = testSigner()
        val event = FolderAnnouncement.create(
            groupId = "home",
            displayName = "Stardew Valley",
            includePatterns = listOf("*.sav"),
            excludePatterns = listOf("*.tmp"),
            signer = signer,
            version = VersionVector(mapOf(signer.deviceId to 1)),
            folderId = "folder-1",
            createdAtMillis = 1234,
        )

        assertTrue(event.hasValidEventId())
        assertTrue(event.verifySignature(signer.publicKey))
    }

    @Test
    fun metadataTamperingInvalidatesEvent() {
        val signer = testSigner()
        val event = FolderAnnouncement.create(
            groupId = "home",
            displayName = "Original",
            includePatterns = emptyList(),
            excludePatterns = emptyList(),
            signer = signer,
            version = VersionVector(),
            folderId = "folder-1",
            createdAtMillis = 1234,
        )
        val tampered = event.copy(displayName = "Different")

        assertFalse(tampered.hasValidEventId())
        assertFalse(tampered.verifySignature(signer.publicKey))
    }

    private fun testSigner(): DeviceSigner {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        return object : DeviceSigner {
            override val deviceId = deviceIdFor(pair.public)
            override val publicKey = pair.public
            override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(payload)
                sign()
            }
        }
    }
}
