package com.syncdroid.app.mesh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRuntimeDiscoveryModeTest {
    @Test fun foregroundAlwaysUsesContinuousDiscovery() {
        assertTrue(shouldRunContinuousDiscovery(appInForeground = true, scheduledDiscoveryEnabled = true))
    }

    @Test fun backgroundUsesTimerWhenSchedulingIsEnabled() {
        assertFalse(shouldRunContinuousDiscovery(appInForeground = false, scheduledDiscoveryEnabled = true))
    }

    @Test fun disablingSchedulingKeepsDiscoveryContinuous() {
        assertTrue(shouldRunContinuousDiscovery(appInForeground = false, scheduledDiscoveryEnabled = false))
    }

    @Test fun activeSyncKeepsDiscoveryRunningPastWindowEnd() {
        assertTrue(shouldKeepDiscoveryActiveWhileSyncing(activeSyncCount = 1, runtimeClosing = false))
    }

    @Test fun discoveryCanStopAfterActiveSyncFinishes() {
        assertFalse(shouldKeepDiscoveryActiveWhileSyncing(activeSyncCount = 0, runtimeClosing = false))
    }

    @Test fun explicitRuntimeShutdownDoesNotWaitForActiveSync() {
        assertFalse(shouldKeepDiscoveryActiveWhileSyncing(activeSyncCount = 1, runtimeClosing = true))
    }
}
