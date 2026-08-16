package com.syncdroid.shared.protocol

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class RemainingWireGoldenTest {
    private val fixture = loadRemainingFixture()
    private val vector = VersionVector(mapOf("phone-a" to 2))
    private val exception = createSignedSyncExceptionEvent(
        "group-1", "folder-1", "save\\main.sav", true, "phone-a", vector, 42, { byteArrayOf(1, 2, 3) },
    )

    @Test
    fun meshBundleAndSignedExceptionMatchGoldenValues() {
        val bundle = MeshStateBundleWire(
            "Home",
            listOf(WireMembershipEvent("member-event", "group-1", "AddDevice", "phone-a", "Fold", "AQID", "phone-a", emptyList(), vector, 10, "sig-a")),
            listOf(WireFolderAnnouncement("folder-event", "group-1", "folder-1", "Saves", listOf("*.sav"), listOf("*.tmp"), "phone-a", vector, 20, "sig-b")),
            listOf(exception),
            listOf(WireChatMessage("chat-event", "group-1", "phone-a", "Hello", 30, "sig-c")),
        )
        assertGolden("mesh.bundle", MeshBundleWireCodec.encode(bundle))
        assertGolden(
            "mesh.bundle",
            MeshBundleWireCodec.encode(MeshBundleWireCodec.decode(fixture.required("mesh.bundle").hexToBytes())),
        )
        assertGolden("exception.payload", exception.canonicalPayload())
        assertEquals(fixture.required("exception.eventId"), exception.eventId)
        assertEquals("save/main.sav", exception.relativePath)
        assertEquals(true, exception.hasValidEventId())
    }

    @Test
    fun pairingCompletionAndStableProofMatchGoldenValues() {
        val completion = PairingCompletionMessage.Complete(
            "group-1", "Home", byteArrayOf(4, 5),
            listOf(WrappedFolderKeyTransfer("folder-1", "key-1", byteArrayOf(6, 7), byteArrayOf(8, 9, 10))),
        )
        val proof = StablePeerProof("group-1", "phone-a", "AQID", "BAUG", "BwgJ", "sig")
        assertGolden("pairing.completion", PairingCompletionWireCodec.encode(completion))
        assertGolden("pairing.ack", PairingCompletionWireCodec.encode(PairingCompletionMessage.Ack))
        assertGolden("session.peerProof", StablePeerProofWireCodec.encode(proof))
        assertGolden(
            "pairing.completion",
            PairingCompletionWireCodec.encode(
                PairingCompletionWireCodec.decode(fixture.required("pairing.completion").hexToBytes()),
            ),
        )
        assertGolden(
            "session.peerProof",
            StablePeerProofWireCodec.encode(StablePeerProofWireCodec.decode(fixture.required("session.peerProof").hexToBytes())),
        )
    }

    private fun assertGolden(key: String, bytes: ByteArray) = assertEquals(fixture.required(key), bytes.hex(), key)
}

private fun loadRemainingFixture(): Properties {
    val file = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("protocol/fixtures/wire-codecs-v1.properties") }
        .first(Files::isRegularFile)
    return Properties().apply { Files.newInputStream(file).use(::load) }
}

private fun Properties.required(key: String): String = requireNotNull(getProperty(key))
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}
