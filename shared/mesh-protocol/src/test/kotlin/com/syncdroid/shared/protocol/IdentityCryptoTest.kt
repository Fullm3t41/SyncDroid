package com.syncdroid.shared.protocol

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentityCryptoTest {
    @Test
    fun identityDerivationDecodeAndSignatureVerificationAreShared() {
        val pair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val encoded = Base64.getEncoder().encodeToString(pair.public.encoded)
        val decoded = decodeEcPublicKeyBase64(encoded)
        assertEquals(deviceIdForPublicKey(pair.public), deviceIdForPublicKey(decoded))
        assertEquals(fingerprintForPublicKey(pair.public), fingerprintForPublicKey(decoded))

        val payload = "shared-signature".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(payload)
            Base64.getEncoder().encodeToString(sign())
        }
        assertTrue(verifyEcdsaSha256(decoded, payload, signature))
        assertFalse(verifyEcdsaSha256(decoded, "tampered".toByteArray(), signature))
    }
}
