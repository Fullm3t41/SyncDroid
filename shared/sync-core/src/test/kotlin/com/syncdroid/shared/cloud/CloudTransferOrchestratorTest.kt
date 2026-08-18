package com.syncdroid.shared.cloud

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudTransferOrchestratorTest {
    @Test
    fun selectedFoldersRunForEveryConnectedProvider() = runBlocking {
        val calls = mutableListOf<Pair<CloudProvider, String>>()
        val orchestrator = CloudTransferOrchestrator(
            policy = { CloudSyncPolicy(CloudSyncScope.SELECTED_FOLDERS, setOf("games")) },
            folderIds = { listOf("games", "photos") },
            connectedProviders = { CloudProvider.entries },
            runner = CloudTransferRunner { provider, folder ->
                calls += provider to folder
                CloudTransferResult(uploadedFiles = 1)
            },
        )

        val result = orchestrator.run(CloudSyncTrigger.SCHEDULED_WINDOW)

        assertEquals(
            listOf(CloudProvider.GOOGLE_DRIVE to "games", CloudProvider.ONE_DRIVE to "games"),
            calls,
        )
        assertEquals(2, result.uploadedFiles)
    }

    @Test
    fun disabledPolicyDoesNoWork() = runBlocking {
        var calls = 0
        val orchestrator = CloudTransferOrchestrator(
            policy = { CloudSyncPolicy() },
            folderIds = { listOf("games") },
            connectedProviders = { CloudProvider.entries },
            runner = CloudTransferRunner { _, _ -> calls++; CloudTransferResult() },
        )
        orchestrator.run(CloudSyncTrigger.MANUAL)
        assertEquals(0, calls)
    }
}
