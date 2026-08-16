package com.syncdows.app.mesh

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PeerTransportTest {
    @Test
    fun mutuallyAuthenticatedTlsCarriesJpakePairing() = runBlocking {
        val inviterIdentity = memoryIdentity("inviter")
        val joinerIdentity = memoryIdentity("joiner")
        val invitationId = "transport-test-invitation"
        val serverResult = CompletableDeferred<PairingResult>()
        val server = MeshPeerServer(DeviceTlsContext(inviterIdentity, allowUnknownPeer = true)) { connection ->
            val handshake = PairingHandshake(
                PairingRole.Inviter,
                invitationId,
                "482913",
                PairingIdentity.from(inviterIdentity, "Mac"),
            )
            serverResult.complete(PairingConnectionProtocol(connection, handshake).run())
        }

        try {
            val port = server.start()
            val joinerResult = MeshPeerClient(DeviceTlsContext(joinerIdentity, allowUnknownPeer = true))
                .connect(InetAddress.getLoopbackAddress(), port)
                .use { connection ->
                    assertContentEquals(inviterIdentity.publicKey.encoded, connection.peerTlsIdentity.publicKeySpki)
                    PairingConnectionProtocol(
                        connection,
                        PairingHandshake(
                            PairingRole.Joiner,
                            invitationId,
                            "482913",
                            PairingIdentity.from(joinerIdentity, "Android"),
                        ),
                    ).run()
                }
            val inviterResult = withTimeout(10_000) { serverResult.await() }
            assertEquals(inviterIdentity.deviceId, joinerResult.remoteIdentity.deviceId)
            assertEquals(joinerIdentity.deviceId, inviterResult.remoteIdentity.deviceId)
            assertContentEquals(inviterResult.sessionKey, joinerResult.sessionKey)
        } finally {
            server.close()
        }
    }

    private fun memoryIdentity(alias: String): WindowsDeviceIdentity {
        val path = java.nio.file.Files.createTempDirectory("syncdows-peer-identity").resolve("identity.p12")
        return WindowsDeviceIdentity(alias, path, legacyKeyStoreFactory = null)
    }
}
