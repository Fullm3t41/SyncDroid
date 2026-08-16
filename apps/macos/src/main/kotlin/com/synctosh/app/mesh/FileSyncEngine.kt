package com.synctosh.app.mesh

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.io.path.invariantSeparatorsPathString

enum class FileSyncAction { Nothing, DownloadRemote, SendLocal, Conflict }

data class FileSyncPlan(
    val action: FileSyncAction,
    val relativePath: String,
    val local: FileVersion?,
    val remote: RemoteFileVersion,
    val reason: String,
    val remoteManifest: BlockManifest?,
)

fun decideFileSync(local: FileVersion?, remote: RemoteFileVersion): Pair<FileSyncAction, String> {
    if (local == null) return if (remote.deleted) {
        FileSyncAction.Nothing to "Both sides have no live file"
    } else {
        FileSyncAction.DownloadRemote to "File is new on the remote device"
    }
    if (local.deleted == remote.deleted && local.contentSha256.equals(remote.contentSha256, true)) {
        return FileSyncAction.Nothing to "File content is already identical"
    }
    return when (local.version.relationTo(remote.version)) {
        CausalRelation.Before -> FileSyncAction.DownloadRemote to "Remote file causally follows local"
        CausalRelation.After -> FileSyncAction.SendLocal to "Local file causally follows remote"
        CausalRelation.Equal -> FileSyncAction.Conflict to "Equal vectors describe different content"
        CausalRelation.Concurrent -> when {
            remote.previousContentSha256 != null &&
                remote.previousContentSha256.equals(local.contentSha256, true) ->
                FileSyncAction.DownloadRemote to "Remote content proves it descends from local"
            local.previousContentSha256 != null &&
                local.previousContentSha256.equals(remote.contentSha256, true) ->
                FileSyncAction.SendLocal to "Local content proves it descends from remote"
            else -> FileSyncAction.Conflict to "Both devices changed this file independently"
        }
    }
}

