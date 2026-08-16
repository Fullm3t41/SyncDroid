package com.syncdroid.shared.update

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

const val RELEASE_SIGNATURE_FILE = "syncdroid-update.properties.sig"
const val SIGNED_MANIFEST_PLATFORM_ID = "release-manifest-v1"

/** Public half of the permanent SyncDroid release-signing key. */
const val RELEASE_SIGNING_PUBLIC_KEY_BASE64 =
    "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAoPgI9GYbyrLtZFBSK/FAmTh4prK6e4FwLJe5caU+eN3aD+9eTTw0ZmJSETHZAc9yh7qNmjfVXSz+Wnw4MeyTnv1q++uagasHy5jN22hru0ETSvPtnrnNrtDuiXsSDZiKPITfBvk1oqwiqIYmDPX8/HpABk5lIi+3UnnVgEGlCYLwrrEMPwniJY+rzbTvpIeneT1zXpB/gz5lNyjaB+JhV4AuhroWLWeGA7cxAbN+yY5AYK6zUPHF+FPsmm5JyDqHD1J/1s7Ng8UoHMPgsXNoWTVlfMXKc9YQqWFp1LWJv3rj26bfZqovYNuATB1LwyPwPK4qukj3+ue130hQInQl6HkZ49xkWhC9jFjoq9K+yP87FB8atvHhktN6mxJXcSEywdeU3Lj1mXCbF9TwpfB09HVHHAfUzQ4QvG/NtLDctLj8HYdqx3zcPP8O4fymCkHOBLDxJA3vq6AW3Mj+Ar7INNsGGB7/5vldpM+kwygpax4oyO2Uu4nyxoeNdaVXVX8TAgMBAAE="

data class SignedReleaseManifest(
    val manifestText: String,
    val signatureBase64: String,
    val manifest: ReleaseManifest,
) {
    fun envelopeBytes(): ByteArray = buildString {
        appendLine(ENVELOPE_MAGIC)
        appendLine(signatureBase64)
        append(manifestText)
    }.toByteArray(StandardCharsets.UTF_8)

    companion object {
        private const val ENVELOPE_MAGIC = "SYNCDROID-SIGNED-MANIFEST-V1"

        fun verify(
            manifestText: String,
            signatureText: String,
            publicKeyBase64: String = RELEASE_SIGNING_PUBLIC_KEY_BASE64,
        ): SignedReleaseManifest {
            require(manifestText.toByteArray(StandardCharsets.UTF_8).size <= MAX_MANIFEST_BYTES) {
                "Release manifest is too large"
            }
            val signatureBase64 = signatureText.trim()
            val signatureBytes = runCatching { Base64.getDecoder().decode(signatureBase64) }
                .getOrElse { throw IllegalArgumentException("Release manifest signature is not valid Base64", it) }
            val publicKeyBytes = runCatching { Base64.getDecoder().decode(publicKeyBase64) }
                .getOrElse { throw IllegalArgumentException("Release signing public key is invalid", it) }
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val valid = Signature.getInstance("SHA256withRSA").run {
                initVerify(publicKey)
                update(manifestText.toByteArray(StandardCharsets.UTF_8))
                verify(signatureBytes)
            }
            require(valid) { "Release manifest signature is invalid" }
            return SignedReleaseManifest(manifestText, signatureBase64, ReleaseManifest.parse(manifestText))
        }

        fun decodeEnvelope(
            bytes: ByteArray,
            publicKeyBase64: String = RELEASE_SIGNING_PUBLIC_KEY_BASE64,
        ): SignedReleaseManifest {
            require(bytes.size <= MAX_ENVELOPE_BYTES) { "Signed release manifest is too large" }
            val text = String(bytes, StandardCharsets.UTF_8)
            val firstBreak = text.indexOf('\n')
            val secondBreak = text.indexOf('\n', firstBreak + 1)
            require(firstBreak > 0 && secondBreak > firstBreak && text.substring(0, firstBreak).trimEnd('\r') == ENVELOPE_MAGIC) {
                "Unsupported signed release manifest envelope"
            }
            val signature = text.substring(firstBreak + 1, secondBreak).trimEnd('\r')
            return verify(text.substring(secondBreak + 1), signature, publicKeyBase64)
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        const val MAX_MANIFEST_BYTES = 1024 * 1024
        const val MAX_ENVELOPE_BYTES = MAX_MANIFEST_BYTES + 16 * 1024
    }
}
