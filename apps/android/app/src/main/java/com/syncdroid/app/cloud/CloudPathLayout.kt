package com.syncdroid.app.cloud

data class CloudObjectPath(val segments: List<String>) {
    init {
        require(segments.isNotEmpty()) { "A cloud path needs at least one segment" }
    }

    val displayPath: String get() = segments.joinToString("/")
}

object CloudPathLayout {
    const val ROOT_FOLDER = "SyncDroid"

    fun folderRoot(syncFolderName: String): CloudObjectPath = CloudObjectPath(
        listOf(ROOT_FOLDER, validatedFolderName(syncFolderName)),
    )

    fun file(syncFolderName: String, relativePath: String): CloudObjectPath {
        val fileSegments = relativePath.replace('\\', '/')
            .split('/')
            .filter(String::isNotBlank)
        require(fileSegments.isNotEmpty()) { "Cloud file path cannot be blank" }
        require(fileSegments.none { it == "." || it == ".." }) { "Cloud file path cannot traverse folders" }
        return CloudObjectPath(folderRoot(syncFolderName).segments + fileSegments)
    }

    private fun validatedFolderName(rawName: String): String {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "Sync folder name cannot be blank" }
        require(name != "." && name != "..") { "This sync folder name cannot be used in cloud storage" }
        require(name.length <= 120) { "Sync folder names used in cloud storage are limited to 120 characters" }
        require(name.none { it.isISOControl() || it in INVALID_CROSS_PROVIDER_CHARACTERS }) {
            "Sync folder name contains a character unsupported by Google Drive or OneDrive"
        }
        return name
    }

    private val INVALID_CROSS_PROVIDER_CHARACTERS = setOf('"', '*', ':', '<', '>', '?', '/', '\\', '|')
}
