package com.syncdroid.app.mesh

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroups
import org.bouncycastle.crypto.agreement.jpake.JPAKERound1Payload
import org.bouncycastle.crypto.agreement.jpake.JPAKERound2Payload
import org.bouncycastle.crypto.agreement.jpake.JPAKERound3Payload
import org.bouncycastle.crypto.digests.SHA256Digest

typealias PairingRole = com.syncdroid.shared.protocol.PairingRole
typealias PairingIdentity = com.syncdroid.shared.protocol.PairingIdentity
typealias PairingRound1 = com.syncdroid.shared.protocol.PairingRound1
typealias PairingRound2 = com.syncdroid.shared.protocol.PairingRound2
typealias PairingRound3 = com.syncdroid.shared.protocol.PairingRound3
typealias PairingConfirmation = com.syncdroid.shared.protocol.PairingConfirmation

fun PairingIdentity.decodePublicKey(): PublicKey = decodePublicKey(publicKeySpkiBase64)

fun com.syncdroid.shared.protocol.PairingIdentity.Companion.from(
    signer: DeviceSigner,
    displayName: String = "Device",
) = PairingIdentity(
    signer.deviceId,
    Base64.getEncoder().encodeToString(signer.publicKey.encoded),
    displayName,
)

data class PairingResult(
    val remoteIdentity: PairingIdentity,
    val sessionKey: ByteArray,
)

/**
 * J-PAKE prevents a captured exchange from becoming an offline six-digit-code oracle.
 * The final HMAC binds the PAKE key to both device public keys and every handshake round.
 */
class PairingHandshake(
    private val role: PairingRole,
    private val invitationId: String,
    pairingCode: String,
    private val localIdentity: PairingIdentity,
    random: SecureRandom = SecureRandom(),
) {
    private val participantId = participantId(role, localIdentity.deviceId, invitationId)
    private val participant = JPAKEParticipant(
        participantId,
        pairingCode.also { require(it.matches(Regex("\\d{6}"))) }.toCharArray(),
        JPAKEPrimeOrderGroups.NIST_3072,
        SHA256Digest(),
        random,
    )
    private var localRound1: PairingRound1? = null
    private var remoteRound1: PairingRound1? = null
    private var localRound2: PairingRound2? = null
    private var remoteRound2: PairingRound2? = null
    private var keyMaterial: BigInteger? = null
    private var sessionKey: ByteArray? = null

    fun createRound1(): PairingRound1 {
        check(localRound1 == null) { "Pairing round one was already created" }
        val payload = participant.createRound1PayloadToSend()
        return PairingRound1(
            invitationId,
            role,
            localIdentity,
            payload.participantId,
            payload.gx1,
            payload.gx2,
            payload.knowledgeProofForX1.toList(),
            payload.knowledgeProofForX2.toList(),
        ).also { localRound1 = it }
    }

    fun receiveRound1(value: PairingRound1) {
        check(remoteRound1 == null) { "Remote pairing round one was already received" }
        require(value.invitationId == invitationId && value.role != role) { "Pairing invitation or role mismatch" }
        require(value.identity.deviceId == deviceIdFor(value.identity.decodePublicKey())) {
            "Pairing identity does not match its public key"
        }
        require(value.participantId == participantId(value.role, value.identity.deviceId, invitationId)) {
            "Pairing participant identity is not transcript-bound"
        }
        participant.validateRound1PayloadReceived(
            JPAKERound1Payload(
                value.participantId,
                value.gx1,
                value.gx2,
                value.proofX1.toTypedArray(),
                value.proofX2.toTypedArray(),
            ),
        )
        remoteRound1 = value
    }

    fun createRound2(): PairingRound2 {
        check(remoteRound1 != null) { "Remote round one must be validated first" }
        check(localRound2 == null) { "Pairing round two was already created" }
        val payload = participant.createRound2PayloadToSend()
        return PairingRound2(
            invitationId,
            role,
            payload.participantId,
            payload.a,
            payload.knowledgeProofForX2s.toList(),
        ).also { localRound2 = it }
    }

    fun receiveRound2(value: PairingRound2) {
        val remote = requireNotNull(remoteRound1) { "Remote round one must be validated first" }
        check(remoteRound2 == null) { "Remote pairing round two was already received" }
        require(value.invitationId == invitationId && value.role == remote.role) { "Pairing round two mismatch" }
        require(value.participantId == remote.participantId) { "Pairing participant changed identity" }
        participant.validateRound2PayloadReceived(
            JPAKERound2Payload(value.participantId, value.a, value.proofX2s.toTypedArray()),
        )
        remoteRound2 = value
    }

    fun createRound3(): PairingRound3 {
        val material = ensureSessionKey()
        val payload = participant.createRound3PayloadToSend(material)
        return PairingRound3(invitationId, role, payload.participantId, payload.macTag)
    }

    fun receiveRound3(value: PairingRound3) {
        val remote = requireNotNull(remoteRound1) { "Remote identity is unavailable" }
        require(value.invitationId == invitationId && value.role == remote.role) { "Pairing round three mismatch" }
        require(value.participantId == remote.participantId) { "Pairing participant changed identity" }
        participant.validateRound3PayloadReceived(
            JPAKERound3Payload(value.participantId, value.macTag),
            requireNotNull(keyMaterial) { "Local pairing key has not been calculated" },
        )
    }

    fun createConfirmation(): PairingConfirmation = PairingConfirmation(
        invitationId,
        role,
        transcriptMac(requireNotNull(sessionKey) { "Pairing key is unavailable" }, role),
    )

    fun finish(remoteConfirmation: PairingConfirmation): PairingResult {
        val remote = requireNotNull(remoteRound1) { "Remote identity is unavailable" }
        require(remoteConfirmation.invitationId == invitationId && remoteConfirmation.role == remote.role) {
            "Pairing confirmation mismatch"
        }
        val expected = transcriptMac(requireNotNull(sessionKey), remote.role)
        require(MessageDigest.isEqual(expected, remoteConfirmation.hmacSha256)) {
            "Pairing transcript authentication failed"
        }
        return PairingResult(remote.identity, requireNotNull(sessionKey).copyOf())
    }

    private fun ensureSessionKey(): BigInteger {
        keyMaterial?.let { return it }
        check(localRound2 != null && remoteRound2 != null) { "Both round-two messages are required" }
        val material = participant.calculateKeyingMaterial()
        keyMaterial = material
        val transcriptHash = sha256(transcript())
        sessionKey = hkdfSha256(
            inputKeyMaterial = material.unsignedBytes(),
            salt = sha256("syncdroid-jpake-v1:$invitationId".toByteArray(StandardCharsets.UTF_8)),
            info = transcriptHash,
            length = 32,
        )
        return material
    }

    private fun transcriptMac(key: ByteArray, confirmingRole: PairingRole): ByteArray = Mac
        .getInstance("HmacSHA256")
        .run {
            init(SecretKeySpec(key, "HmacSHA256"))
            update("syncdroid-pairing-confirm-v1".toByteArray(StandardCharsets.UTF_8))
            update(confirmingRole.name.toByteArray(StandardCharsets.UTF_8))
            doFinal(transcript())
        }

    private fun transcript(): ByteArray {
        val round1 = listOf(requireNotNull(localRound1), requireNotNull(remoteRound1)).sortedBy { it.role.ordinal }
        val round2 = listOf(requireNotNull(localRound2), requireNotNull(remoteRound2)).sortedBy { it.role.ordinal }
        return canonicalBytes {
            string("syncdroid-pairing-transcript-v1")
            string(invitationId)
            round1.forEach { value ->
                string(value.role.name)
                string(value.identity.deviceId)
                string(value.identity.publicKeySpkiBase64)
                string(value.identity.displayName)
                string(value.participantId)
                string(value.gx1.toString(16))
                string(value.gx2.toString(16))
                strings(value.proofX1.map { it.toString(16) })
                strings(value.proofX2.map { it.toString(16) })
            }
            round2.forEach { value ->
                string(value.role.name)
                string(value.participantId)
                string(value.a.toString(16))
                strings(value.proofX2s.map { it.toString(16) })
            }
        }
    }
}

