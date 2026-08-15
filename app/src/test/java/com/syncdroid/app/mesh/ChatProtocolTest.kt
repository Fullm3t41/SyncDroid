package com.syncdroid.app.mesh

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProtocolTest {
    @Test fun signedMessageVerifiesAndTrimsDraftWhitespace() {
        val signer = testSigner()

        val message = MeshChatMessage.create("group-1", "  Hello mesh  ", signer, 123)

        assertEquals("Hello mesh", message.body)
        assertTrue(message.hasValidMessageId())
        assertTrue(message.verifySignature(signer.publicKey))
    }

    @Test fun changingMessageBodyInvalidatesIdAndSignature() {
        val signer = testSigner()
        val message = MeshChatMessage.create("group-1", "Original", signer, 123)
        val changed = message.copy(body = "Changed")

        assertFalse(changed.hasValidMessageId())
        assertFalse(changed.verifySignature(signer.publicKey))
    }

    @Test fun emptyAndOversizedMessagesAreRejected() {
        val signer = testSigner()

        assertThrows(IllegalArgumentException::class.java) {
            MeshChatMessage.create("group-1", "   ", signer)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeshChatMessage.create("group-1", "x".repeat(4_001), signer)
        }
    }

    private fun testSigner(): DeviceSigner {
        val pair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
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
