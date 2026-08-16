package com.syncdroid.shared.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets

enum class PairingRole { Inviter, Joiner }

data class PairingIdentity(
    val deviceId: String,
    val publicKeySpkiBase64: String,
    val displayName: String = "Device",
) {
    companion object
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

data class PairingConfirmation(
    val invitationId: String,
    val role: PairingRole,
    val hmacSha256: ByteArray,
)

/** Wire-compatible with the SDP1 pairing messages used by SyncDroid 0.1.x. */
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
        require(ByteArray(MAGIC.size).also(input::readFully).contentEquals(MAGIC)) { "Invalid pairing message" }
        val value = when (val type = input.readUnsignedByte()) {
            TYPE_ROUND_1 -> input.readRound1()
            TYPE_ROUND_2 -> input.readRound2()
            TYPE_ROUND_3 -> input.readRound3()
            TYPE_CONFIRMATION -> input.readConfirmation()
            else -> error("Unknown pairing message type $type")
        }
        require(input.available() == 0) { "Trailing pairing message data" }
        value
    }

    private fun DataOutputStream.writeRound1(value: PairingRound1) {
        writeByte(TYPE_ROUND_1)
        writeString(value.invitationId)
        writeByte(value.role.ordinal)
        writeString(value.identity.deviceId)
        writeString(value.identity.publicKeySpkiBase64)
        writeString(value.identity.displayName)
        writeString(value.participantId)
        writeBigInt(value.gx1)
        writeBigInt(value.gx2)
        writeBigInts(value.proofX1)
        writeBigInts(value.proofX2)
    }

    private fun DataInputStream.readRound1() = PairingRound1(
        readString(), readRole(), PairingIdentity(readString(), readString(), readString()), readString(),
        readBigInt(), readBigInt(), readBigInts(), readBigInts(),
    )

    private fun DataOutputStream.writeRound2(value: PairingRound2) {
        writeByte(TYPE_ROUND_2)
        writeString(value.invitationId)
        writeByte(value.role.ordinal)
        writeString(value.participantId)
        writeBigInt(value.a)
        writeBigInts(value.proofX2s)
    }

    private fun DataInputStream.readRound2() = PairingRound2(
        readString(), readRole(), readString(), readBigInt(), readBigInts(),
    )

    private fun DataOutputStream.writeRound3(value: PairingRound3) {
        writeByte(TYPE_ROUND_3)
        writeString(value.invitationId)
        writeByte(value.role.ordinal)
        writeString(value.participantId)
        writeBigInt(value.macTag)
    }

    private fun DataInputStream.readRound3() = PairingRound3(readString(), readRole(), readString(), readBigInt())

    private fun DataOutputStream.writeConfirmation(value: PairingConfirmation) {
        writeByte(TYPE_CONFIRMATION)
        writeString(value.invitationId)
        writeByte(value.role.ordinal)
        writeData(value.hmacSha256)
    }

    private fun DataInputStream.readConfirmation() = PairingConfirmation(readString(), readRole(), readData())

    private fun DataOutputStream.writeString(value: String) = writeData(value.toByteArray(StandardCharsets.UTF_8))
    private fun DataInputStream.readString() = String(readData(), StandardCharsets.UTF_8)
    private fun DataOutputStream.writeBigInt(value: BigInteger) = writeData(value.toByteArray())
    private fun DataInputStream.readBigInt() = BigInteger(readData().also { require(it.isNotEmpty()) })
    private fun DataOutputStream.writeBigInts(values: List<BigInteger>) {
        require(values.size <= MAX_ITEMS)
        writeInt(values.size)
        values.forEach { writeBigInt(it) }
    }
    private fun DataInputStream.readBigInts() = List(readInt().also { require(it in 0..MAX_ITEMS) }) { readBigInt() }
    private fun DataOutputStream.writeData(value: ByteArray) {
        require(value.size <= MAX_FIELD_BYTES)
        writeInt(value.size)
        write(value)
    }
    private fun DataInputStream.readData() = ByteArray(readInt().also { require(it in 0..MAX_FIELD_BYTES) }).also(::readFully)
    private fun DataInputStream.readRole() = PairingRole.entries.getOrNull(readUnsignedByte())
        ?: error("Unknown pairing role")

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'D'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
    private const val TYPE_ROUND_1 = 1
    private const val TYPE_ROUND_2 = 2
    private const val TYPE_ROUND_3 = 3
    private const val TYPE_CONFIRMATION = 4
    private const val MAX_ITEMS = 8
    private const val MAX_FIELD_BYTES = 16 * 1024
}
