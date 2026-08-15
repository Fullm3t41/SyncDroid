package com.syncdroid.app.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.syncdroid.app.sync.normalizedRelativePath
import java.io.File

data class ConfiguredFolderEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

interface ConfiguredFolderSource {
    fun list(relativeDirectory: String): List<ConfiguredFolderEntry>
    fun delete(relativePaths: Collection<String>)
}

fun configuredFolderSource(context: Context, location: String): ConfiguredFolderSource =
    if (location.startsWith("content://")) {
        DocumentTreeFolderSource(context, Uri.parse(location))
    } else {
        DirectConfiguredFolderSource(File(location))
    }

internal class DirectConfiguredFolderSource(rootDirectory: File) : ConfiguredFolderSource {
    private val root = rootDirectory.canonicalFile.also {
        require(it.isDirectory) { "The configured folder is unavailable" }
    }

    override fun list(relativeDirectory: String): List<ConfiguredFolderEntry> {
        val directory = resolve(relativeDirectory)
        require(directory.isDirectory) { "The selected location is not a folder" }
        return directory.listFiles().orEmpty()
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { file ->
                ConfiguredFolderEntry(
                    name = file.name,
                    relativePath = file.relativeTo(root).invariantSeparatorsPath,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0,
                    modifiedAtMillis = file.lastModified(),
                )
            }
    }

    override fun delete(relativePaths: Collection<String>) {
        val targets = relativePaths.map(::resolve)
        require(targets.all(File::isFile)) { "Only files can be deleted from this screen" }
        targets.forEach { require(it.delete()) { "Could not delete ${it.name}" } }
    }

    private fun resolve(relativePath: String): File {
        val target = if (relativePath.isBlank()) root else File(root, normalizedRelativePath(relativePath)).canonicalFile
        require(target == root || target.toPath().startsWith(root.toPath())) { "Path is outside the configured folder" }
        return target
    }
}

private class DocumentTreeFolderSource(context: Context, treeUri: Uri) : ConfiguredFolderSource {
    private val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
        "Folder permission is unavailable"
    }

    override fun list(relativeDirectory: String): List<ConfiguredFolderEntry> {
        val directory = find(relativeDirectory)
        require(directory.isDirectory) { "The selected location is not a folder" }
        return directory.listFiles()
            .sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name.orEmpty().lowercase() }))
            .mapNotNull { document ->
                val name = document.name ?: return@mapNotNull null
                ConfiguredFolderEntry(
                    name = name,
                    relativePath = join(relativeDirectory, name),
                    isDirectory = document.isDirectory,
                    sizeBytes = if (document.isFile) document.length() else 0,
                    modifiedAtMillis = document.lastModified(),
                )
            }
    }

    override fun delete(relativePaths: Collection<String>) {
        val targets = relativePaths.map(::find)
        require(targets.all(DocumentFile::isFile)) { "Only files can be deleted from this screen" }
        targets.forEach { require(it.delete()) { "Could not delete ${it.name.orEmpty()}" } }
    }

    private fun find(relativePath: String): DocumentFile {
        var current = root
        if (relativePath.isNotBlank()) {
            normalizedRelativePath(relativePath).split('/').forEach { part ->
                current = requireNotNull(current.findFile(part)) { "$part is no longer available" }
            }
        }
        return current
    }
}

fun parentFolderPath(relativePath: String): String = relativePath.substringBeforeLast('/', "")

private fun join(parent: String, child: String): String =
    if (parent.isBlank()) child else "${normalizedRelativePath(parent)}/$child"
