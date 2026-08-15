package com.syncdroid.app.mesh

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PairingCodeOffer(
    val code: String,
    val salt: ByteArray,
    val verifier: ByteArray,
    val expiresAtMillis: Long,
    val maxAttempts: Int = 5,
    val invitationId: String = newInvitationId(),
) {
    init {
        require(code.matches(Regex("\\d{6}"))) { "Pairing codes must contain exactly six digits" }
    }

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis

    fun verifies(candidate: String, nowMillis: Long, attemptNumber: Int): Boolean {
        if (isExpired(nowMillis) || attemptNumber !in 1..maxAttempts || !candidate.matches(Regex("\\d{6}"))) {
            return false
        }
        return MessageDigest.isEqual(verifier, pairingVerifier(candidate, salt))
    }
}

object PairingCodes {
    fun create(
        nowMillis: Long = System.currentTimeMillis(),
        validForMillis: Long = DEFAULT_VALIDITY_MILLIS,
        random: SecureRandom = SecureRandom(),
    ): PairingCodeOffer {
        val code = random.nextInt(1_000_000).toString().padStart(6, '0')
        val salt = ByteArray(16).also(random::nextBytes)
        return PairingCodeOffer(
            code = code,
            salt = salt,
            verifier = pairingVerifier(code, salt),
            expiresAtMillis = nowMillis + validForMillis,
        )
    }

    const val DEFAULT_VALIDITY_MILLIS = 5 * 60 * 1000L
}

private fun pairingVerifier(code: String, salt: ByteArray): ByteArray = SecretKeyFactory
    .getInstance("PBKDF2WithHmacSHA256")
    .generateSecret(PBEKeySpec(code.toCharArray(), salt, 120_000, 256))
    .encoded

private fun newInvitationId(): String = ByteArray(16)
    .also(SecureRandom()::nextBytes)
    .let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
