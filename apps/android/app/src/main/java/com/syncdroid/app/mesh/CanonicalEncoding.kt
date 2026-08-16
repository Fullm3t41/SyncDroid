package com.syncdroid.app.mesh

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/** Language-neutral canonical bytes used for hashes and signatures. */
internal fun canonicalBytes(block: CanonicalOutput.() -> Unit): ByteArray =
    ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output -> CanonicalOutput(output).block() }
        bytes.toByteArray()
    }

internal class CanonicalOutput(private val output: DataOutputStream) {
    fun string(value: String) {
        val encoded = Normalizer.normalize(value, Normalizer.Form.NFC).toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= MAX_CANONICAL_STRING_BYTES) { "Canonical string is too large" }
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    fun int64(value: Long) = output.writeLong(value)

    fun bool(value: Boolean) = output.writeByte(if (value) 1 else 0)

    fun strings(values: List<String>) {
        require(values.size <= MAX_CANONICAL_ITEMS) { "Canonical list is too large" }
        output.writeInt(values.size)
        values.forEach(::string)
    }
}

private const val MAX_CANONICAL_STRING_BYTES = 1024 * 1024
private const val MAX_CANONICAL_ITEMS = 10_000
