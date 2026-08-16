package com.syncdroid.app.mesh

import android.content.Context
import android.os.Build
import android.provider.Settings

fun defaultDeviceName(context: Context): String {
    val configured = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()?.trim()
    return configured?.takeIf { it.isNotBlank() }
        ?: Build.MODEL.trim().takeIf { it.isNotBlank() }
        ?: "Android device"
}

class LocalDeviceNameStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "local_device_name",
        Context.MODE_PRIVATE,
    )

    fun load(): String = preferences.getString(KEY_CUSTOM_NAME, null)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: defaultDeviceName(appContext)

    fun save(name: String) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Device name cannot be blank" }
        require(cleanName.length <= MAX_DEVICE_NAME_LENGTH) { "Device name is too long" }
        preferences.edit().putString(KEY_CUSTOM_NAME, cleanName).apply()
    }

    private companion object {
        const val KEY_CUSTOM_NAME = "custom_name"
        const val MAX_DEVICE_NAME_LENGTH = 64
    }
}
