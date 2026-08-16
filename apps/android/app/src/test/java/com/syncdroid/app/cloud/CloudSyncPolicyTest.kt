package com.syncdroid.app.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncPolicyTest {
    @Test fun selectedFoldersOnlyEnablesExplicitFolders() {
        val policy = CloudSyncPolicy(CloudSyncScope.SELECTED_FOLDERS, setOf("one"))
        assertTrue(policy.isEnabledFor("one"))
        assertFalse(policy.isEnabledFor("two"))
    }

    @Test fun allFoldersOverridesIndividualSelection() {
        val policy = CloudSyncPolicy(CloudSyncScope.ALL_FOLDERS)
        assertTrue(policy.isEnabledFor("any-folder"))
    }
}
