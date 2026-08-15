package com.syncdroid.app.sync

enum class EndpointType { LocalPeer, GoogleDrive, OneDrive }
enum class SyncDirection { TwoWay, UploadOnly, DownloadOnly }
enum class SnapshotState { Complete, Applying, Conflicted, Superseded }
enum class ConflictState { Unresolved, KeepLeft, KeepRight, KeepBoth, Resolved }

data class FileManifestEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val sha256: String,
    val deleted: Boolean = false,
    val fileId: String = "",
    val previousSha256: String? = null,
    val version: VersionVector = VersionVector(),
    val localSequence: Long = 0,
    val originDeviceId: String = "",
)

fun manifestsHaveSameContent(left: List<FileManifestEntry>, right: List<FileManifestEntry>): Boolean {
    if (left.size != right.size) return false
    return left.sortedBy(FileManifestEntry::relativePath)
        .zip(right.sortedBy(FileManifestEntry::relativePath))
        .all { (old, current) ->
            old.relativePath == current.relativePath &&
                old.sizeBytes == current.sizeBytes &&
                old.sha256.equals(current.sha256, ignoreCase = true) &&
                old.deleted == current.deleted
        }
}

data class SnapshotManifest(
    val snapshotId: String,
    val folderId: String,
    val originDeviceId: String,
    val createdAtMillis: Long,
    val version: VersionVector,
    val parentSnapshotIds: List<String>,
    val files: List<FileManifestEntry>,
)

data class SyncDecision(
    val action: Action,
    val reason: String,
) {
    enum class Action { Nothing, AcceptRemote, SendLocal, Conflict }
}

fun decideSync(local: SnapshotManifest, remote: SnapshotManifest): SyncDecision =
    when (local.version.relationTo(remote.version)) {
        CausalRelation.Before -> SyncDecision(SyncDecision.Action.AcceptRemote, "Remote snapshot causally follows local")
        CausalRelation.After -> SyncDecision(SyncDecision.Action.SendLocal, "Local snapshot causally follows remote")
        CausalRelation.Equal -> {
            if (manifestsHaveSameContent(local.files, remote.files)) {
                SyncDecision(SyncDecision.Action.Nothing, "Snapshots are identical")
            } else {
                SyncDecision(SyncDecision.Action.Conflict, "Equal clocks contain different file manifests")
            }
        }
        CausalRelation.Concurrent -> SyncDecision(SyncDecision.Action.Conflict, "Both endpoints changed independently")
    }
