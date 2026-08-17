package com.syncdroid.app.scheduling

import android.content.Context

data class DiscoveryPolicy(
    val scheduledDiscoveryEnabled: Boolean = true,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val windowSecondsOverride: Long? = null,
) {
    init {
        require(intervalMinutes in SUPPORTED_INTERVALS)
        require(windowSecondsOverride == null || windowSecondsOverride in SUPPORTED_WINDOWS_SECONDS)
    }

    val windowSeconds: Long get() = windowSecondsOverride ?: rendezvousWindowSeconds(intervalMinutes)
    val alwaysOnDiscovery: Boolean get() = !scheduledDiscoveryEnabled

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 3 * 60
        const val DEFAULT_WINDOW_SECONDS = 5 * 60L
        val SUPPORTED_INTERVALS = setOf(15, 30, 60, DEFAULT_INTERVAL_MINUTES, 6 * 60, 24 * 60, 48 * 60, 7 * 24 * 60)
        val SUPPORTED_WINDOWS_SECONDS = setOf(DEFAULT_WINDOW_SECONDS, 10 * 60L, 15 * 60L)
    }
}

class DiscoveryPolicyStore(context: Context) {
    private val preferences = context.getSharedPreferences("discovery_policy", Context.MODE_PRIVATE)

    fun load(): DiscoveryPolicy {
        val storedInterval = preferences.takeIf { it.contains(KEY_INTERVAL) }
            ?.getInt(KEY_INTERVAL, DiscoveryPolicy.DEFAULT_INTERVAL_MINUTES)
        val interval = when {
            storedInterval == null -> DiscoveryPolicy.DEFAULT_INTERVAL_MINUTES
            storedInterval in DiscoveryPolicy.SUPPORTED_INTERVALS -> storedInterval
            storedInterval < DiscoveryPolicy.SUPPORTED_INTERVALS.min() -> DiscoveryPolicy.SUPPORTED_INTERVALS.min()
            else -> DiscoveryPolicy.DEFAULT_INTERVAL_MINUTES
        }
        val storedWindow = preferences.takeIf { it.contains(KEY_WINDOW_SECONDS) }
            ?.getLong(KEY_WINDOW_SECONDS, DiscoveryPolicy.DEFAULT_WINDOW_SECONDS)
            ?.let { seconds ->
                when {
                    seconds in DiscoveryPolicy.SUPPORTED_WINDOWS_SECONDS -> seconds
                    seconds < DiscoveryPolicy.DEFAULT_WINDOW_SECONDS -> DiscoveryPolicy.DEFAULT_WINDOW_SECONDS
                    else -> null
                }
            }
        return DiscoveryPolicy(
            scheduledDiscoveryEnabled = preferences.getBoolean(KEY_ENABLED, true),
            intervalMinutes = interval,
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
