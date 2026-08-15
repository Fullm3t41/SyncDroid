package com.syncdroid.app.mesh

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingHandshakeTest {
    @Test fun matchingSixDigitCodeAuthenticatesTheFullTranscript() {
        val inviterSigner = signer()
        val joinerSigner = signer()
        val inviter = PairingHandshake(
            PairingRole.Inviter, "invite-1", "123456", PairingIdentity.from(inviterSigner),
        )
        val joiner = PairingHandshake(
            PairingRole.Joiner, "invite-1", "123456", PairingIdentity.from(joinerSigner),
        )

        val inviterRound1 = roundTrip(inviter.createRound1())
        val joinerRound1 = roundTrip(joiner.createRound1())
        inviter.receiveRound1(joinerRound1)
        joiner.receiveRound1(inviterRound1)

        val inviterRound2 = roundTrip(inviter.createRound2())
        val joinerRound2 = roundTrip(joiner.createRound2())
        inviter.receiveRound2(joinerRound2)
        joiner.receiveRound2(inviterRound2)

        val inviterRound3 = roundTrip(inviter.createRound3())
        val joinerRound3 = roundTrip(joiner.createRound3())
        inviter.receiveRound3(joinerRound3)
        joiner.receiveRound3(inviterRound3)

        val inviterResult = inviter.finish(roundTrip(joiner.createConfirmation()))
        val joinerResult = joiner.finish(roundTrip(inviter.createConfirmation()))

        assertEquals(joinerSigner.deviceId, inviterResult.remoteIdentity.deviceId)
        assertEquals(inviterSigner.deviceId, joinerResult.remoteIdentity.deviceId)
        assertArrayEquals(inviterResult.sessionKey, joinerResult.sessionKey)
    }

    @Test fun differentCodesFailExplicitKeyConfirmation() {
        val inviter = PairingHandshake(PairingRole.Inviter, "invite-2", "111111", PairingIdentity.from(signer()))
        val joiner = PairingHandshake(PairingRole.Joiner, "invite-2", "222222", PairingIdentity.from(signer()))
        val inviterRound1 = inviter.createRound1()
        val joinerRound1 = joiner.createRound1()
        inviter.receiveRound1(joinerRound1)
        joiner.receiveRound1(inviterRound1)
        val inviterRound2 = inviter.createRound2()
        val joinerRound2 = joiner.createRound2()

        assertThrows(Exception::class.java) {
            inviter.receiveRound2(joinerRound2)
            joiner.receiveRound2(inviterRound2)
            val inviterRound3 = inviter.createRound3()
            val joinerRound3 = joiner.createRound3()
            inviter.receiveRound3(joinerRound3)
            joiner.receiveRound3(inviterRound3)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> roundTrip(value: T): T = PairingWireCodec.decode(PairingWireCodec.encode(value)) as T

    private fun signer(): DeviceSigner {
        val pair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        return object : DeviceSigner {
            override val deviceId = deviceIdFor(pair.public)
            override val publicKey = pair.public
            override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private); update(payload); sign()
            }
        }
    }
}
