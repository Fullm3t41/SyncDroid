package com.syncdows.app.platform

import java.net.InetAddress

object WindowsDeviceName {
    fun current(): String {
        val computerName = System.getenv("COMPUTERNAME")?.trim()
        if (!computerName.isNullOrBlank()) return computerName

        val hostName = runCatching { InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrNull()
        if (!hostName.isNullOrBlank()) return hostName
        return "This PC"
    }
}
