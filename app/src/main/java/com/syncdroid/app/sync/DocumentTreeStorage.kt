package com.syncdroid.app.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

interface SyncFileApplier {
    fun apply(relativePath: String, input: InputStream, expectedSha256: String, sourceModifiedAtMillis: Long? = null)
    fun delete(relativePath: String)
}

class DocumentTreeFileApplier(
    private val context: Context,
    treeUri: Uri,
) : SyncFileApplier {
    private val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) { "Folder permission is unavailable" }

    override fun apply(
        relativePath: String,
        input: InputStream,
        expectedSha256: String,
        sourceModifiedAtMillis: Long?,
    ) {
        val parts = safeParts(relativePath)
        val parent = ensureDirectory(parts.dropLast(1))
        val name = parts.last()
        val temporaryName = ".syncdroid-${UUID.randomUUID()}.tmp"
        val temporary = requireNotNull(parent.createFile("application/octet-stream", temporaryName)) {
            "Could not create a temporary document"
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            requireNotNull(context.contentResolver.openOutputStream(temporary.uri, "w")).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
                output.flush()
            }
            val actual = digest.digest().toHex()
            require(actual.equals(expectedSha256, true)) { "Received file hash does not match its manifest" }
            parent.findFile(name)?.let { require(it.isFile && it.delete()) { "Could not replace existing document" } }
            require(temporary.renameTo(name)) { "The document provider could not move the synced file into place" }
        } finally {
            if (temporary.exists() && temporary.name == temporaryName) temporary.delete()
        }
    }

    override fun delete(relativePath: String) {
        find(safeParts(relativePath))?.let { require(it.isFile && it.delete()) { "Could not delete synced document" } }
    }

    fun open(relativePath: String): InputStream? = find(safeParts(relativePath))?.takeIf(DocumentFile::isFile)?.let {
        context.contentResolver.openInputStream(it.uri)
    }

    private fun ensureDirectory(parts: List<String>): DocumentFile {
        var current = root
        parts.forEach { name ->
            current = current.findFile(name)?.also { require(it.isDirectory) { "$name is not a folder" } }
                ?: requireNotNull(current.createDirectory(name)) { "Could not create folder $name" }
        }
        return current
    }

    private fun find(parts: List<String>): DocumentFile? {
        var current = root
        parts.forEach { name -> current = current.findFile(name) ?: return null }
        return current
    }

    private fun safeParts(path: String): List<String> = normalizedRelativePath(path).split('/')
}