class FileSyncEngine(
    private val store: MeshStore,
    private val identity: MacDeviceIdentity,
    private val profile: MeshProfile,
) {
    private val history = FileHistoryRepository(store, identity.deviceId)

    fun scanConfiguredFolders(recordHistory: Boolean = true) {
        store.configuredFolders(profile.groupId, identity.deviceId).forEach { scanFolder(it, recordHistory) }
    }

    fun buildCatalog(remoteDeviceId: String): List<FolderClock> =
        store.folders(profile.groupId, identity.deviceId).mapNotNull { folder ->
            val local = store.folderIndexState(folder.folderId, identity.deviceId) ?: return@mapNotNull null
            val knownPeer = store.folderIndexState(folder.folderId, remoteDeviceId)
            FolderClock(
                folder.folderId,
                local.indexEpoch,
                local.maxSequence,
                knownPeer?.indexEpoch ?: 0,
                knownPeer?.metadataReceivedSequence ?: 0,
                knownPeer?.contentAppliedSequence ?: 0,
            )
        }

    fun buildUpdatesForPeer(remoteCatalog: List<FolderClock>): List<FolderIndexUpdate> {
        val peerByFolder = remoteCatalog.associateBy(FolderClock::folderId)
        return store.folders(profile.groupId, identity.deviceId).mapNotNull { folder ->
            val local = store.folderIndexState(folder.folderId, identity.deviceId) ?: return@mapNotNull null
            val root = configuredRoot(folder.folderId) ?: return@mapNotNull null
            val peer = peerByFolder[folder.folderId]
            val full = peer == null || peer.knownPeerIndexEpoch != local.indexEpoch ||
                peer.knownPeerReceivedSequence > local.maxSequence
            val previous = if (full) 0 else peer.knownPeerReceivedSequence
            if (!full && previous == local.maxSequence) return@mapNotNull null
            val versions = (if (full) store.fileVersions(folder.folderId) else {
                store.fileVersionsAfter(folder.folderId, previous)
            }).sortedBy(FileVersion::localSequence)
            require(versions.size <= MAX_INDEX_FILES) { "Folder index is too large for one session" }
            FolderIndexUpdate(
                folder.folderId,
                local.indexEpoch,
                previous,
                local.maxSequence,
                full,
                versions.map { it.toIndexedRecord(root) },
            )
        }
    }

    fun receiveIndexes(remoteDeviceId: String, updates: List<FolderIndexUpdate>): List<FileSyncPlan> {
        val plans = mutableListOf<FileSyncPlan>()
        updates.forEach { update ->
            validate(update)
            require(store.folders(profile.groupId, identity.deviceId).any { it.folderId == update.folderId }) {
                "Peer sent an index for another mesh"
            }
            require(store.acceptRemoteIndex(remoteDeviceId, update)) { "A full index is required" }
            val localByPath = store.fileVersions(update.folderId).associateBy(FileVersion::relativePath)
            update.files.map { it.toRemote(update.folderId, remoteDeviceId) }.forEach { remote ->
                val local = localByPath[remote.relativePath]
                val (action, reason) = decideFileSync(local, remote)
                if (action == FileSyncAction.Conflict) store.recordConflict(local, remote)
                plans += FileSyncPlan(action, remote.relativePath, local, remote, reason, store.remoteBlockManifest(remote))
            }
        }
        val receivedKeys = plans.mapTo(mutableSetOf()) { it.key() }
        store.folders(profile.groupId, identity.deviceId).forEach { folder ->
            store.pendingRemoteVersions(folder.folderId, remoteDeviceId).forEach { remote ->
                val key = "${remote.folderId}\u0000${remote.relativePath}\u0000${remote.remoteSequence}"
                if (key in receivedKeys) return@forEach
                val local = store.fileVersion(folder.folderId, remote.relativePath)
                val (action, reason) = decideFileSync(local, remote)
                if (action == FileSyncAction.Conflict) store.recordConflict(local, remote)
                plans += FileSyncPlan(action, remote.relativePath, local, remote, reason, store.remoteBlockManifest(remote))
            }
        }
        return plans.sortedWith(compareBy({ it.remote.folderId }, { it.remote.remoteSequence }))
    }

    fun configuredRoot(folderId: String): Path? = store.configuredFolders(profile.groupId, identity.deviceId)
        .firstOrNull { it.folderId == folderId }
        ?.localPath
        ?.let(Path::of)
        ?.takeIf(Files::isDirectory)

    fun markRemoteApplied(remoteDeviceId: String, remote: RemoteFileVersion, acknowledge: Boolean) {
        store.markRemoteApplied(remote, remoteDeviceId, identity.deviceId, acknowledge)
    }

    private fun scanFolder(folder: MeshFolder, recordHistory: Boolean) {
        val root = Path.of(requireNotNull(folder.localPath)).toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Configured folder is unavailable: ${folder.displayName}" }
        val scanned = scanFiles(root, folder.includePatterns, folder.excludePatterns)
        val previous = store.fileVersions(folder.folderId).associateBy(FileVersion::relativePath)
        val priorState = store.folderIndexState(folder.folderId, identity.deviceId)
        val state = priorState ?: FolderIndexState(
            folder.folderId,
            identity.deviceId,
            randomEpoch(),
            0,
            0,
            0,
            System.currentTimeMillis(),
        )
        var nextSequence = state.maxSequence
        var changed = false
        val updated = linkedMapOf<String, FileVersion>()
        val historyChanges = mutableListOf<Pair<FileHistoryAction, FileVersion>>()
        scanned.forEach { file ->
            val old = previous[file.relativePath]
            val unchanged = old != null && !old.deleted && old.sizeBytes == file.sizeBytes &&
                old.contentSha256.equals(file.sha256, true)
            updated[file.relativePath] = if (unchanged) old else {
                changed = true
                nextSequence++
                FileVersion(
                    folder.folderId,
                    file.relativePath,
                    old?.fileId ?: UUID.randomUUID().toString(),
                    file.sizeBytes,
                    file.modifiedAtMillis,
                    file.sha256,
                    old?.contentSha256?.takeIf(String::isNotBlank),
                    false,
                    (old?.version ?: VersionVector()).increment(identity.deviceId),
                    identity.deviceId,
                    nextSequence,
                ).also { current ->
                    historyChanges += (if (old == null || old.deleted) FileHistoryAction.ADDED else FileHistoryAction.UPDATED) to current
                }
            }
        }
        val scannedPaths = scanned.mapTo(mutableSetOf(), ScannedFile::relativePath)
        previous.forEach { (path, old) ->
            if (path in scannedPaths) return@forEach
            updated[path] = if (old.deleted) old else {
                changed = true
                nextSequence++
                old.copy(
                    sizeBytes = 0,
                    modifiedAtMillis = System.currentTimeMillis(),
                    contentSha256 = "",
                    previousContentSha256 = old.contentSha256.takeIf(String::isNotBlank),
                    deleted = true,
                    version = old.version.increment(identity.deviceId),
                    originDeviceId = identity.deviceId,
                    localSequence = nextSequence,
                ).also { historyChanges += FileHistoryAction.DELETED to old }
            }
        }
        if (changed || priorState == null) {
            val now = System.currentTimeMillis()
            store.saveLocalIndex(
                updated.values.toList(),
                state.copy(
                    maxSequence = nextSequence,
                    metadataReceivedSequence = nextSequence,
                    contentAppliedSequence = nextSequence,
                    updatedAtMillis = now,
                ),
            )
            if (recordHistory) historyChanges.forEach { (action, version) ->
                if (action == FileHistoryAction.DELETED) history.recordDetectedDeletion(version, now)
                else history.recordChange(action, version, identity.deviceId, now)
            }
        }
    }

    private fun validate(update: FolderIndexUpdate) {
        require(update.indexEpoch != 0L && update.previousSequence >= 0 && update.lastSequence >= update.previousSequence)
        var sequence = update.previousSequence
        val paths = mutableSetOf<String>()
        update.files.forEach { file ->
            require(file.sequence > sequence && file.sequence <= update.lastSequence) { "Index sequences are not increasing" }
            sequence = file.sequence
            require(paths.add(normalizedRelativePath(file.relativePath))) { "Index contains a duplicate path" }
            require(file.fileId.isNotBlank() && file.originDeviceId.isNotBlank())
            require(file.sizeBytes >= 0 && file.modifiedAtMillis >= 0)
            require(file.deleted || file.contentSha256.matches(HASH_PATTERN)) { "Invalid content hash" }
            require(file.previousContentSha256 == null || file.previousContentSha256.matches(HASH_PATTERN))
            var expectedOffset = 0L
            file.blocks.forEachIndexed { index, block ->
                require(block.index == index && block.offsetBytes == expectedOffset && block.sizeBytes >= 0)
                require(block.sha256.matches(HASH_PATTERN)); expectedOffset += block.sizeBytes
            }
            if (file.blocks.isNotEmpty()) require(file.blockSizeBytes > 0 && (file.deleted || expectedOffset == file.sizeBytes))
        }
        if (update.files.isNotEmpty()) require(sequence == update.lastSequence) { "Index last sequence does not match its records" }
    }

    private fun FileVersion.toIndexedRecord(root: Path): IndexedFileRecord {
        val manifest = if (!deleted && sizeBytes >= BlockManifestBuilder.RESUMABLE_THRESHOLD_BYTES) {
            store.localBlockManifest(this) ?: BlockManifestBuilder.build(this, root.resolve(relativePath)).also {
                store.storeLocalBlockManifest(it)
            }
        } else null
        return IndexedFileRecord(
            relativePath,
            fileId,
            sizeBytes,
            modifiedAtMillis,
            contentSha256,
            previousContentSha256,
            originDeviceId,
            deleted,
            version,
            localSequence,
            blockSizeBytes = manifest?.blockSizeBytes ?: 0,
            blocks = manifest?.blocks ?: emptyList(),
        )
    }

    private companion object {
        const val MAX_INDEX_FILES = 50_000
        val HASH_PATTERN = Regex("[a-fA-F0-9]{64}")
    }
}

