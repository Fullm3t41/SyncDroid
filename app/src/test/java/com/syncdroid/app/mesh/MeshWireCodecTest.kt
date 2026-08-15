package com.syncdroid.app.mesh

import com.syncdroid.app.sync.VersionVector
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MeshWireCodecTest {
    @Test fun bundleRoundTripsWithoutChangingSignedPayloads() {
        val signer = testSigner()
        val membership = MembershipEvent.createAddDevice(
            "group-1", "Téléphone 🌏", signer.publicKey, signer, emptyList(),
            VersionVector().increment(signer.deviceId), 100,
        )
        val folder = FolderAnnouncement.create(
            groupId = "group-1",
            displayName = "Game saves",
            includePatterns = listOf("*.sav"),
            excludePatterns = listOf("*.tmp"),
            signer = signer,
            version = VersionVector().increment(signer.deviceId),
            folderId = "folder-1",
            createdAtMillis = 200,
        )
        val chat = MeshChatMessage.create("group-1", "Hello from the mesh 👋", signer, 300)

        val encoded = MeshWireCodec.encode(
            MeshStateBundle("My mesh 🏠", listOf(membership), listOf(folder), chatMessages = listOf(chat)),
        )
        val decoded = MeshWireCodec.decode(encoded)

        assertEquals("SDMB", encoded.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(membership, decoded.membershipEvents.single())
        assertEquals(folder, decoded.folderAnnouncements.single())
        assertEquals(chat, decoded.chatMessages.single())
        assertEquals("My mesh 🏠", decoded.groupName)
        assertTrue(decoded.membershipEvents.single().verifySignature(signer.publicKey))
        assertTrue(decoded.chatMessages.single().verifySignature(signer.publicKey))
    }

    @Test fun decoderRetainsLegacyV1BundleCompatibility() {
        val signer = testSigner()
        val membership = legacyMembershipEvent(signer)
        val legacy = encodeLegacy("Legacy mesh", listOf(membership))

        val decoded = MeshWireCodec.decode(legacy)

        assertEquals("Legacy mesh", decoded.groupName)
        assertEquals(membership, decoded.membershipEvents.single())
        assertTrue(decoded.membershipEvents.single().verifySignature(signer.publicKey))
    }

    @Test fun decoderRejectsUnknownProtocol() {
        assertThrows(IllegalArgumentException::class.java) { MeshWireCodec.decode(byteArrayOf(0, 1)) }
    }

    @Test fun decoderRejectsUnknownMajorVersion() {
        val encoded = MeshWireCodec.encode(MeshStateBundle("Mesh", emptyList(), emptyList()))
        encoded[5] = 3
        assertThrows(IllegalArgumentException::class.java) { MeshWireCodec.decode(encoded) }
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

    private fun encodeLegacy(groupName: String, memberships: List<MembershipEvent>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF("syncdroid-mesh-state-v1")
                output.writeUTF(groupName)
                output.writeInt(memberships.size)
                memberships.forEach { event ->
                    output.writeUTF(event.eventId)
                    output.writeUTF(event.groupId)
                    output.writeUTF(event.eventType.name)
                    output.writeUTF(event.subjectDeviceId)
                    output.writeUTF(event.subjectDisplayName)
                    output.writeUTF(event.subjectPublicKeyBase64)
                    output.writeUTF(event.signerDeviceId)
                    output.writeInt(event.parentEventIds.size)
                    event.parentEventIds.forEach(output::writeUTF)
                    output.writeUTF(event.version.toJson())
                    output.writeLong(event.createdAtMillis)
                    output.writeUTF(event.signatureBase64)
                }
                output.writeInt(0)
            }
            bytes.toByteArray()
        }

    private fun legacyMembershipEvent(signer: DeviceSigner): MembershipEvent {
        val publicKey = Base64.getEncoder().encodeToString(signer.publicKey.encoded)
        val version = VersionVector(mapOf(signer.deviceId to 1))
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF("syncdroid-membership-v1")
                output.writeUTF("legacy-group")
                output.writeUTF(MembershipEventType.AddDevice.name)
                output.writeUTF(signer.deviceId)
                output.writeUTF("Old Android")
                output.writeUTF(publicKey)
                output.writeUTF(signer.deviceId)
                output.writeLong(100)
                output.writeUTF(version.toJson())
                output.writeInt(0)
            }
            bytes.toByteArray()
        }
        return MembershipEvent(
            eventId = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(payload),
            ),
            groupId = "legacy-group",
            eventType = MembershipEventType.AddDevice,
            subjectDeviceId = signer.deviceId,
            subjectDisplayName = "Old Android",
            subjectPublicKeyBase64 = publicKey,
            signerDeviceId = signer.deviceId,
            parentEventIds = emptyList(),
            version = version,
            createdAtMillis = 100,
            signatureBase64 = Base64.getEncoder().encodeToString(signer.sign(payload)),
        )
    }
}
