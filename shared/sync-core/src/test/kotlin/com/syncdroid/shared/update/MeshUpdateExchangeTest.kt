package com.syncdroid.shared.update

import com.syncdroid.shared.protocol.MeshSessionMessage
import com.syncdroid.shared.protocol.UpdateAssetDescriptor
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

class MeshUpdateExchangeTest {
    @Test
    fun trustedPeersTransferTheRequestedAssetInChunks() = runBlocking {
        val descriptor = UpdateAssetDescriptor(
            releaseVersion = "0.2.0",
            platformId = "android",
            fileName = "SyncDroid-Mesh-0.2.0-Android.apk",
            sha256 = "b".repeat(64),
            sizeBytes = 5,
        )
        val source = FakeCache(descriptor, byteArrayOf(1, 2, 3, 4, 5), wantsAsset = false)
        val target = FakeCache(descriptor, byteArrayOf(), wantsAsset = true)
        val sourceToTarget = Channel<MeshSessionMessage>(Channel.UNLIMITED)
        val targetToSource = Channel<MeshSessionMessage>(Channel.UNLIMITED)

        val sourceJob = async {
            MeshUpdateExchange(source).run(
                localDeviceId = "device-b",
                remoteDeviceId = "device-a",
                send = sourceToTarget::send,
                receive = targetToSource::receive,
            )
        }
        val targetJob = async {
            MeshUpdateExchange(target).run(
                localDeviceId = "device-a",
                remoteDeviceId = "device-b",
                send = targetToSource::send,
                receive = sourceToTarget::receive,
            )
        }
        sourceJob.await()
        targetJob.await()

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), target.bytes)
    }

    private class FakeCache(
        private val descriptor: UpdateAssetDescriptor,
        initialBytes: ByteArray,
        private val wantsAsset: Boolean,
    ) : MeshUpdateCache {
        var bytes = initialBytes
            private set

        override fun availableAssets(): List<UpdateAssetDescriptor> =
            if (!wantsAsset && bytes.size.toLong() == descriptor.sizeBytes) listOf(descriptor) else emptyList()

        override fun desiredAsset(): UpdateAssetDescriptor? = descriptor.takeIf { wantsAsset && bytes.size.toLong() < descriptor.sizeBytes }
        override fun partialSize(sha256: String): Long = bytes.size.toLong()

        override suspend fun readChunk(sha256: String, offset: Long, maxBytes: Int): ByteArray =
            bytes.copyOfRange(offset.toInt(), (offset + maxBytes).coerceAtMost(bytes.size.toLong()).toInt())

        override suspend fun writeChunk(asset: UpdateAssetDescriptor, offset: Long, bytes: ByteArray) {
            require(offset == this.bytes.size.toLong())
            this.bytes += bytes
        }
    }
}
