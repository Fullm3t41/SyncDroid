package com.syncdroid.shared.protocol

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

fun deviceIdForPublicKey(publicKey: PublicKey): String = sha256(publicKey.encoded)
    .copyOfRange(0, 18)
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

fun fingerprintForPublicKey(publicKey: PublicKey): String = sha256(publicKey.encoded)
    .joinToString("") { "%02X".format(it) }
    .chunked(4)
    .take(8)
    .joinToString(" ")

fun decodeEcPublicKeyBase64(encoded: String): PublicKey = KeyFactory.getInstance("EC")
    .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))

fun verifyEcdsaSha256(publicKey: PublicKey, payload: ByteArray, signatureBase64: String): Boolean = runCatching {
    Signature.getInstance("SHA256withECDSA").run {
        initVerify(publicKey)
        update(payload)
        verify(Base64.getDecoder().decode(signatureBase64))
    }
}.getOrDefault(false)