object PairingWireCodec {
    fun encode(value: Any): ByteArray = com.syncdroid.shared.protocol.PairingWireCodec.encode(value)
    fun decode(bytes: ByteArray): Any = com.syncdroid.shared.protocol.PairingWireCodec.decode(bytes)
}

class PairingConnectionProtocol(
    private val connection: AuthenticatedPeerConnection,
    private val handshake: PairingHandshake,
) {
    suspend fun run(): PairingResult {
        val localRound1 = handshake.createRound1()
        connection.send(PairingWireCodec.encode(localRound1))
        handshake.receiveRound1(connection.receive().decodeAs())

        val localRound2 = handshake.createRound2()
        connection.send(PairingWireCodec.encode(localRound2))
        handshake.receiveRound2(connection.receive().decodeAs())

        val localRound3 = handshake.createRound3()
        connection.send(PairingWireCodec.encode(localRound3))
        handshake.receiveRound3(connection.receive().decodeAs())

        connection.send(PairingWireCodec.encode(handshake.createConfirmation()))
        return handshake.finish(connection.receive().decodeAs())
    }

    private inline fun <reified T> ByteArray.decodeAs(): T = PairingWireCodec.decode(this) as? T
        ?: error("Unexpected pairing message order")
}

private fun participantId(role: PairingRole, deviceId: String, invitationId: String): String =
    "${role.name.lowercase()}:$deviceId:$invitationId"

private fun BigInteger.unsignedBytes(): ByteArray = toByteArray().let { bytes ->
    if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}

private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

private fun hkdfSha256(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    val extract = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(salt, "HmacSHA256"))
        doFinal(inputKeyMaterial)
    }
    val output = ByteArrayOutputStream()
    var previous = ByteArray(0)
    var counter = 1
    while (output.size() < length) {
        previous = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(extract, "HmacSHA256"))
            update(previous)
            update(info)
            update(counter.toByte())
            doFinal()
        }
        output.write(previous)
        counter++
    }
    return output.toByteArray().copyOf(length)
}
