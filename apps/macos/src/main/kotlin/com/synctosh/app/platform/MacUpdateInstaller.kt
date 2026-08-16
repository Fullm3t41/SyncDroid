package com.synctosh.app.platform

import java.awt.Desktop
import java.nio.file.Path

object MacUpdateInstaller {
    fun open(installer: Path) {
        require(installer.toFile().isFile) { "The downloaded disk image is unavailable" }
        Desktop.getDesktop().open(installer.toFile())
    }
}
