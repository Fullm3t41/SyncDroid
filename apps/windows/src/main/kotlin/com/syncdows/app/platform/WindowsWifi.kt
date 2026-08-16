package com.syncdows.app.platform

object WindowsWifi {
    fun currentSsid(): String? {
        if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) return null
        return runCatching {
            val process = ProcessBuilder("netsh", "wlan", "show", "interfaces")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor() != 0) return@runCatching null
            parseSsid(output)
        }.getOrNull()
    }

    internal fun parseSsid(output: String): String? = output.lineSequence()
        .map(String::trim)
        .firstOrNull { line -> line.startsWith("SSID", ignoreCase = true) && !line.startsWith("BSSID", ignoreCase = true) }
        ?.substringAfter(':', "")
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
