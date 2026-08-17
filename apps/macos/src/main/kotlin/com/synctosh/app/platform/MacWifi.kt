package com.synctosh.app.platform

object MacWifi {
    fun currentSsid(): String? {
        if (!System.getProperty("os.name", "").contains("Mac", ignoreCase = true)) return null
        return runCatching {
            val interfaceName = wifiInterface() ?: return@runCatching null
            val output = command("/usr/sbin/networksetup", "-getairportnetwork", interfaceName) ?: return@runCatching null
            parseCurrentNetwork(output)
        }.getOrNull()
    }

    private fun wifiInterface(): String? {
        val output = command("/usr/sbin/networksetup", "-listallhardwareports") ?: return null
        val lines = output.lineSequence().map(String::trim).toList()
        return lines.indices.firstNotNullOfOrNull { index ->
            if (lines[index].equals("Hardware Port: Wi-Fi", ignoreCase = true)) {
                lines.getOrNull(index + 1)?.substringAfter("Device:", "")?.trim()?.takeIf(String::isNotBlank)
            } else null
        }
    }

    private fun command(vararg arguments: String): String? {
        val process = ProcessBuilder(*arguments).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return output.takeIf { process.waitFor() == 0 }
    }

    internal fun parseCurrentNetwork(output: String): String? = output
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.startsWith("Current Wi-Fi Network:", ignoreCase = true) }
        ?.substringAfter(':', "")
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
