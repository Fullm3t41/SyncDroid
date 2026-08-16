package com.syncdroid.shared.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverwriteOnlyExceptionTest {
    @Test
    fun offlineParticipantBlocksFinalization() {
        assertFalse(
            shouldFinalizeOverwriteOnlyException(
                participantDeviceIds = setOf("phone-a", "phone-b", "mac-a"),
                absenceReporterDeviceIds = setOf("phone-a", "phone-b"),
                tombstonedDeviceIds = emptySet(),
            ),
        )
    }

    @Test
    fun reportsAndTombstonesCanJointlyCompleteDeletion() {
        assertTrue(
            shouldFinalizeOverwriteOnlyException(
                participantDeviceIds = setOf("phone-a", "phone-b", "mac-a"),
                absenceReporterDeviceIds = setOf("phone-a", "phone-b"),
                tombstonedDeviceIds = setOf("mac-a"),
            ),
        )
    }

    @Test
    fun emptyParticipantSetNeverFinalizes() {
        assertFalse(
            shouldFinalizeOverwriteOnlyException(
                participantDeviceIds = emptySet(),
                absenceReporterDeviceIds = setOf("phone-a"),
                tombstonedDeviceIds = emptySet(),
            ),
        )
    }
}
