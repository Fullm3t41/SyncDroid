package com.syncdows.app.platform

import java.nio.file.Path

object WindowsUpdateInstaller {
    fun launch(installer: Path) {
        require(installer.toFile().isFile) { "The downloaded installer is unavailable" }
        ProcessBuilder(installer.toAbsolutePath().toString()).start()
    }
}
