package com.syncdroid.app.storage

import java.io.File

data class ManagedFileEntry(
    val file: File,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

class ManagedFileRepository(private val rootDirectory: File) {
    val root: File by lazy {
        rootDirectory.apply {
            check(exists() || mkdirs()) { "Could not create managed storage" }
            check(isDirectory) { "Managed storage path is not a directory" }
        }.canonicalFile
    }

    fun list(directory: File): List<ManagedFileEntry> {
        val safeDirectory = requireInsideRoot(directory)
        require(safeDirectory.isDirectory) { "Not a directory" }

        return safeDirectory.listFiles().orEmpty()
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { ManagedFileEntry(it, it.isDirectory, if (it.isFile) it.length() else 0L) }
    }

    fun createFolder(parent: File, rawName: String): Result<File> = runCatching {
        val safeParent = requireInsideRoot(parent)
        require(safeParent.isDirectory) { "Parent is not a directory" }
        val name = rawName.trim()
        require(name.isNotEmpty()) { "Enter a folder name" }
        require(name != "." && name != "..") { "That folder name is not allowed" }
        require(name.length <= 80) { "Folder names can be up to 80 characters" }
        require(name.none { it == '/' || it == '\\' || it.isISOControl() }) {
            "Folder names cannot contain slashes or control characters"
        }

        val folder = requireInsideRoot(File(safeParent, name))
        require(!folder.exists()) { "A folder with that name already exists" }
        check(folder.mkdir()) { "Could not create the folder" }
        folder
    }

    fun relativePath(file: File): String {
        val safeFile = requireInsideRoot(file)
        return safeFile.relativeTo(root).path.ifEmpty { "Managed storage" }
    }

    fun parentInsideRoot(file: File): File? {
        val safeFile = requireInsideRoot(file)
        return if (safeFile == root) null else safeFile.parentFile?.let(::requireInsideRoot)
    }

    private fun requireInsideRoot(file: File): File {
        val canonical = file.canonicalFile
        require(canonical == root || canonical.toPath().startsWith(root.toPath())) {
            "Path is outside SyncDroid managed storage"
        }
        return canonical
    }
}
