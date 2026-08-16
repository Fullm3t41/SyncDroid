package com.syncdroid.shared.sync

import com.syncdroid.shared.protocol.VersionVector
import com.syncdroid.shared.protocol.FileBlock
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CrossPlatformSyncFixtureTest {
    private val fixture = loadFixture()

    @Test
    fun contentManifestMatchesGoldenHashes() {
        val input = fixture.value("block.inputHex").hexBytes()
        val manifest = ContentBlockManifestBuilder.build(input.size.toLong(), ByteArrayInputStream(input))
        assertEquals(fixture.value("block.sha256"), manifest.contentSha256)
        assertEquals(1, manifest.blocks.size)
        assertEquals(fixture.value("block.sha256"), manifest.blocks.single().sha256)

        val empty = ContentBlockManifestBuilder.build(0, ByteArrayInputStream(byteArrayOf()))
        assertEquals(fixture.value("empty.sha256"), empty.contentSha256)
        assertEquals(fixture.value("empty.sha256"), empty.blocks.single().sha256)
    }

    @Test
    fun fileDecisionsMatchGoldenActions() {
        val base = FileSyncState(false, "old", null, VersionVector(mapOf("phone_1" to 1)))
        val newerRemote = FileSyncState(false, "new", "old", VersionVector(mapOf("phone_1" to 2)))
        val newerLocal = FileSyncState(false, "local", "remote", VersionVector(mapOf("phone_1" to 3)))
        val olderRemote = FileSyncState(false, "remote", "local", VersionVector(mapOf("phone_1" to 2)))
        val concurrent = FileSyncState(false, "other", null, VersionVector(mapOf("tablet-2" to 1)))

        assertEquals(fixture.action("decision.newRemote"), decideFileSync(null, base).action)
        assertEquals(fixture.action("decision.remoteDescends"), decideFileSync(base, newerRemote).action)
        assertEquals(fixture.action("decision.localDescends"), decideFileSync(newerLocal, olderRemote).action)
        assertEquals(fixture.action("decision.concurrent"), decideFileSync(base, concurrent).action)
    }

    @Test
    fun relativePathsArePlatformNeutralAndTraversalSafe() {
        assertEquals("nested/save.sav", normalizeRelativePath("/nested\\save.sav/"))
        assertFailsWith<IllegalArgumentException> { normalizeRelativePath("../save.sav") }
    }

    @Test
    fun indexReconciliationMatchesGoldenDecisions() {
        val current = IndexStateSnapshot(7, 5, 5, 3)
        val accepted = reconcileReceivedIndex(current, 7, 5, 8, false)
        assertEquals(fixture.value("index.incremental"), accepted::class.simpleName)
        assertEquals(3, (accepted as IndexReceiveDecision.Accepted).next.contentAppliedSequence)
        assertEquals(
            fixture.value("index.gap"),
            reconcileReceivedIndex(current, 7, 4, 8, false)::class.simpleName,
        )
        val full = requireNotNull(planIndexExport(7, 10, 6, 6))
        assertEquals(fixture.value("index.exportFull"), "${full.fullIndex},${full.previousSequence},${full.lastSequence}")
        val incremental = requireNotNull(planIndexExport(7, 10, 7, 6))
        assertEquals(
            fixture.value("index.exportIncremental"),
            "${incremental.fullIndex},${incremental.previousSequence},${incremental.lastSequence}",
        )
        assertEquals(8, acknowledgeIndexContent((accepted).next, 7, 8).contentAppliedSequence)
    }

    @Test
    fun resumableProgressAndTransferIdentityMatchGoldenValues() {
        val blocks = listOf(
            FileBlock(0, 0, 1, "00".repeat(32)),
            FileBlock(1, 1, 1, "11".repeat(32)),
            FileBlock(2, 2, 1, "22".repeat(32)),
            FileBlock(3, 3, 1, "33".repeat(32)),
        )
        val progress = ResumableTransferProgress().record(0).record(3)
        assertEquals(fixture.value("resume.receivedBlocks"), progress.receivedBlocksBase64)
        assertEquals(listOf(1, 2), progress.missingBlocks(blocks))
        assertEquals(fixture.value("resume.transferId"), resumableTransferId("folder", "file", "hash"))
    }
}

private fun loadFixture(): Properties {
    val file = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("protocol/fixtures/shared-core-v1.properties") }
        .first(Files::isRegularFile)
    return Properties().apply { Files.newInputStream(file).use(::load) }
}

private fun Properties.value(key: String): String = requireNotNull(getProperty(key)) { "Missing fixture $key" }
private fun Properties.action(key: String): FileSyncAction = FileSyncAction.valueOf(value(key))
private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
