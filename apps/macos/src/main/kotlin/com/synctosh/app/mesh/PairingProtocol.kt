package com.synctosh.app.mesh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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

enum class PairingRole { Inviter, Joiner }

data class PairingIdentity(
    val deviceId: String,
    val publicKeySpkiBase64: String,
    val displayName: String,
) {
    fun decodePublicKey(): PublicKey = decodePublicKey(publicKeySpkiBase64)

    companion object {
        fun from(signer: DeviceSigner, displayName: String) = PairingIdentity(
            signer.deviceId,
            Base64.getEncoder().encodeToString(signer.publicKey.encoded),
            displayName,
        )
    }
}

data class PairingRound1(
    val invitationId: String,
    val role: PairingRole,
    val identity: PairingIdentity,
    val participantId: String,
    val gx1: BigInteger,
    val gx2: BigInteger,
    val proofX1: List<BigInteger>,
    val proofX2: List<BigInteger>,
)

data class PairingRound2(
    val invitationId: String,
    val role: PairingRole,
    val participantId: String,
    val a: BigInteger,
    val proofX2s: List<BigInteger>,
)

data class PairingRound3(
    val invitationId: String,
    val role: PairingRole,
    val participantId: String,
    val macTag: BigInteger,
)

data class PairingConfirmation(val invitationId: String, val role: PairingRole, val hmacSha256: ByteArray)
data class PairingResult(val remoteIdentity: PairingIdentity, val sessionKey: ByteArray)

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
        check(localRound1 == null)
        val value = participant.createRound1PayloadToSend()
        return PairingRound1(
            invitationId, role, localIdentity, value.participantId, value.gx1, value.gx2,
            value.knowledgeProofForX1.toList(), value.knowledgeProofForX2.toList(),
        ).also { localRound1 = it }
    }

    fun receiveRound1(value: PairingRound1) {
        check(remoteRound1 == null)
        require(value.invitationId == invitationId && value.role != role)
        require(value.identity.deviceId == deviceIdFor(value.identity.decodePublicKey()))
        require(value.participantId == participantId(value.role, value.identity.deviceId, invitationId))
        participant.validateRound1PayloadReceived(
            JPAKERound1Payload(
                value.participantId, value.gx1, value.gx2,
                value.proofX1.toTypedArray(), value.proofX2.toTypedArray(),
            ),
        )
        remoteRound1 = value
    }

    fun createRound2(): PairingRound2 {
        check(remoteRound1 != null && localRound2 == null)
        val value = participant.createRound2PayloadToSend()
        return PairingRound2(invitationId, role, value.participantId, value.a, value.knowledgeProofForX2s.toList())
            .also { localRound2 = it }
    }

    fun receiveRound2(value: PairingRound2) {
        val remote = requireNotNull(remoteRound1)
        check(remoteRound2 == null)
        require(value.invitationId == invitationId && value.role == remote.role && value.participantId == remote.participantId)
        participant.validateRound2PayloadReceived(
            JPAKERound2Payload(value.participantId, value.a, value.proofX2s.toTypedArray()),
        )
        remoteRound2 = value
    }

    fun createRound3(): PairingRound3 {
        val material = ensureSessionKey()
        val value = participant.createRound3PayloadToSend(material)
        return PairingRound3(invitationId, role, value.participantId, value.macTag)
    }

    fun receiveRound3(value: PairingRound3) {
        val remote = requireNotNull(remoteRound1)
        require(value.invitationId == invitationId && value.role == remote.role && value.participantId == remote.participantId)
        participant.validateRound3PayloadReceived(
            JPAKERound3Payload(value.participantId, value.macTag),
            requireNotNull(keyMaterial),
        )
    }

    fun createConfirmation() = PairingConfirmation(
        invitationId,
        role,
        transcriptMac(requireNotNull(sessionKey), role),
    )

    fun finish(remoteConfirmation: PairingConfirmation): PairingResult {
        val remote = requireNotNull(remoteRound1)
        require(remoteConfirmation.invitationId == invitationId && remoteConfirmation.role == remote.role)
        require(MessageDigest.isEqual(transcriptMac(requireNotNull(sessionKey), remote.role), remoteConfirmation.hmacSha256)) {
            "Pairing transcript authentication failed"
        }
        return PairingResult(remote.identity, requireNotNull(sessionKey).copyOf())
    }

    private fun ensureSessionKey(): BigInteger {
        keyMaterial?.let { return it }
        check(localRound2 != null && remoteRound2 != null)
        val material = participant.calculateKeyingMaterial()
        keyMaterial = material
        sessionKey = hkdfSha256(
            material.unsignedBytes(),
            sha256("syncdroid-jpake-v1:$invitationId".toByteArray(StandardCharsets.UTF_8)),
            sha256(transcript()),
            32,
        )
        return material
    }

    private fun transcriptMac(key: ByteArray, confirmingRole: PairingRole) = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        update("syncdroid-pairing-confirm-v1".toByteArray(StandardCharsets.UTF_8))
        update(confirmingRole.name.toByteArray(StandardCharsets.UTF_8))
        doFinal(transcript())
    }

    private fun transcript(): ByteArray {
        val rounds1 = listOf(requireNotNull(localRound1), requireNotNull(remoteRound1)).sortedBy { it.role.ordinal }
        val rounds2 = listOf(requireNotNull(localRound2), requireNotNull(remoteRound2)).sortedBy { it.role.ordinal }
        return canonicalBytes {
            string("syncdroid-pairing-transcript-v1")
            string(invitationId)
            rounds1.forEach {
                string(it.role.name); string(it.identity.deviceId); string(it.identity.publicKeySpkiBase64)
                string(it.identity.displayName); string(it.participantId); string(it.gx1.toString(16)); string(it.gx2.toString(16))
                strings(it.proofX1.map { number -> number.toString(16) })
                strings(it.proofX2.map { number -> number.toString(16) })
            }
            rounds2.forEach {
                string(it.role.name); string(it.participantId); string(it.a.toString(16))
                strings(it.proofX2s.map { number -> number.toString(16) })
            }
        }
    }
}

