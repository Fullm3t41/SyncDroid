package com.syncdroid.shared.protocol

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class WireCodecGoldenTest {
    private val fixture = loadWireFixture()

    @Test
    fun pairingMessagesMatchInstalledProtocolBytes() {
        val messages = listOf(
            "pairing.round1" to PairingRound1(
                "invite-1", PairingRole.Inviter, PairingIdentity("phone-1", "AQID", "Fold 5"),
                "inviter:phone-1:invite-1", BigInteger.ONE, BigInteger("128"),
                listOf(BigInteger.TWO, BigInteger.valueOf(3)), listOf(BigInteger.valueOf(4), BigInteger.valueOf(5)),
            ),
            "pairing.round2" to PairingRound2(
                "invite-1", PairingRole.Joiner, "joiner:mac-1:invite-1", BigInteger.valueOf(6),
                listOf(BigInteger.valueOf(7), BigInteger.valueOf(8)),
            ),
            "pairing.round3" to PairingRound3(
                "invite-1", PairingRole.Inviter, "inviter:phone-1:invite-1", BigInteger.valueOf(9),
            ),
            "pairing.confirmation" to PairingConfirmation(
                "invite-1", PairingRole.Joiner, byteArrayOf(0, 1, 2, 0xff.toByte()),
            ),
        )
        messages.forEach { (key, message) ->
            assertGolden(key, PairingWireCodec.encode(message))
            assertGolden(key, PairingWireCodec.encode(PairingWireCodec.decode(fixture.value(key).hexToBytes())))
        }
    }

    @Test
    fun sessionAndIndexMessagesMatchInstalledProtocolBytes() {
        val record = IndexedFileRecord(
            "save/main.sav", "file-1", 3, 42, "ab".repeat(32), "cd".repeat(32), "phone-a", false,
            VersionVector(mapOf("phone-a" to 2)), 9, 131072,
            listOf(FileBlock(0, 0, 3, "ef".repeat(32))),
        )
        val messages = listOf(
            "session.metadata" to MeshSessionMessage.Metadata(byteArrayOf(1, 2, 3)),
            "session.catalog" to MeshSessionMessage.Catalog(listOf(FolderClock("folder-1", 11, 12, 13, 14, 15))),
            "session.indexBatch" to MeshSessionMessage.IndexBatch(
                listOf(FolderIndexUpdate("folder-1", 11, 8, 9, false, listOf(record))),
            ),
            "session.transferPlan" to MeshSessionMessage.TransferPlan(2),
            "session.phaseDone" to MeshSessionMessage.PhaseDone,
            "session.error" to MeshSessionMessage.Error("retry"),
        )
        messages.forEach { (key, message) ->
            assertGolden(key, MeshSessionWireCodec.encode(message))
            assertGolden(key, MeshSessionWireCodec.encode(MeshSessionWireCodec.decode(fixture.value(key).hexToBytes())))
        }
    }

    @Test
    fun transferMessagesMatchInstalledProtocolBytes() {
        val messages = listOf(
            "transfer.wholeRequest" to FileTransferMessage.WholeFileRequest(
                "folder-1", "file-1", "save/main.sav", "ab".repeat(32),
            ),
            "transfer.fileStart" to FileTransferMessage.FileStart(3, 42),
            "transfer.fileChunk" to FileTransferMessage.FileChunk(7, byteArrayOf(1, 2, 3)),
            "transfer.fileEnd" to FileTransferMessage.FileEnd("ab".repeat(32)),
            "transfer.blockRequest" to FileTransferMessage.BlockRequest(
                "folder-1", "file-1", "save/main.sav", "ab".repeat(32), 4,
            ),
            "transfer.blockResponse" to FileTransferMessage.BlockResponse(4, byteArrayOf(4, 5, 6)),
            "transfer.error" to FileTransferMessage.Error("missing"),
        )
        messages.forEach { (key, message) ->
            assertGolden(key, FileTransferWireCodec.encode(message))
            assertGolden(key, FileTransferWireCodec.encode(FileTransferWireCodec.decode(fixture.value(key).hexToBytes())))
        }
    }

    private fun assertGolden(key: String, bytes: ByteArray) = assertEquals(fixture.value(key), bytes.toHex(), key)
}

private fun loadWireFixture(): Properties {
    val file = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("protocol/fixtures/wire-codecs-v1.properties") }
        .first(Files::isRegularFile)
    return Properties().apply { Files.newInputStream(file).use(::load) }
}

private fun Properties.value(key: String): String = requireNotNull(getProperty(key)) { "Missing fixture $key" }
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
