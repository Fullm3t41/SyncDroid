package com.syncdroid.app.mesh

import com.syncdroid.app.data.SyncDroidDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.security.Signature
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

data class StablePeerProof(
    val groupId: String,
    val deviceId: String,
    val publicKeyBase64: String,
    val tlsPublicKeyBase64: String,
    val nonceBase64: String,
    val signatureBase64: String,
) {
    fun payload(): ByteArray = canonicalBytes {
        string("syncdroid-tls-identity-proof-v1")
        string(groupId)
        string(deviceId)
        string(publicKeyBase64)
        string(tlsPublicKeyBase64)
        string(nonceBase64)
    }

    fun hasValidDeviceId(): Boolean = runCatching {
        deviceIdFor(decodePublicKey(publicKeyBase64)) == deviceId
    }.getOrDefault(false)

    fun verify(): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(decodePublicKey(publicKeyBase64))
            update(payload())
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)
}

private object StablePeerProofCodec {
    fun encode(proof: StablePeerProof): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            listOf(
                proof.groupId, proof.deviceId, proof.publicKeyBase64, proof.tlsPublicKeyBase64,
                proof.nonceBase64, proof.signatureBase64,
            ).forEach { value ->
                val encoded = value.toByteArray(Charsets.UTF_8)
                require(encoded.size <= MAX_FIELD_BYTES)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StablePeerProof = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC) { "Invalid stable identity proof" }
        fun field(): String {
            val size = input.readInt().also { require(it in 0..MAX_FIELD_BYTES) }
            return String(ByteArray(size).also(input::readFully), Charsets.UTF_8)
        }
        StablePeerProof(field(), field(), field(), field(), field(), field()).also {
            require(input.available() == 0)
        }
    }

    private const val MAGIC = 0x53445049
    private const val MAX_FIELD_BYTES = 16 * 1024
}
