package com.syncdroid.app.mesh

import android.content.Context

data class PairingAttemptState(
    val failedAttempts: Int = 0,
    val resetAtMillis: Long = 0L,
    val lockedUntilMillis: Long = 0L,
) {
    fun normalized(nowMillis: Long): PairingAttemptState = when {
        lockedUntilMillis > 0L && nowMillis >= lockedUntilMillis -> PairingAttemptState()
        resetAtMillis > 0L && nowMillis >= resetAtMillis -> PairingAttemptState()
        else -> this
    }

    fun afterFailure(nowMillis: Long): PairingAttemptState {
        val current = normalized(nowMillis)
        if (current.lockedUntilMillis > nowMillis) return current
        val failures = current.failedAttempts + 1
        return if (failures >= PairingAttemptLimiter.MAX_FAILED_ATTEMPTS) {
            PairingAttemptState(
                failedAttempts = PairingAttemptLimiter.MAX_FAILED_ATTEMPTS,
                resetAtMillis = nowMillis + PairingAttemptLimiter.LOCKOUT_MILLIS,
                lockedUntilMillis = nowMillis + PairingAttemptLimiter.LOCKOUT_MILLIS,
            )
        } else {
            PairingAttemptState(
                failedAttempts = failures,
                resetAtMillis = current.resetAtMillis.takeIf { it > nowMillis }
                    ?: nowMillis + PairingAttemptLimiter.ATTEMPT_WINDOW_MILLIS,
            )
        }
    }

    fun attemptsRemaining(nowMillis: Long): Int =
        (PairingAttemptLimiter.MAX_FAILED_ATTEMPTS - normalized(nowMillis).failedAttempts).coerceAtLeast(0)
}

class PairingAttemptLimiter(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun state(nowMillis: Long = System.currentTimeMillis()): PairingAttemptState {
        val stored = PairingAttemptState(
            failedAttempts = preferences.getInt(KEY_FAILURES, 0),
            resetAtMillis = preferences.getLong(KEY_RESET_AT, 0L),
            lockedUntilMillis = preferences.getLong(KEY_LOCKED_UNTIL, 0L),
        )
        val normalized = stored.normalized(nowMillis)
        if (normalized != stored) save(normalized)
        return normalized
    }

    @Synchronized
    fun recordFailure(nowMillis: Long = System.currentTimeMillis()): PairingAttemptState =
        state(nowMillis).afterFailure(nowMillis).also(::save)

    @Synchronized
    fun recordSuccess() = save(PairingAttemptState())

    private fun save(state: PairingAttemptState) {
        preferences.edit()
            .putInt(KEY_FAILURES, state.failedAttempts)
            .putLong(KEY_RESET_AT, state.resetAtMillis)
            .putLong(KEY_LOCKED_UNTIL, state.lockedUntilMillis)
            .apply()
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 15 * 60 * 1000L
        const val ATTEMPT_WINDOW_MILLIS = 15 * 60 * 1000L
        private const val PREFERENCES = "pairing_attempt_limiter"
        private const val KEY_FAILURES = "failed_attempts"
        private const val KEY_RESET_AT = "reset_at_millis"
        private const val KEY_LOCKED_UNTIL = "locked_until_millis"
    }
}
