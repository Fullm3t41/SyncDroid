package com.synctosh.app.platform

import java.net.InetAddress

object MacDeviceName {
    fun current(): String {
        val computerName = runCatching {
            ProcessBuilder("/usr/sbin/scutil", "--get", "ComputerName")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText().trim() }
        }.getOrNull()
        if (!computerName.isNullOrBlank()) return computerName

        val hostName = runCatching { InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrNull()
        if (!hostName.isNullOrBlank()) return hostName
        return "This Mac"
    }
}
