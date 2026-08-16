package com.synctosh.app.platform

import java.nio.file.Path

object MacAppPaths {
    val updates: Path by lazy {
        Path.of(System.getProperty("user.home"), "Library", "Caches", "SyncTosh", "updates")
    }
}