private data class ScannedFile(val relativePath: String, val sizeBytes: Long, val modifiedAtMillis: Long, val sha256: String)

private fun scanFiles(root: Path, includes: List<String>, excludes: List<String>): List<ScannedFile> {
    val rootReal = root.toRealPath()
    return Files.walk(rootReal).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
        }.map { path ->
            val real = path.toRealPath(LinkOption.NOFOLLOW_LINKS)
            require(real.startsWith(rootReal)) { "Folder contains a file outside its root" }
            rootReal.relativize(real).invariantSeparatorsPathString to real
        }.filter { (relativePath) -> shouldSync(relativePath, includes, excludes) }
            .map { (relativePath, path) -> stableFile(relativePath, path) }
            .sorted(compareBy(ScannedFile::relativePath))
            .toList()
    }
}

private fun stableFile(relativePath: String, path: Path): ScannedFile {
    repeat(2) {
        val size = Files.size(path)
        val modified = Files.getLastModifiedTime(path).toMillis()
        val hash = Files.newInputStream(path).buffered().use(::sha256Hex)
        if (size == Files.size(path) && modified == Files.getLastModifiedTime(path).toMillis()) {
            return ScannedFile(relativePath, size, modified, hash)
        }
    }
    error("File changed repeatedly while it was being scanned: $relativePath")
}

