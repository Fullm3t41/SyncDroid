package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MembershipProtocolTest {
    @Test fun trustedDeviceCanCreateVerifiableAddition() {
        val inviter = TestSigner(keyPair())
        val newDevice = keyPair()
        val event = MembershipEvent.createAddDevice(
            groupId = "group_1",
            subjectDisplayName = "Tablet",
            subjectPublicKey = newDevice.public,
            signer = inviter,
            parentEventIds = listOf("parent_b", "parent_a"),
            version = VersionVector(mapOf(inviter.deviceId to 2)),
            createdAtMillis = 1234,
        )

        assertTrue(event.hasValidEventId())
        assertTrue(event.hasValidSubjectId())
        assertTrue(event.verifySignature(inviter.publicKey))
    }

    @Test fun tamperedAdditionFailsSignatureValidation() {
        val inviter = TestSigner(keyPair())
        val event = MembershipEvent.createAddDevice(
            "group_1", "Tablet", keyPair().public, inviter, emptyList(), VersionVector(), 1234,
        )
        assertFalse(event.copy(subjectDisplayName = "Attacker").verifySignature(inviter.publicKey))
    }

    @Test fun deviceNicknameUpdateIsSelfSignedAndTranscriptBound() {
        val device = TestSigner(keyPair())
        val event = MembershipEvent.createDeviceNameUpdate(
            "group_1",
            "James's Fold",
            device,
            listOf("parent"),
            VersionVector(mapOf(device.deviceId to 2)),
            5678,
        )

        assertTrue(event.eventType == MembershipEventType.UpdateDeviceName)
        assertTrue(event.subjectDeviceId == device.deviceId)
        assertTrue(event.hasValidEventId())
        assertTrue(event.verifySignature(device.publicKey))
        assertFalse(event.copy(subjectDisplayName = "Changed").verifySignature(device.publicKey))
    }

    @Test fun trustedDeviceCanCreateSignedRemovalForAnotherMember() {
        val remover = TestSigner(keyPair())
        val removed = keyPair()
        val event = MembershipEvent.createRemoveDevice(
            groupId = "group_1",
            subjectDisplayName = "Old tablet",
            subjectPublicKey = removed.public,
            signer = remover,
            parentEventIds = listOf("membership-head"),
            version = VersionVector(mapOf(remover.deviceId to 3)),
            createdAtMillis = 6789,
        )

        assertTrue(event.eventType == MembershipEventType.RemoveDevice)
        assertTrue(event.subjectDeviceId == deviceIdFor(removed.public))
        assertTrue(event.hasValidEventId())
        assertTrue(event.verifySignature(remover.publicKey))
        assertFalse(event.copy(subjectDisplayName = "Different device").verifySignature(remover.publicKey))
    }

    @Test fun deviceCanSignItsOwnLeaveEvent() {
        val device = TestSigner(keyPair())
        val event = MembershipEvent.createRemoveDevice(
            "group_1",
            "Leaving phone",
            device.publicKey,
            device,
            emptyList(),
            VersionVector(mapOf(device.deviceId to 4)),
            7890,
        )

        assertTrue(event.subjectDeviceId == event.signerDeviceId)
        assertTrue(event.verifySignature(device.publicKey))
    }

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private class TestSigner(private val keys: KeyPair) : DeviceSigner {
        override val publicKey = keys.public
        override val deviceId = deviceIdFor(publicKey)
        override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(payload)
            sign()
        }
    }
}
