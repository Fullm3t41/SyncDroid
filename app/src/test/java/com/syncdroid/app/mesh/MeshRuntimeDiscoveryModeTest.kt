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
}