private fun shouldSync(relativePath: String, includes: List<String>, excludes: List<String>): Boolean {
    if (excludes.any { globMatches(it, relativePath) }) return false
    return includes.isEmpty() || includes.any { globMatches(it, relativePath) }
}

private fun globMatches(rawPattern: String, path: String): Boolean {
    val pattern = rawPattern.trim().replace('\\', '/')
    if (pattern.isEmpty()) return false
    val target = if ('/' in pattern) path else path.substringAfterLast('/')
    val regex = buildString {
        append('^')
        var index = 0
        while (index < pattern.length) {
            when (val char = pattern[index]) {
                '*' -> if (index + 1 < pattern.length && pattern[index + 1] == '*') {
                    append(".*"); index++
                } else append("[^/]*")
                '?' -> append("[^/]")
                '.', '(', ')', '[', ']', '$', '^', '{', '}', '+', '|', '\\' -> append("\\$char")
                else -> append(char)
            }
            index++
        }
        append('$')
    }
    return Regex(regex, RegexOption.IGNORE_CASE).matches(target)
}

fun normalizedRelativePath(path: String): String {
    val normalized = path.replace('\\', '/').trim('/')
    require(normalized.isNotBlank() && normalized.length <= 4_096) { "Invalid relative path" }
    require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Invalid relative path" }
    return normalized
}

fun sha256Hex(input: java.io.InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun IndexedFileRecord.toRemote(folderId: String, deviceId: String) = RemoteFileVersion(
    folderId,
    deviceId,
    normalizedRelativePath(relativePath),
    fileId,
    sizeBytes,
    modifiedAtMillis,
    contentSha256.lowercase(),
    previousContentSha256?.lowercase(),
    originDeviceId,
    deleted,
    version,
    sequence,
)

private fun FileSyncPlan.key() = "${remote.folderId}\u0000${remote.relativePath}\u0000${remote.remoteSequence}"

private fun randomEpoch(): Long = (SecureRandom().nextLong() and Long.MAX_VALUE).coerceAtLeast(1)
