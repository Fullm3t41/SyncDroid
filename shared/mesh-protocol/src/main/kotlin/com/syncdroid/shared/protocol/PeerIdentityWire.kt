package com.syncdroid.shared.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

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
}

object StablePeerProofWireCodec {
    fun encode(value: StablePeerProof): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            listOf(
                value.groupId, value.deviceId, value.publicKeyBase64, value.tlsPublicKeyBase64,
                value.nonceBase64, value.signatureBase64,
            ).forEach { output.writeString(it) }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StablePeerProof = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC) { "Invalid stable identity proof" }
        StablePeerProof(
            input.readString(), input.readString(), input.readString(), input.readString(), input.readString(), input.readString(),
        ).also { require(input.available() == 0) { "Trailing stable identity proof data" } }
    }

    private fun DataOutputStream.writeString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD_BYTES)
        writeInt(encoded.size)
        write(encoded)
    }
    private fun DataInputStream.readString(): String {
        val size = readInt().also { require(it in 0..MAX_FIELD_BYTES) }
        return String(ByteArray(size).also(::readFully), Charsets.UTF_8)
    }

    private const val MAGIC = 0x53445049
    private const val MAX_FIELD_BYTES = 16 * 1024
}
