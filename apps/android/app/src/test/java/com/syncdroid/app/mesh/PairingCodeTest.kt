package com.syncdroid.app.mesh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {
    @Test fun offerAcceptsCorrectCodeWithinWindow() {
        val offer = PairingCodes.create(nowMillis = 1_000)
        assertTrue(offer.verifies(offer.code, nowMillis = 2_000, attemptNumber = 1))
        assertFalse(offer.verifies("999999", nowMillis = 2_000, attemptNumber = 2))
    }

    @Test fun offerExpiresAndLimitsAttempts() {
        val offer = PairingCodes.create(nowMillis = 1_000, validForMillis = 100)
        assertFalse(offer.verifies(offer.code, nowMillis = 1_100, attemptNumber = 1))
        assertFalse(offer.verifies(offer.code, nowMillis = 1_050, attemptNumber = 6))
    }
}
