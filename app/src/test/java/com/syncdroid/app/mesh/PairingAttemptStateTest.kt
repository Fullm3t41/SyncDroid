package com.syncdroid.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingAttemptStateTest {
    @Test fun fifthFailureStartsFifteenMinuteLockout() {
        var state = PairingAttemptState()
        repeat(5) { state = state.afterFailure(nowMillis = 1_000L + it) }

        assertEquals(5, state.failedAttempts)
        assertEquals(0, state.attemptsRemaining(1_005L))
        assertTrue(state.lockedUntilMillis >= 1_004L + PairingAttemptLimiter.LOCKOUT_MILLIS)
    }

    @Test fun attemptsResetAfterLockout() {
        val locked = PairingAttemptState().let { initial ->
            (0 until 5).fold(initial) { state, attempt -> state.afterFailure(1_000L + attempt) }
        }

        val reset = locked.normalized(locked.lockedUntilMillis)
        assertEquals(PairingAttemptState(), reset)
        assertEquals(5, reset.attemptsRemaining(locked.lockedUntilMillis))
    }

    @Test fun failedAttemptWindowAlsoExpires() {
        val state = PairingAttemptState().afterFailure(1_000L)
        assertEquals(4, state.attemptsRemaining(1_001L))
        assertEquals(5, state.attemptsRemaining(1_000L + PairingAttemptLimiter.ATTEMPT_WINDOW_MILLIS))
    }
}
