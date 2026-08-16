package com.syncdows.app.platform

import java.nio.file.Path

object WindowsAppPaths {
    val applicationData: Path by lazy {
        val localAppData = System.getenv("LOCALAPPDATA")?.trim()?.takeIf(String::isNotEmpty)
        if (localAppData != null) {
            Path.of(localAppData, "SyncDows")
        } else {
            Path.of(System.getProperty("user.home"), "AppData", "Local", "SyncDows")
        }
    }

    val database: Path get() = applicationData.resolve("syncdows.db")
    val identity: Path get() = applicationData.resolve("identity.p12")
    val transfers: Path get() = applicationData.resolve("transfers")
}
