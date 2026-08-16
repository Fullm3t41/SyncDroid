package com.synctosh.app.platform

import com.synctosh.app.model.MainSection
import com.synctosh.app.model.ThemeMode
import java.util.prefs.Preferences

class AppPreferences {
    private val preferences = Preferences.userRoot().node("com/synctosh/app")

    var themeMode: ThemeMode
        get() = preferences.get(KEY_THEME, ThemeMode.System.name)
            .let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.System }
        set(value) = preferences.put(KEY_THEME, value.name)

    var selectedSection: MainSection
        get() = preferences.get(KEY_SECTION, MainSection.Sync.name)
            .let { stored -> MainSection.entries.firstOrNull { it.name == stored } ?: MainSection.Sync }
        set(value) = preferences.put(KEY_SECTION, value.name)

    var windowWidth: Float
        get() = preferences.getFloat(KEY_WINDOW_WIDTH, 1_080f).coerceAtLeast(760f)
        set(value) = preferences.putFloat(KEY_WINDOW_WIDTH, value)

    var windowHeight: Float
        get() = preferences.getFloat(KEY_WINDOW_HEIGHT, 760f).coerceAtLeast(600f)
        set(value) = preferences.putFloat(KEY_WINDOW_HEIGHT, value)

    var discoveryIntervalMinutes: Int
        get() = preferences.getInt(KEY_DISCOVERY_INTERVAL, 5)
        set(value) = preferences.putInt(KEY_DISCOVERY_INTERVAL, value)

    var discoveryWindowSeconds: Long
        get() = preferences.getLong(KEY_DISCOVERY_WINDOW, 30L)
        set(value) = preferences.putLong(KEY_DISCOVERY_WINDOW, value)

    var deviceName: String?
        get() = preferences.get(KEY_DEVICE_NAME, null)?.takeIf(String::isNotBlank)
        set(value) {
            if (value.isNullOrBlank()) preferences.remove(KEY_DEVICE_NAME)
            else preferences.put(KEY_DEVICE_NAME, value.trim())
        }

    fun pairingAttemptState(nowMillis: Long = System.currentTimeMillis()): PairingAttemptState = PairingAttemptState(
        failedAttempts = preferences.getInt(KEY_PAIRING_FAILURES, 0),
        resetAtMillis = preferences.getLong(KEY_PAIRING_RESET_AT, 0),
        nowMillis = nowMillis,
    )

    fun recordPairingFailure(nowMillis: Long = System.currentTimeMillis()): PairingAttemptState {
        val current = pairingAttemptState(nowMillis).normalized()
        val failures = (current.failedAttempts + 1).coerceAtMost(PairingAttemptState.MAX_ATTEMPTS)
        val next = PairingAttemptState(
            failures,
            current.resetAtMillis.takeIf { it > nowMillis } ?: nowMillis + PairingAttemptState.WINDOW_MILLIS,
            nowMillis,
        )
        preferences.putInt(KEY_PAIRING_FAILURES, next.failedAttempts)
        preferences.putLong(KEY_PAIRING_RESET_AT, next.resetAtMillis)
        return next
    }

    fun clearPairingAttempts() {
        preferences.remove(KEY_PAIRING_FAILURES)
        preferences.remove(KEY_PAIRING_RESET_AT)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_SECTION = "selected_section"
        const val KEY_WINDOW_WIDTH = "window_width"
        const val KEY_WINDOW_HEIGHT = "window_height"
        const val KEY_DISCOVERY_INTERVAL = "discovery_interval_minutes"
        const val KEY_DISCOVERY_WINDOW = "discovery_window_seconds"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_PAIRING_FAILURES = "pairing_failed_attempts"
        const val KEY_PAIRING_RESET_AT = "pairing_reset_at"
    }
}

data class PairingAttemptState(
    val failedAttempts: Int,
    val resetAtMillis: Long,
    private val nowMillis: Long,
) {
    fun normalized(): PairingAttemptState = if (resetAtMillis == 0L || nowMillis >= resetAtMillis) {
        PairingAttemptState(0, 0, nowMillis)
    } else {
        copy(failedAttempts = failedAttempts.coerceIn(0, MAX_ATTEMPTS))
    }

    val attemptsRemaining get() = (MAX_ATTEMPTS - normalized().failedAttempts).coerceAtLeast(0)
    val locked get() = attemptsRemaining == 0 && resetAtMillis > nowMillis

    companion object {
        const val MAX_ATTEMPTS = 5
        const val WINDOW_MILLIS = 15 * 60 * 1_000L
    }
}
