package com.syncdroid.app.mesh

import com.syncdroid.app.data.SyncDroidDatabase
import com.syncdroid.shared.protocol.verifyEcdsaSha256
import java.security.SecureRandom
import java.util.Base64

class StablePeerAuthenticator(
    private val database: SyncDroidDatabase,
    private val identity: AndroidDeviceIdentity,
    private val groupId: String,
) {
    suspend fun authenticate(connection: AuthenticatedPeerConnection): PeerIdentity {
        val local = createProof()
        connection.send(StablePeerProofCodec.encode(local))
        val remote = StablePeerProofCodec.decode(connection.receive())
        require(remote.groupId == groupId) { "Peer authenticated for another mesh" }
        require(remote.tlsPublicKeyBase64 == Base64.getEncoder().encodeToString(connection.peer.publicKeyEncoded)) {
            "Peer identity proof is not bound to this TLS connection"
        }
        val member = requireNotNull(database.meshDao().getDevice(groupId, remote.deviceId)) {
            "Peer is not a member of this mesh"
        }
        require(member.trustState == "TRUSTED" && member.publicKeyBase64 == remote.publicKeyBase64) {
            "Peer mesh identity is not trusted"
        }
        require(remote.hasValidDeviceId() && remote.verify()) { "Peer identity proof is invalid" }
        return PeerIdentity(remote.deviceId, Base64.getDecoder().decode(remote.publicKeyBase64)).also {
            connection.bindAuthenticatedPeer(it)
        }
    }

    private fun createProof(): StablePeerProof {
        val nonce = ByteArray(32).also(SecureRandom()::nextBytes)
        val unsigned = StablePeerProof(
            groupId = groupId,
            deviceId = identity.deviceId,
            publicKeyBase64 = Base64.getEncoder().encodeToString(identity.publicKey.encoded),
            tlsPublicKeyBase64 = Base64.getEncoder().encodeToString(identity.tlsPublicKey.encoded),
            nonceBase64 = Base64.getEncoder().encodeToString(nonce),
            signatureBase64 = "",
        )
        return unsigned.copy(signatureBase64 = Base64.getEncoder().encodeToString(identity.sign(unsigned.payload())))
    }
}

typealias StablePeerProof = com.syncdroid.shared.protocol.StablePeerProof

fun StablePeerProof.hasValidDeviceId(): Boolean = runCatching {
    deviceIdFor(decodePublicKey(publicKeyBase64)) == deviceId
}.getOrDefault(false)

fun StablePeerProof.verify(): Boolean = runCatching { decodePublicKey(publicKeyBase64) }
    .map { verifyEcdsaSha256(it, payload(), signatureBase64) }
    .getOrDefault(false)

private object StablePeerProofCodec {
    fun encode(proof: StablePeerProof): ByteArray =
        com.syncdroid.shared.protocol.StablePeerProofWireCodec.encode(proof)

    fun decode(bytes: ByteArray): StablePeerProof =
        com.syncdroid.shared.protocol.StablePeerProofWireCodec.decode(bytes)
}
