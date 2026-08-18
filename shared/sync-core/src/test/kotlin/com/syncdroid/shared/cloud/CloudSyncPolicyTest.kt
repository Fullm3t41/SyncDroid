package com.syncdroid.shared.cloud

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudSyncPolicyTest {
    @Test
    fun selectedFoldersOnlyEnablesExplicitFolders() {
        val policy = CloudSyncPolicy(CloudSyncScope.SELECTED_FOLDERS, setOf("games"))

        assertTrue(policy.isEnabledFor("games"))
        assertFalse(policy.isEnabledFor("photos"))
    }

    @Test
    fun allFoldersIncludesFutureFolders() {
        assertTrue(CloudSyncPolicy(CloudSyncScope.ALL_FOLDERS).isEnabledFor("future-folder"))
    }

    @Test
    fun changingOneFolderPreservesTheOthers() {
        val policy = CloudSyncPolicy(CloudSyncScope.SELECTED_FOLDERS, setOf("one"))
            .withFolderEnabled("two", true)
            .withFolderEnabled("one", false)

        assertFalse(policy.isEnabledFor("one"))
        assertTrue(policy.isEnabledFor("two"))
    }
}
