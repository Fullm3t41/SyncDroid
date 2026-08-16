package com.syncdroid.shared.sync

import com.syncdroid.shared.protocol.CausalRelation
import com.syncdroid.shared.protocol.VersionVector

enum class FileSyncAction { Nothing, DownloadRemote, SendLocal, Conflict }

data class FileSyncState(
    val deleted: Boolean,
    val contentSha256: String,
    val previousContentSha256: String?,
    val version: VersionVector,
)

data class FileSyncDecision(val action: FileSyncAction, val reason: String)

fun decideFileSync(local: FileSyncState?, remote: FileSyncState): FileSyncDecision {
    if (local == null) return if (remote.deleted) {
        FileSyncDecision(FileSyncAction.Nothing, "Both sides have no live file")
    } else {
        FileSyncDecision(FileSyncAction.DownloadRemote, "File is new on the remote device")
    }
    if (local.deleted == remote.deleted && local.contentSha256.equals(remote.contentSha256, true)) {
        return FileSyncDecision(FileSyncAction.Nothing, "File content is already identical")
    }
    return when (local.version.relationTo(remote.version)) {
        CausalRelation.Before -> FileSyncDecision(FileSyncAction.DownloadRemote, "Remote file causally follows local")
        CausalRelation.After -> FileSyncDecision(FileSyncAction.SendLocal, "Local file causally follows remote")
        CausalRelation.Equal -> FileSyncDecision(FileSyncAction.Conflict, "Equal vectors describe different content")
        CausalRelation.Concurrent -> when {
            remote.previousContentSha256 != null &&
                remote.previousContentSha256.equals(local.contentSha256, true) ->
                FileSyncDecision(FileSyncAction.DownloadRemote, "Remote content proves it descends from local")
            local.previousContentSha256 != null &&
                local.previousContentSha256.equals(remote.contentSha256, true) ->
                FileSyncDecision(FileSyncAction.SendLocal, "Local content proves it descends from remote")
            else -> FileSyncDecision(FileSyncAction.Conflict, "Both devices changed this file independently")
        }
    }
}
