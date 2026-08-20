package com.synctosh.app.platform

import java.nio.file.Path

object MacAppPaths {
    private val cacheRoot: Path by lazy {
        Path.of(System.getProperty("user.home"), "Library", "Caches", "SyncTosh")
    }
    private val stateRoot: Path by lazy {
        Path.of(System.getProperty("user.home"), "Library", "Application Support", "SyncTosh")
    }

    val updates: Path by lazy {
        cacheRoot.resolve("updates")
    }

    val workerEndpoint: Path by lazy { cacheRoot.resolve("worker.endpoint") }
    val workerLock: Path by lazy { stateRoot.resolve("worker.lock") }
    val workerLog: Path by lazy { cacheRoot.resolve("worker-ui.log") }
}