object PairingWireCodec {
    fun encode(value: Any): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write(MAGIC)
            when (value) {
                is PairingRound1 -> output.writeRound1(value)
                is PairingRound2 -> output.writeRound2(value)
                is PairingRound3 -> output.writeRound3(value)
                is PairingConfirmation -> output.writeConfirmation(value)
                else -> error("Unsupported pairing message")
            }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Any = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(ByteArray(MAGIC.size).also(input::readFully).contentEquals(MAGIC))
        val value = when (input.readUnsignedByte()) {
            TYPE_ROUND_1 -> input.readRound1()
            TYPE_ROUND_2 -> input.readRound2()
            TYPE_ROUND_3 -> input.readRound3()
            TYPE_CONFIRMATION -> input.readConfirmation()
            else -> error("Unknown pairing message")
        }
        require(input.available() == 0)
        value
    }

    private fun DataOutputStream.writeRound1(value: PairingRound1) {
        writeByte(TYPE_ROUND_1); writeString(value.invitationId); writeByte(value.role.ordinal)
        writeString(value.identity.deviceId); writeString(value.identity.publicKeySpkiBase64); writeString(value.identity.displayName)
        writeString(value.participantId); writeBigInt(value.gx1); writeBigInt(value.gx2)
        writeBigInts(value.proofX1); writeBigInts(value.proofX2)
    }
    private fun DataInputStream.readRound1() = PairingRound1(
        readString(), readRole(), PairingIdentity(readString(), readString(), readString()), readString(),
        readBigInt(), readBigInt(), readBigInts(), readBigInts(),
    )
    private fun DataOutputStream.writeRound2(value: PairingRound2) {
        writeByte(TYPE_ROUND_2); writeString(value.invitationId); writeByte(value.role.ordinal)
        writeString(value.participantId); writeBigInt(value.a); writeBigInts(value.proofX2s)
    }
    private fun DataInputStream.readRound2() = PairingRound2(readString(), readRole(), readString(), readBigInt(), readBigInts())
    private fun DataOutputStream.writeRound3(value: PairingRound3) {
        writeByte(TYPE_ROUND_3); writeString(value.invitationId); writeByte(value.role.ordinal)
        writeString(value.participantId); writeBigInt(value.macTag)
    }
    private fun DataInputStream.readRound3() = PairingRound3(readString(), readRole(), readString(), readBigInt())
    private fun DataOutputStream.writeConfirmation(value: PairingConfirmation) {
        writeByte(TYPE_CONFIRMATION); writeString(value.invitationId); writeByte(value.role.ordinal); writeData(value.hmacSha256)
    }
    private fun DataInputStream.readConfirmation() = PairingConfirmation(readString(), readRole(), readData())

    private fun DataOutputStream.writeString(value: String) = writeData(value.toByteArray(StandardCharsets.UTF_8))
    private fun DataInputStream.readString() = String(readData(), StandardCharsets.UTF_8)
    private fun DataOutputStream.writeBigInt(value: BigInteger) = writeData(value.toByteArray())
    private fun DataInputStream.readBigInt() = BigInteger(readData().also { require(it.isNotEmpty()) })
    private fun DataOutputStream.writeBigInts(values: List<BigInteger>) { require(values.size <= 8); writeInt(values.size); values.forEach { writeBigInt(it) } }
    private fun DataInputStream.readBigInts() = List(readInt().also { require(it in 0..8) }) { readBigInt() }
    private fun DataOutputStream.writeData(value: ByteArray) { require(value.size <= 16 * 1024); writeInt(value.size); write(value) }
    private fun DataInputStream.readData() = ByteArray(readInt().also { require(it in 0..16 * 1024) }).also(::readFully)
    private fun DataInputStream.readRole() = PairingRole.entries.getOrNull(readUnsignedByte()) ?: error("Unknown role")

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'D'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
    private const val TYPE_ROUND_1 = 1
    private const val TYPE_ROUND_2 = 2
    private const val TYPE_ROUND_3 = 3
    private const val TYPE_CONFIRMATION = 4
}

private fun participantId(role: PairingRole, deviceId: String, invitationId: String) =
    "${role.name.lowercase()}:$deviceId:$invitationId"

private fun BigInteger.unsignedBytes() = toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

private fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    val extracted = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(salt, "HmacSHA256")); doFinal(input) }
    val output = ByteArrayOutputStream()
    var previous = ByteArray(0)
    var counter = 1
    while (output.size() < length) {
        previous = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(extracted, "HmacSHA256")); update(previous); update(info); update(counter.toByte()); doFinal()
        }
        output.write(previous); counter++
    }
    return output.toByteArray().copyOf(length)
}
