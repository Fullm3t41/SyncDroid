package com.synctosh.app.platform

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

object MacFolderPicker {
    fun chooseExisting(title: String): Path? = chooseDirectory(title)

    fun openInFinder(path: Path) {
        val folder = path.toAbsolutePath().normalize()
        require(Files.isDirectory(folder)) { "This folder is no longer available on this Mac" }
        require(Desktop.isDesktopSupported()) { "Finder integration is unavailable" }
        val desktop = Desktop.getDesktop()
        require(desktop.isSupported(Desktop.Action.OPEN)) { "Finder integration is unavailable" }
        desktop.open(folder.toFile())
    }

    fun chooseParentAndCreate(title: String, displayName: String): Path? {
        val parent = chooseDirectory(title) ?: return null
        val folderName = displayName
            .trim()
            .replace(Regex("[/\\u0000:]"), "-")
            .ifBlank { "SyncTosh Folder" }
        val destination = parent.resolve(folderName)
        require(!Files.exists(destination)) {
            "A file or folder named '$folderName' already exists in that location"
        }
        return Files.createDirectory(destination)
    }

    private fun chooseDirectory(title: String): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            currentDirectory = Path.of(System.getProperty("user.home")).toFile()
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath().toAbsolutePath().normalize()
        } else {
            null
        }
    }
}
