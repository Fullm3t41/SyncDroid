package com.syncdroid.app.scheduling

import android.content.Context

data class DiscoveryPolicy(
    val scheduledDiscoveryEnabled: Boolean = true,
    val intervalMinutes: Int = 5,
    val windowSecondsOverride: Long? = null,
) {
    init {
        require(intervalMinutes in SUPPORTED_INTERVALS)
        require(windowSecondsOverride == null || windowSecondsOverride in SUPPORTED_WINDOWS_SECONDS)
    }

    val windowSeconds: Long get() = windowSecondsOverride ?: rendezvousWindowSeconds(intervalMinutes)

    companion object {
        val SUPPORTED_INTERVALS = setOf(5, 15, 30, 60, 6 * 60, 24 * 60, 48 * 60, 7 * 24 * 60)
        val SUPPORTED_WINDOWS_SECONDS = setOf(30L, 60L, 120L, 300L)
    }
}

class DiscoveryPolicyStore(context: Context) {
    private val preferences = context.getSharedPreferences("discovery_policy", Context.MODE_PRIVATE)

    fun load(): DiscoveryPolicy {
        val storedWindow = preferences.takeIf { it.contains(KEY_WINDOW_SECONDS) }
            ?.getLong(KEY_WINDOW_SECONDS, 0)
            ?.takeIf { it in DiscoveryPolicy.SUPPORTED_WINDOWS_SECONDS }
        return DiscoveryPolicy(
            scheduledDiscoveryEnabled = preferences.getBoolean(KEY_ENABLED, true),
            intervalMinutes = preferences.getInt(KEY_INTERVAL, 5).takeIf {
                it in DiscoveryPolicy.SUPPORTED_INTERVALS
            } ?: 5,
            windowSecondsOverride = storedWindow,
        )
    }

    fun save(policy: DiscoveryPolicy) {
        val editor = preferences.edit()
            .putBoolean(KEY_ENABLED, policy.scheduledDiscoveryEnabled)
            .putInt(KEY_INTERVAL, policy.intervalMinutes)
        if (policy.windowSecondsOverride == null) editor.remove(KEY_WINDOW_SECONDS)
        else editor.putLong(KEY_WINDOW_SECONDS, policy.windowSecondsOverride)
        editor.apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_INTERVAL = "interval_minutes"
        const val KEY_WINDOW_SECONDS = "window_seconds"
    }
}
