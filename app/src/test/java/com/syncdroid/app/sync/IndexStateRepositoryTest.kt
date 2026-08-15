package com.syncdroid.app.sync

import com.syncdroid.app.data.FolderIndexStateEntity
import com.syncdroid.app.data.SyncDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class IndexStateRepositoryTest {
    @Test fun sequenceGapRequestsFullIndexAndAppliedAckCannotRunAhead() = runBlocking {
        var state: FolderIndexStateEntity? = null
        val dao = Proxy.newProxyInstance(
            SyncDao::class.java.classLoader,
            arrayOf(SyncDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "folderIndexState" -> state
                "upsertFolderIndexState" -> { state = args!![0] as FolderIndexStateEntity; Unit }
                else -> defaultValue(method.returnType)
            }
        } as SyncDao
        val repository = IndexStateRepository(dao)

        val first = repository.receiveMetadata("folder", "tablet", 9, 0, 4, fullIndex = true)
        assertTrue(first is IndexAcceptance.Accepted)
        assertEquals(IndexAcceptance.RequiresFullIndex, repository.receiveMetadata("folder", "tablet", 9, 2, 6, false))
        repository.acknowledgeApplied("folder", "tablet", 9, 3)
        assertEquals(3L, state?.contentAppliedSequence)
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        else -> null
    }
}
