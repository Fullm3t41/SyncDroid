package com.syncdroid.app.sync

enum class FolderDeletionPolicy {
    PROPAGATE,
    OVERWRITE_ONLY,
}

data class DeletionPlan(
    val propagatedDeletions: Set<String>,
    val newExceptions: Set<String>,
)

fun planMissingFiles(
    policy: FolderDeletionPolicy,
    previousPaths: Set<String>,
    currentPaths: Set<String>,
    activeExceptions: Set<String>,
): DeletionPlan {
    val missing = previousPaths - currentPaths - activeExceptions
    return when (policy) {
        FolderDeletionPolicy.PROPAGATE -> DeletionPlan(missing, emptySet())
        FolderDeletionPolicy.OVERWRITE_ONLY -> DeletionPlan(emptySet(), missing)
    }
}

fun shouldApplyRemoteDeletion(
    policy: FolderDeletionPolicy,
    relativePath: String,
    activeExceptions: Set<String>,
): Boolean = policy == FolderDeletionPolicy.PROPAGATE && relativePath !in activeExceptions

fun shouldAcceptRemoteFile(relativePath: String, activeExceptions: Set<String>): Boolean =
    relativePath !in activeExceptions
