package com.syncdroid.shared.sync

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Prevents simultaneous peer sessions in one process from writing the same
 * resumable file or partial-transfer record. Unclaimed files remain pending
 * and are reconsidered by a later mesh session.
 */
class ActiveTransferClaims private constructor(
    private val owner: Any,
    private val claimedKeys: Set<String>,
) : Closeable {
    fun owns(key: String): Boolean = key in claimedKeys

    override fun close() {
        claimedKeys.forEach { key -> owners.remove(key, owner) }
    }

    companion object {
        private val owners = ConcurrentHashMap<String, Any>()

        fun claim(keys: Collection<String>): ActiveTransferClaims {
            val owner = Any()
            val claimed = keys.asSequence()
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .filter { key -> owners.putIfAbsent(key, owner) == null }
                .toSet()
            return ActiveTransferClaims(owner, claimed)
        }
    }
}

fun activeTransferKey(folderId: String, fileId: String, contentSha256: String): String =
    "$folderId\u0000$fileId\u0000${contentSha256.lowercase()}"
