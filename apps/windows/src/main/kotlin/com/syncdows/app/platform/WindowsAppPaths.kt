package com.syncdows.app.platform

import java.nio.file.Path

object WindowsAppPaths {
    val applicationData: Path by lazy {
        applicationDataPath(System.getenv("LOCALAPPDATA"), System.getProperty("user.home"))
    }

    val database: Path get() = applicationData.resolve("syncdows.db")
    val identity: Path get() = applicationData.resolve("identity.p12")
    val transfers: Path get() = applicationData.resolve("transfers")
    val updates: Path get() = applicationData.resolve("updates")

    internal fun applicationDataPath(localAppData: String?, userHome: String): Path {
        val root = localAppData?.trim()?.takeIf(String::isNotEmpty)?.let(Path::of)
            ?: Path.of(userHome, "AppData", "Local")
        return root.resolve("Fullm3t41").resolve("SyncDows")
    }
}
